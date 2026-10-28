"""OTLP/HTTP 接收端点（默认 `POST /v1/traces`、`POST /v1/metrics`）。

刻意不挂在 `/api/v1` 前缀下：OTLP 客户端的 endpoint 配置习惯是
`http://host:4318/v1/traces`，保持标准路径便于直接对接。

处理顺序见 docs/OTLP端点接收实现方案.md §5.1：
读 body → gunzip → content-type 分流解码 → mapper 转换 → sink 投递 → 立即响应。
"""
from __future__ import annotations

import gzip
import logging
from typing import Any

from fastapi import APIRouter, Request, Response
from fastapi.responses import JSONResponse

from ...config import (
    METRIC_PATH,
    OTLP_AUTH_TOKEN,
    OTLP_MAX_BODY_BYTES,
    OTLP_PATH,
)
from ...otlp import (
    OtlpDecodeError,
    build_json_response,
    build_metrics_json_response,
    build_metrics_protobuf_response,
    build_protobuf_response,
    decode_body,
    decode_metrics_body,
    map_resource_metrics,
    map_resource_spans,
)

logger = logging.getLogger("trace_server.otlp")

router = APIRouter()


def _unauthorized() -> Response:
    return JSONResponse(
        status_code=401,
        content={"error": "Unauthorized"},
    )


def _check_auth(request: Request) -> bool:
    """OTLP_AUTH_TOKEN 非空时校验 Authorization: Bearer <token>。"""
    if not OTLP_AUTH_TOKEN:
        return True
    header = request.headers.get("authorization") or ""
    token = header[7:].strip() if header.lower().startswith("bearer ") else ""
    return token == OTLP_AUTH_TOKEN


async def _read_body(request: Request) -> bytes:
    """读取原始 body（受 OTLP_MAX_BODY_BYTES 限制）。"""
    chunks: list[bytes] = []
    total = 0
    async for chunk in request.stream():
        total += len(chunk)
        if total > OTLP_MAX_BODY_BYTES:
            raise ValueError("body too large")
        chunks.append(chunk)
    return b"".join(chunks)


def _respond(request: Request, rejected: int, message: str | None) -> Response:
    """按请求的 content-type 返回对应格式的 ExportTraceServiceResponse。"""
    content_type = (request.headers.get("content-type") or "").lower()
    if "application/x-protobuf" in content_type:
        body = build_protobuf_response(rejected, message)
        if body is not None:
            return Response(content=body, media_type="application/x-protobuf")
        return Response(content=b"", media_type="application/x-protobuf")
    return JSONResponse(content=build_json_response(rejected, message))


async def _handle(request: Request) -> Response:
    if not _check_auth(request):
        return _unauthorized()

    try:
        body = await _read_body(request)
    except ValueError:
        logger.warning("OTLP 请求体超过上限 %d 字节", OTLP_MAX_BODY_BYTES)
        return JSONResponse(status_code=413, content={"error": "body too large"})
    except Exception:
        logger.exception("OTLP 读取请求体失败")
        return JSONResponse(status_code=400, content={"error": "read body failed"})

    if (request.headers.get("content-encoding") or "").lower().find("gzip") >= 0:
        try:
            body = gzip.decompress(body)
        except OSError:
            logger.warning("OTLP gzip 解压失败")
            return JSONResponse(
                status_code=400, content={"error": "decompress failed"}
            )

    if not body:
        return _respond(request, 0, None)

    try:
        resource_spans = decode_body(body, request.headers.get("content-type") or "")
    except OtlpDecodeError as exc:
        logger.warning("OTLP 请求解析失败: %s", exc)
        return JSONResponse(status_code=400, content={"error": str(exc)})

    if not resource_spans:
        return _respond(request, 0, None)

    events, rejected = map_resource_spans(resource_spans)

    sink = getattr(request.app.state, "otlp_sink", None)
    if sink is None:
        logger.error("OTLP sink 未初始化，丢弃 %d 条事件", len(events))
        return JSONResponse(
            status_code=503, content={"error": "otlp sink not ready"}
        )

    if not sink.offer(events):
        # 队列满 → 让客户端按自己的退避策略重试（绝不静默丢）
        return JSONResponse(
            status_code=503, content={"error": "otlp queue full, retry later"}
        )

    message = f"rejected {rejected} span(s)" if rejected else None
    if rejected:
        logger.warning("OTLP 部分 span 解析失败: %d 条", rejected)
    return _respond(request, rejected, message)


@router.post(OTLP_PATH, include_in_schema=False)
async def ingest_traces(request: Request) -> Response:
    return await _handle(request)


def _respond_metrics(request: Request, rejected: int, message: str | None) -> Response:
    """按请求的 content-type 返回 ExportMetricsServiceResponse。"""
    content_type = (request.headers.get("content-type") or "").lower()
    if "application/x-protobuf" in content_type:
        body = build_metrics_protobuf_response(rejected, message)
        if body is not None:
            return Response(content=body, media_type="application/x-protobuf")
        return Response(content=b"", media_type="application/x-protobuf")
    return JSONResponse(content=build_metrics_json_response(rejected, message))


async def _handle_metrics(request: Request) -> Response:
    """OTel 指标接收：解码 → 归一化 → MetricsSink 入队 → 立即响应。

    与 traces 共用鉴权、gzip、body 上限逻辑，但 data point 落到 metric_point。
    """
    if not _check_auth(request):
        return _unauthorized()

    try:
        body = await _read_body(request)
    except ValueError:
        logger.warning("OTLP metrics 请求体超过上限 %d 字节", OTLP_MAX_BODY_BYTES)
        return JSONResponse(status_code=413, content={"error": "body too large"})
    except Exception:
        logger.exception("OTLP metrics 读取请求体失败")
        return JSONResponse(status_code=400, content={"error": "read body failed"})

    if (request.headers.get("content-encoding") or "").lower().find("gzip") >= 0:
        try:
            body = gzip.decompress(body)
        except OSError:
            logger.warning("OTLP metrics gzip 解压失败")
            return JSONResponse(status_code=400, content={"error": "decompress failed"})

    if not body:
        return _respond_metrics(request, 0, None)

    try:
        resource_metrics = decode_metrics_body(
            body, request.headers.get("content-type") or ""
        )
    except OtlpDecodeError as exc:
        logger.warning("OTLP metrics 请求解析失败: %s", exc)
        return JSONResponse(status_code=400, content={"error": str(exc)})

    if not resource_metrics:
        return _respond_metrics(request, 0, None)

    points, rejected = map_resource_metrics(resource_metrics)

    sink = getattr(request.app.state, "metrics_sink", None)
    if sink is None:
        logger.error("OTel MetricsSink 未初始化，丢弃 %d 个 data point", len(points))
        return JSONResponse(status_code=503, content={"error": "metrics sink not ready"})

    if not sink.offer(points):
        return JSONResponse(
            status_code=503, content={"error": "metrics queue full, retry later"}
        )

    message = f"rejected {rejected} data point(s)" if rejected else None
    if rejected:
        logger.warning("OTLP metrics 部分 data point 解析失败: %d 个", rejected)
    return _respond_metrics(request, rejected, message)


@router.post(METRIC_PATH, include_in_schema=False)
async def ingest_metrics(request: Request) -> Response:
    return await _handle_metrics(request)


@router.post("/v1/logs", include_in_schema=False)
async def ingest_logs() -> Any:
    return {}
