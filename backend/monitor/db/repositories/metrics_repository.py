"""读面仓储：指标看板查询。

三个数据源分别对应方案里的三类指标：

- `metric_point`   —— OTel 指标（单次调用级，客户端预聚合）
- `traces` / `spans` —— 轨迹表指标（单条 trace）
- `trajectory_fact` —— 轨迹分析指标（整轮 / 整会话效率）

聚合口径严格按 docs/trace-metrics-design.md §1.1 / §3 / §4.5：
平均耗时一律 `SUM(sample_sum) / NULLIF(SUM(sample_count), 0)`，
不对各 data point 的平均值再平均；比率类用分子分母分别求和后再除。
"""
from __future__ import annotations

import json
from datetime import datetime
from typing import Any

from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

LLM_DURATION = "gen_ai.client.operation.duration"
TOKEN_USAGE = "gen_ai.client.token.usage"
TOOL_DURATION = "chrys.function.invocation.duration"

# metric 别名 → (metric_name, 值表达式)
# 值表达式里出现的聚合都在 SQL 里完成，避免把 data point 拉回 Python 再算。
_METRICS: dict[str, tuple[str, str]] = {
    "tokens": (TOKEN_USAGE, "SUM(sample_sum)"),
    "llm_calls": (LLM_DURATION, "SUM(sample_count)"),
    "llm_duration": (
        LLM_DURATION,
        "SUM(sample_sum) / NULLIF(SUM(sample_count), 0) * 1000",
    ),
    "tool_calls": (TOOL_DURATION, "SUM(sample_count)"),
    "tool_duration": (
        TOOL_DURATION,
        "SUM(sample_sum) / NULLIF(SUM(sample_count), 0) * 1000",
    ),
    "tool_failures": (
        TOOL_DURATION,
        "SUM(CASE WHEN error_type IS NOT NULL AND error_type <> '' "
        "THEN sample_count ELSE 0 END)",
    ),
}

# breakdown 维度白名单（列名直接拼进 SQL，必须过滤）
_DIMENSIONS = (
    "token_type",
    "request_model",
    "response_model",
    "provider",
    "operation",
    "tool_name",
    "error_type",
)

# timeseries 时间粒度：别名 → 桶宽（秒）
_BUCKETS = {
    "1m": 60,
    "5m": 300,
    "15m": 900,
    "30m": 1800,
    "1h": 3600,
    "6h": 21600,
    "1d": 86400,
}


def _json_value(value: Any) -> Any:
    """jsonb 列经 asyncpg 返回 str，前端需要原生对象。"""
    if value is None or isinstance(value, (dict, list)):
        return value
    if isinstance(value, str):
        try:
            return json.loads(value)
        except (json.JSONDecodeError, TypeError):
            return value
    return value


def _iso(value: Any) -> str | None:
    if value is None:
        return None
    return value.isoformat() if hasattr(value, "isoformat") else str(value)


def _number(value: Any, default: float = 0.0) -> float:
    return float(value) if value is not None else default


def _int(value: Any) -> int:
    return int(value) if value is not None else 0


def metric_spec(metric: str) -> tuple[str, str]:
    """metric 别名 → (metric_name, 值表达式)；非法别名抛 ValueError。"""
    key = (metric or "").strip().lower()
    if key not in _METRICS:
        raise ValueError(f"不支持的 metric: {metric!r}，可选 {tuple(_METRICS)}")
    return _METRICS[key]


def bucket_seconds(interval: str) -> int:
    key = (interval or "").strip().lower()
    if key not in _BUCKETS:
        raise ValueError(
            f"不支持的 interval: {interval!r}，可选 {tuple(_BUCKETS)}"
        )
    return _BUCKETS[key]


