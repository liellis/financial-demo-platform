"""OTLP/HTTP 请求体解码（protobuf / JSON 双支持）。

两条路径最终都归一成 **OTLP/JSON 形状的 dict**（lowerCamelCase），
这样 mapper 只需处理一种结构。

关键差异：protobuf 解码后 `traceId` / `spanId` 是 bytes，经 MessageToDict
转成 base64 字符串；而 OTLP/JSON 规范里是 hex 字符串。`parse_otel_id`
对两种形态都做兼容，否则 ID 解析必然错位。
"""
from __future__ import annotations

import base64
import json
import re
from typing import Any

_HEX32 = re.compile(r"^[0-9a-fA-F]{32}$")
_HEX16 = re.compile(r"^[0-9a-fA-F]{16}$")


class OtlpDecodeError(ValueError):
    """请求体无法按 OTLP 解析（路由层据此返回 400）。"""


def parse_otel_id(raw: Any) -> str:
    """把 OTLP 的 ID 字段归一成小写 hex 字符串（trace 32 位 / span 16 位）。

    支持三种形态：
    - bytes / bytearray：protobuf 原始字节
    - hex 字符串：OTLP/JSON 规范形态
    - base64 字符串：MessageToDict 对 bytes 的默认输出
    """
    if raw is None:
        return ""
    if isinstance(raw, (bytes, bytearray)):
        return bytes(raw).hex()
    if isinstance(raw, str):
        if _HEX32.match(raw) or _HEX16.match(raw):
            return raw.lower()
        try:
            decoded = base64.b64decode(raw, validate=True)
        except Exception:
            return raw.strip().lower()
        return decoded.hex()
    return str(raw).strip().lower()


def _decode_protobuf(body: bytes) -> list[dict[str, Any]]:
    """protobuf → OTLP/JSON 形状的 resourceSpans 列表。

    依赖懒加载：未安装 protobuf 时不影响服务器其它功能启动，
    只在使用该 content-type 时报错。
    """
    try:
        from google.protobuf.json_format import MessageToDict
        from opentelemetry.proto.collector.trace.v1.trace_service_pb2 import (
            ExportTraceServiceRequest,
        )
    except ImportError as exc:  # pragma: no cover - 依赖缺失时的兜底
        raise OtlpDecodeError(f"未安装 OTLP protobuf 依赖: {exc}") from exc

    msg = ExportTraceServiceRequest()
    try:
        msg.ParseFromString(body)
    except Exception as exc:
        raise OtlpDecodeError(f"protobuf 解析失败: {exc}") from exc

    # preserving_proto_field_name=False → lowerCamelCase，与 OTLP/JSON 一致
    data = MessageToDict(msg, preserving_proto_field_name=False)
    return data.get("resourceSpans") or []


def _decode_json(body: bytes) -> list[dict[str, Any]]:
    try:
        data = json.loads(body)
    except (json.JSONDecodeError, UnicodeDecodeError) as exc:
        raise OtlpDecodeError(f"JSON 解析失败: {exc}") from exc
    if not isinstance(data, dict):
        raise OtlpDecodeError("OTLP JSON 根节点必须是对象")
    spans = data.get("resourceSpans")
    if spans is None:
        return []
    if not isinstance(spans, list):
        raise OtlpDecodeError("resourceSpans 必须是数组")
    return spans


def decode_body(body: bytes, content_type: str) -> list[dict[str, Any]]:
    """按 content-type 解码请求体，返回 resourceSpans 列表。

    Raises:
        OtlpDecodeError: 解析失败或 content-type 不支持。
    """
    ct = (content_type or "").lower()
    if "application/x-protobuf" in ct:
        return _decode_protobuf(body)
    if "application/json" in ct:
        return _decode_json(body)
    raise OtlpDecodeError(f"不支持的 Content-Type: {content_type!r}")


def build_protobuf_response(rejected: int, error_message: str | None) -> bytes | None:
    """构造 ExportTraceServiceResponse（protobuf）。

    依赖缺失或构造失败时返回 None，调用方回退成空 body（OTel 客户端接受）。
    """
    try:
        from opentelemetry.proto.collector.trace.v1.trace_service_pb2 import (
            ExportTraceServiceResponse,
        )
    except ImportError:  # pragma: no cover
        return None

    resp = ExportTraceServiceResponse()
    if rejected:
        resp.partial_success.rejected_spans = rejected
        if error_message:
            resp.partial_success.error_message = error_message
    return resp.SerializeToString()


def build_json_response(rejected: int, error_message: str | None) -> dict[str, Any]:
    """构造 OTLP/JSON 形状的响应体（无失败时为空对象）。"""
    if not rejected:
        return {}
    partial: dict[str, Any] = {"rejectedSpans": rejected}
    if error_message:
        partial["errorMessage"] = error_message
    return {"partialSuccess": partial}
