"""OTLP resourceMetrics → metric_point 行 dict。

归一化规则（与 docs/trace-metrics-design.md 一致）：
- resource 属性取 project.id / service.name
- data point 属性只保留稳定维度；`gen_ai.tool.call.id`、`gen_ai.response.id`
  这类每次调用都变的属性必须剔除，否则每个 data point 都会成为新 series
- `series_hash` = 归一化属性的 md5，配合 (metric_name, ts 区间) 做幂等
"""
from __future__ import annotations

import hashlib
import json
from datetime import datetime, timezone
from typing import Any

from ..config import OTLP_DEFAULT_PROJECT_ID

from .decode import parse_otel_id  # noqa: F401  （保持与 trace 解码同一依赖面）
from .mapper import _parse_nano, flatten_attributes
from .semconv import PROJECT_ID_KEYS

# 每次调用都会变化的属性：参与 series 标识会导致无法聚合，必须剔除
VOLATILE_ATTRIBUTES = frozenset(
    {
        "gen_ai.tool.call.id",
        "gen_ai.response.id",
        "gen_ai.request.id",
        "gen_ai.conversation.id",
    }
)

# 属性名 → metric_point 维度列
_DIMENSION_COLUMNS = {
    "operation": "gen_ai.operation.name",
    "provider": "gen_ai.provider.name",
    "request_model": "gen_ai.request.model",
    "response_model": "gen_ai.response.model",
    "token_type": "gen_ai.token.type",
    "tool_name": "gen_ai.tool.name",
    "error_type": "error.type",
}

# OTLP aggregationTemporality：1=DELTA，2=CUMULATIVE
_TEMPORALITY = {1: "delta", 2: "cumulative"}

_MAX_TEXT = {
    "operation": 64,
    "provider": 64,
    "request_model": 128,
    "response_model": 128,
    "token_type": 16,
    "tool_name": 128,
    "error_type": 128,
}


def _epoch_ns_to_dt(nanos: int | None) -> datetime | None:
    """OTLP 纳秒时间戳 → 带时区 datetime（精度到微秒）。"""
    if nanos is None:
        return None
    seconds, remainder = divmod(int(nanos), 1_000_000_000)
    return datetime.fromtimestamp(seconds, tz=timezone.utc).replace(
        microsecond=remainder // 1000
    )


def _as_number(value: Any) -> float | None:
    """OTLP 数值字段：可能是数字，也可能是字符串（int64 用字符串传输）。"""
    if value is None or isinstance(value, bool):
        return None
    if isinstance(value, (int, float)):
        return float(value)
    if isinstance(value, str):
        try:
            return float(value)
        except ValueError:
            return None
    return None


def _as_int(value: Any) -> int | None:
    number = _as_number(value)
    return int(number) if number is not None else None


def _as_int_list(value: Any) -> list[int] | None:
    if not isinstance(value, list):
        return None
    out: list[int] = []
    for item in value:
        number = _as_int(item)
        out.append(number if number is not None else 0)
    return out


def _as_float_list(value: Any) -> list[float] | None:
    if not isinstance(value, list):
        return None
    out: list[float] = []
    for item in value:
        number = _as_number(item)
        if number is not None:
            out.append(number)
    return out


def _series_hash(attributes: dict[str, Any]) -> str:
    """归一化属性 → 稳定 series 标识。"""
    canonical = json.dumps(
        sorted(attributes.items()), ensure_ascii=False, default=str, separators=(",", ":")
    )
    return hashlib.md5(canonical.encode("utf-8")).hexdigest()


def _clean_attributes(raw: dict[str, Any]) -> dict[str, Any]:
    """剔除易变属性，并丢弃空值。"""
    cleaned: dict[str, Any] = {}
    for key, value in raw.items():
        if key in VOLATILE_ATTRIBUTES or value in (None, ""):
            continue
        cleaned[key] = value
    return cleaned


def _pick(attrs: dict[str, Any], key: str, limit: int) -> str | None:
    value = attrs.get(key)
    if value in (None, ""):
        return None
    return str(value)[:limit]


def _point_columns(attributes: dict[str, Any]) -> dict[str, str | None]:
    return {
        column: _pick(attributes, key, _MAX_TEXT[column])
        for column, key in _DIMENSION_COLUMNS.items()
    }


def _base_point(
    *,
    project_id: str,
    service_name: str | None,
    metric_name: str,
    metric_type: str,
    unit: str | None,
    temporality: str,
    attributes: dict[str, Any],
    ts_start: datetime,
    ts_end: datetime,
) -> dict[str, Any]:
    cleaned = _clean_attributes(attributes)
    return {
        "project_id": project_id,
        "service_name": service_name[:128] if service_name else None,
        "metric_name": metric_name[:128],
        "metric_type": metric_type,
        "temporality": temporality,
        "unit": unit[:32] if unit else None,
        "ts_start": ts_start,
        "ts_end": ts_end,
        "value": None,
        "sample_count": None,
        "sample_sum": None,
        "bucket_counts": None,
        "explicit_bounds": None,
        "attributes": cleaned,
        "series_hash": _series_hash(cleaned),
        **_point_columns(cleaned),
    }


