"""OTLP 端点接收：把标准 OTLP/HTTP 上报转换成 trace2 内部事件。

模块划分：
- `decode`         —— trace 请求体解码（protobuf / JSON）与 ID 归一
- `semconv`        —— OTel 语义约定常量 + span_type 推断
- `mapper`         —— resourceSpans → 内部事件 dict
- `metrics_decode` —— metrics 请求体解码
- `metrics_mapper` —— resourceMetrics → metric_point 行 dict
- `sinks`          —— 事件/指标落地通道（kafka / redis / database / file / none）

设计文档：docs/OTLP端点接收实现方案.md、docs/trace-metrics-design.md
"""
from __future__ import annotations

from .decode import (
    OtlpDecodeError,
    build_json_response,
    build_protobuf_response,
    decode_body,
    parse_otel_id,
)
from .mapper import flatten_attributes, hex_to_uuid, map_resource_spans
from .metrics_decode import (
    build_metrics_json_response,
    build_metrics_protobuf_response,
    decode_metrics_body,
)
from .metrics_mapper import map_resource_metrics
from .sinks import (
    BaseSink,
    DatabaseSink,
    FileSink,
    KafkaSink,
    MetricsSink,
    NilMetricsSink,
    NilSink,
    RedisSink,
    build_metrics_sink,
    build_sink,
)

__all__ = [
    "OtlpDecodeError",
    "build_json_response",
    "build_protobuf_response",
    "decode_body",
    "parse_otel_id",
    "flatten_attributes",
    "hex_to_uuid",
    "map_resource_spans",
    "build_metrics_json_response",
    "build_metrics_protobuf_response",
    "decode_metrics_body",
    "map_resource_metrics",
    "BaseSink",
    "DatabaseSink",
    "FileSink",
    "KafkaSink",
    "MetricsSink",
    "NilMetricsSink",
    "RedisSink",
    "NilSink",
    "build_metrics_sink",
    "build_sink",
]
