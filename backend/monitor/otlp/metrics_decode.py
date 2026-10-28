"""OTLP/HTTP 指标请求体解码（protobuf / JSON 双支持）。

与 trace 的 `decode.py` 同构：两条路径都归一成 OTLP/JSON 形状的
`resourceMetrics` 列表（lowerCamelCase），mapper 只处理一种结构。
"""
from __future__ import annotations

import json
from typing import Any

from .decode import OtlpDecodeError


def _decode_protobuf(body: bytes) -> list[dict[str, Any]]:
    """protobuf → OTLP/JSON 形状的 resourceMetrics 列表。"""
    try:
        from google.protobuf.json_format import MessageToDict
        from opentelemetry.proto.collector.metrics.v1.metrics_service_pb2 import (
            ExportMetricsServiceRequest,
        )
    except ImportError as exc:  # pragma: no cover - 依赖缺失时的兜底
        raise OtlpDecodeError(f"未安装 OTLP metrics protobuf 依赖: {exc}") from exc

    msg = ExportMetricsServiceRequest()
    try:
        msg.ParseFromString(body)
    except Exception as exc:
        raise OtlpDecodeError(f"metrics protobuf 解析失败: {exc}") from exc

    data = MessageToDict(msg, preserving_proto_field_name=False)
    return data.get("resourceMetrics") or []


def _decode_json(body: bytes) -> list[dict[str, Any]]:
    try:
        data = json.loads(body)
    except (json.JSONDecodeError, UnicodeDecodeError) as exc:
        raise OtlpDecodeError(f"metrics JSON 解析失败: {exc}") from exc
    if not isinstance(data, dict):
        raise OtlpDecodeError("OTLP metrics JSON 根节点必须是对象")
    metrics = data.get("resourceMetrics")
    if metrics is None:
        return []
    if not isinstance(metrics, list):
        raise OtlpDecodeError("resourceMetrics 必须是数组")
    return metrics


def decode_metrics_body(body: bytes, content_type: str) -> list[dict[str, Any]]:
    """按 content-type 解码请求体，返回 resourceMetrics 列表。

    Raises:
        OtlpDecodeError: 解析失败或 content-type 不支持。
    """
    ct = (content_type or "").lower()
    if "application/x-protobuf" in ct:
        return _decode_protobuf(body)
    if "application/json" in ct:
        return _decode_json(body)
    raise OtlpDecodeError(f"不支持的 Content-Type: {content_type!r}")


def build_metrics_protobuf_response(
    rejected: int, error_message: str | None
) -> bytes | None:
    """构造 ExportMetricsServiceResponse（protobuf）。"""
    try:
        from opentelemetry.proto.collector.metrics.v1.metrics_service_pb2 import (
            ExportMetricsServiceResponse,
        )
    except ImportError:  # pragma: no cover
        return None

    resp = ExportMetricsServiceResponse()
    if rejected:
        resp.partial_success.rejected_data_points = rejected
        if error_message:
            resp.partial_success.error_message = error_message
    return resp.SerializeToString()


def build_metrics_json_response(
    rejected: int, error_message: str | None
) -> dict[str, Any]:
    """构造 OTLP/JSON 形状的响应体（无失败时为空对象）。"""
    if not rejected:
        return {}
    partial: dict[str, Any] = {"rejectedDataPoints": rejected}
    if error_message:
        partial["errorMessage"] = error_message
    return {"partialSuccess": partial}