class MetricsRepository:
    def __init__(self, session: AsyncSession):
        self._session = session

    # ---- KPI 卡片 ----

    async def summary(
        self,
        project_id: str,
        start: datetime,
        end: datetime,
    ) -> dict[str, Any]:
        token_rows = await self._group_token_usage(project_id, start, end)
        tokens = {"input": 0, "output": 0, "cacheRead": 0}
        for row in token_rows:
            kind = (row["token_type"] or "").strip().lower()
            if kind in ("input", "output"):
                tokens[kind] = int(round(_number(row["amount"])))

        llm = await self._single_metric(project_id, LLM_DURATION, start, end)
        tool = await self._single_metric(project_id, TOOL_DURATION, start, end)

        trace_row = (
            await self._session.execute(
                text(
                    """
                    SELECT COUNT(*) AS trace_count,
                           COUNT(DISTINCT session_id) AS session_count,
                           COALESCE(AVG(CASE WHEN status = 'success' THEN 1.0
                                             ELSE 0.0 END), 0) AS success_rate,
                           PERCENTILE_CONT(0.95) WITHIN GROUP (
                               ORDER BY duration_ms
                           ) AS p95_duration_ms
                    FROM traces
                    WHERE project_id = :project_id
                      AND created_at >= :start AND created_at < :end
                    """
                ),
                {"project_id": project_id, "start": start, "end": end},
            )
        ).mappings().one()

        tokens["cacheRead"] = _int(
            await self._cache_read_tokens(project_id, start, end)
        )

        calls = _int(llm["calls"])
        tool_calls = _int(tool["calls"])
        return {
            "range": {"from": _iso(start), "to": _iso(end)},
            "tokens": tokens,
            "llm": {
                "calls": calls,
                "avgDurationMs": (
                    round(_number(llm["total"]) / calls * 1000, 1) if calls else 0
                ),
            },
            "tools": {
                "calls": tool_calls,
                "failureRate": (
                    round(_int(tool["failures"]) / tool_calls, 4) if tool_calls else 0
                ),
                "avgDurationMs": (
                    round(_number(tool["total"]) / tool_calls * 1000, 1)
                    if tool_calls
                    else 0
                ),
            },
            "traces": {
                "count": _int(trace_row["trace_count"]),
                "sessions": _int(trace_row["session_count"]),
                "successRate": round(_number(trace_row["success_rate"]), 4),
                "p95DurationMs": (
                    round(_number(trace_row["p95_duration_ms"]), 1)
                    if trace_row["p95_duration_ms"] is not None
                    else 0
                ),
            },
        }

    async def _group_token_usage(
        self, project_id: str, start: datetime, end: datetime
    ) -> list[dict[str, Any]]:
        return (
            (
                await self._session.execute(
                    text(
                        """
                        SELECT token_type, COALESCE(SUM(sample_sum), 0) AS amount
                        FROM metric_point
                        WHERE project_id = :project_id
                          AND metric_name = :metric_name
                          AND ts_end >= :start AND ts_end < :end
                        GROUP BY token_type
                        """
                    ),
                    {
                        "project_id": project_id,
                        "metric_name": TOKEN_USAGE,
                        "start": start,
                        "end": end,
                    },
                )
            )
            .mappings()
            .all()
        )

    async def _single_metric(
        self,
        project_id: str,
        metric_name: str,
        start: datetime,
        end: datetime,
    ) -> dict[str, Any]:
        return (
            (
                await self._session.execute(
                    text(
                        """
                        SELECT COALESCE(SUM(sample_count), 0) AS calls,
                               COALESCE(SUM(sample_sum), 0) AS total,
                               COALESCE(SUM(CASE WHEN error_type IS NOT NULL
                                                  AND error_type <> ''
                                                 THEN sample_count ELSE 0 END), 0)
                                   AS failures
                        FROM metric_point
                        WHERE project_id = :project_id
                          AND metric_name = :metric_name
                          AND ts_end >= :start AND ts_end < :end
                        """
                    ),
                    {
                        "project_id": project_id,
                        "metric_name": metric_name,
                        "start": start,
                        "end": end,
                    },
                )
            )
            .mappings()
            .one()
        )

    async def _cache_read_tokens(
        self, project_id: str, start: datetime, end: datetime
    ) -> int:
        """缓存读 token 只在 span 属性里（token.usage 指标没有 cache 桶）。"""
        return _int(
            (
                await self._session.execute(
                    text(
                        """
                        SELECT COALESCE(SUM(value), 0) AS total
                        FROM (
                            SELECT CASE
                                       WHEN (attributes ->> 'gen_ai.usage.cache_read.input_tokens')
                                            ~ '^[0-9]+(\\.[0-9]+)?$'
                                       THEN (attributes ->> 'gen_ai.usage.cache_read.input_tokens')
                                            ::double precision
                                       ELSE 0
                                   END AS value
                            FROM spans
                            WHERE project_id = :project_id
                              AND created_at >= :start AND created_at < :end
                              AND attributes ? 'gen_ai.usage.cache_read.input_tokens'
                        ) t
                        """
                    ),
                    {"project_id": project_id, "start": start, "end": end},
                )
            ).scalar_one()
        )

    # ---- 趋势 ----

    async def timeseries(
        self,
        project_id: str,
        start: datetime,
        end: datetime,
        metric: str,
        interval: str,
        group_by: str | None = None,
    ) -> dict[str, Any]:
        metric_name, value_expr = metric_spec(metric)
        bucket = bucket_seconds(interval)

        if group_by:
            dimension = group_by.strip().lower()
            if dimension not in _DIMENSIONS:
                raise ValueError(
                    f"不支持的 group_by: {group_by!r}，可选 {_DIMENSIONS}"
                )
            series_expr = f"COALESCE({dimension}, 'unknown')"
        else:
            dimension = None
            series_expr = None

        sql_series = series_expr or "CAST(:series_label AS text)"
        params: dict[str, Any] = {
            "project_id": project_id,
            "metric_name": metric_name,
            "start": start,
            "end": end,
            "bucket": bucket,
            "series_label": metric,
        }
        rows = (
            (
                await self._session.execute(
                    text(
                        f"""
                        SELECT to_timestamp(
                                   (floor(extract(epoch FROM ts_end) / :bucket)
                                    * :bucket)::double precision
                               ) AS bucket,
                               {sql_series} AS series,
                               {value_expr} AS value,
                               COALESCE(SUM(sample_count), 0) AS calls
                        FROM metric_point
                        WHERE project_id = :project_id
                          AND metric_name = :metric_name
                          AND ts_end >= :start AND ts_end < :end
                        GROUP BY 1, 2
                        ORDER BY 1
                        """
                    ),
                    params,
                )
            )
            .mappings()
            .all()
        )
        return {
            "metric": metric,
            "interval": interval,
            "groupBy": dimension,
            "points": [
                {
                    "ts": _iso(row["bucket"]),
                    "series": row["series"],
                    "value": round(_number(row["value"]), 3),
                    "calls": _int(row["calls"]),
                }
                for row in rows
            ],
        }

    # ---- Top N ----

    async def breakdown(
        self,
        project_id: str,
        start: datetime,
        end: datetime,
        metric: str,
        dimension: str,
        limit: int = 10,
    ) -> dict[str, Any]:
        metric_name, value_expr = metric_spec(metric)
        dim = (dimension or "").strip().lower()
        if dim not in _DIMENSIONS:
            raise ValueError(f"不支持的 dimension: {dimension!r}，可选 {_DIMENSIONS}")

        rows = (
            (
                await self._session.execute(
                    text(
                        f"""
                        SELECT COALESCE({dim}, 'unknown') AS name,
                               {value_expr} AS value,
                               COALESCE(SUM(sample_count), 0) AS calls
                        FROM metric_point
                        WHERE project_id = :project_id
                          AND metric_name = :metric_name
                          AND ts_end >= :start AND ts_end < :end
                        GROUP BY 1
                        ORDER BY 2 DESC NULLS LAST
                        LIMIT :limit
                        """
                    ),
                    {
                        "project_id": project_id,
                        "metric_name": metric_name,
                        "start": start,
                        "end": end,
                        "limit": max(1, min(limit, 50)),
                    },
                )
            )
            .mappings()
            .all()
        )
        return {
            "metric": metric,
            "dimension": dim,
            "items": [
                {
                    "name": row["name"],
                    "value": round(_number(row["value"]), 3),
                    "calls": _int(row["calls"]),
                }
                for row in rows
            ],
        }

    # ---- 慢 / 贵轨迹 ----

    async def top_traces(
        self,
        project_id: str,
        start: datetime,
        end: datetime,
        order_by: str = "duration",
        limit: int = 10,
    ) -> dict[str, Any]:
        order = "t.duration_ms DESC NULLS LAST"
        if (order_by or "").strip().lower() in ("tokens", "token"):
            order = "total_tokens DESC NULLS LAST"

        rows = (
            (
                await self._session.execute(
                    text(
                        f"""
                        SELECT t.id, t.name, t.session_id, t.status,
                               t.duration_ms, t.started_at, t.created_at,
                               COALESCE(o.total_tokens, 0) AS total_tokens
                        FROM traces t
                        LEFT JOIN (
                            SELECT trace_id, SUM(total_tokens) AS total_tokens
                            FROM observations
                            GROUP BY trace_id
                        ) o ON o.trace_id = t.id
                        WHERE t.project_id = :project_id
                          AND t.created_at >= :start AND t.created_at < :end
                        ORDER BY {order}
                        LIMIT :limit
                        """
                    ),
                    {
                        "project_id": project_id,
                        "start": start,
                        "end": end,
                        "limit": max(1, min(limit, 100)),
                    },
                )
            )
            .mappings()
            .all()
        )
        return {
            "orderBy": order_by,
            "items": [
                {
                    "id": str(row["id"]),
                    "name": row["name"],
                    "sessionId": row["session_id"],
                    "status": row["status"],
                    "durationMs": row["duration_ms"],
                    "totalTokens": _int(row["total_tokens"]),
                    "startedAt": _iso(row["started_at"] or row["created_at"]),
                }
                for row in rows
            ],
        }

    # ---- 轨迹分析（整轮 / 整会话效率） ----

    async def trajectory_sessions(
        self, project_id: str, limit: int = 50
    ) -> dict[str, Any]:
        """已导入轨迹分析的会话列表，按导出时间倒序。"""
        rows = (
            (
                await self._session.execute(
                    text(
                        """
                        SELECT session_id, exported_at, availability, turn_count,
                               elapsed_ns, exclusive_work_ns, parallelism,
                               overlap_gain_ns, cache_hit_rate
                        FROM trajectory_fact
                        WHERE project_id = :project_id
                        ORDER BY exported_at DESC NULLS LAST
                        LIMIT :limit
                        """
                    ),
                    {
                        "project_id": project_id,
                        "limit": max(1, min(limit, 200)),
                    },
                )
            )
            .mappings()
            .all()
        )
        return {
            "items": [
                {
                    "sessionId": row["session_id"],
                    "exportedAt": _iso(row["exported_at"]),
                    "availability": row["availability"],
                    "turnCount": row["turn_count"],
                    "elapsedNs": row["elapsed_ns"],
                    "exclusiveWorkNs": row["exclusive_work_ns"],
                    "parallelism": row["parallelism"],
                    "overlapGainNs": row["overlap_gain_ns"],
                    "cacheHitRate": row["cache_hit_rate"],
                }
                for row in rows
            ]
        }

    async def trajectory(
        self, project_id: str, session_id: str
    ) -> dict[str, Any] | None:
        row = (
            (
                await self._session.execute(
                    text(
                        """
                        SELECT *
                        FROM trajectory_fact
                        WHERE project_id = :project_id AND session_id = :session_id
                        """
                    ),
                    {"project_id": project_id, "session_id": session_id},
                )
            )
            .mappings()
            .one_or_none()
        )
        if row is None:
            return None
        return {
            "sessionId": row["session_id"],
            "exportedAt": _iso(row["exported_at"]),
            "availability": row["availability"],
            "turnCount": row["turn_count"],
            "elapsedNs": row["elapsed_ns"],
            "exclusiveWorkNs": row["exclusive_work_ns"],
            "responseCpNs": row["response_cp_ns"],
            "computeCpNs": row["compute_cp_ns"],
            "parallelism": row["parallelism"],
            "overlapGainNs": row["overlap_gain_ns"],
            "cacheHitRate": row["cache_hit_rate"],
            "wallTime": _json_value(row["wall_time"]),
            "utilization": _json_value(row["utilization"]),
            "tokens": _json_value(row["tokens"]),
            "operations": _json_value(row["operations"]),
            "skills": _json_value(row["skills"]),
            "mcp": _json_value(row["mcp"]),
            "validation": _json_value(row["validation"]),
            "findings": _json_value(row["findings"]),
            "raw": _json_value(row["raw"]),
        }