def _temporality_name(raw: Any) -> str:
    number = _as_int(raw)
    return _TEMPORALITY.get(number, "cumulative" if number == 2 else "delta")


def map_resource_metrics(
    resource_metrics: list[dict[str, Any]],
    *,
    default_project_id: str | None = None,
) -> tuple[list[dict[str, Any]], int]:
    """resourceMetrics → (metric_point dict 列表, 被拒绝的 data point 数)。"""
    fallback_project = default_project_id or OTLP_DEFAULT_PROJECT_ID
    points: list[dict[str, Any]] = []
    rejected = 0

    for resource_metric in resource_metrics or []:
        if not isinstance(resource_metric, dict):
            continue
        resource = resource_metric.get("resource") or {}
        resource_attrs = flatten_attributes(resource.get("attributes"))
        project_id = str(
            _first_value(resource_attrs, PROJECT_ID_KEYS) or fallback_project
        )[:64]
        service_name = resource_attrs.get("service.name")
        service_name = str(service_name) if service_name else None

        for scope_metric in resource_metric.get("scopeMetrics") or []:
            if not isinstance(scope_metric, dict):
                continue
            for metric in scope_metric.get("metrics") or []:
                if not isinstance(metric, dict):
                    continue
                try:
                    produced, bad = _map_metric(
                        metric,
                        project_id=project_id,
                        service_name=service_name,
                    )
                except Exception:  # 单个指标异常不影响整批
                    rejected += 1
                    continue
                points.extend(produced)
                rejected += bad

    return points, rejected


def _first_value(attrs: dict[str, Any], keys: tuple[str, ...]) -> Any:
    for key in keys:
        value = attrs.get(key)
        if value not in (None, ""):
            return value
    return None


def _map_metric(
    metric: dict[str, Any],
    *,
    project_id: str,
    service_name: str | None,
) -> tuple[list[dict[str, Any]], int]:
    name = metric.get("name")
    if not isinstance(name, str) or not name:
        return [], 1
    unit = metric.get("unit")
    unit = str(unit) if unit else None

    for kind in ("histogram", "sum", "gauge"):
        payload = metric.get(kind)
        if isinstance(payload, dict):
            break
    else:
        # exponentialHistogram 等暂不支持，按拒绝计数
        return [], 1

    data_points = payload.get("dataPoints") or []
    if not isinstance(data_points, list):
        return [], 1

    temporality = _temporality_name(payload.get("aggregationTemporality")) if kind != "gauge" else "instant"

    out: list[dict[str, Any]] = []
    rejected = 0
    for dp in data_points:
        if not isinstance(dp, dict):
            rejected += 1
            continue
        try:
            point = _map_data_point(
                dp,
                kind=kind,
                metric_name=name,
                unit=unit,
                temporality=temporality,
                project_id=project_id,
                service_name=service_name,
            )
        except Exception:
            rejected += 1
            continue
        if point is None:
            rejected += 1
            continue
        out.append(point)
    return out, rejected


def _map_data_point(
    dp: dict[str, Any],
    *,
    kind: str,
    metric_name: str,
    unit: str | None,
    temporality: str,
    project_id: str,
    service_name: str | None,
) -> dict[str, Any] | None:
    end_dt = _epoch_ns_to_dt(_parse_nano(dp.get("timeUnixNano")))
    if end_dt is None:
        return None
    start_dt = _epoch_ns_to_dt(_parse_nano(dp.get("startTimeUnixNano"))) or end_dt

    attributes = flatten_attributes(dp.get("attributes"))
    point = _base_point(
        project_id=project_id,
        service_name=service_name,
        metric_name=metric_name,
        metric_type=kind,
        unit=unit,
        temporality=temporality,
        attributes=attributes,
        ts_start=start_dt,
        ts_end=end_dt,
    )

    if kind == "histogram":
        point["sample_count"] = _as_int(dp.get("count"))
        point["sample_sum"] = _as_number(dp.get("sum"))
        point["bucket_counts"] = _as_int_list(dp.get("bucketCounts"))
        point["explicit_bounds"] = _as_float_list(dp.get("explicitBounds"))
        if point["sample_count"] is None:
            return None
    else:
        # sum / gauge：单值取 asDouble 或 asInt
        value = _as_number(dp.get("asDouble"))
        if value is None:
            value = _as_number(dp.get("asInt"))
        point["value"] = value
        point["sample_sum"] = value
        point["sample_count"] = 1 if value is not None else None
    return point


__all__ = [
    "VOLATILE_ATTRIBUTES",
    "map_resource_metrics",
]
