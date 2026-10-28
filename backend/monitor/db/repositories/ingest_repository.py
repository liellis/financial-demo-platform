"""写面仓储：把 OTLP 接收端的事件/指标落进 PostgreSQL。

与查询侧 `TraceRepository` 分开：这里只做幂等 UPSERT，不做读取。

幂等策略：
- traces / spans / observations 用 `id` 冲突键 UPSERT（`event_id` 只作唯一约束）
- 乱序占位 trace 用 `trace_insert_only` 标记，命中冲突时不做覆盖
- metric_point 用 (project_id, metric_name, series_hash, ts_start, ts_end) 去重，
  重放只 `DO NOTHING`
"""
from __future__ import annotations

import json
from datetime import datetime, timezone
from typing import Any
from zoneinfo import ZoneInfo

from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

from ...config import OTLP_TIMEZONE

try:  # Windows 自带 tzdata 缺失时回落到固定 +08:00
    _TZ = ZoneInfo(OTLP_TIMEZONE)
except Exception:  # pragma: no cover
    _TZ = timezone.utc


def _json(value: Any) -> str | None:
    """任意对象 → JSON 字符串（列侧统一 `::jsonb` 转换）。"""
    if value is None:
        return None
    if isinstance(value, str):
        try:
            return json.dumps(json.loads(value), ensure_ascii=False)
        except (json.JSONDecodeError, TypeError):
            return json.dumps(value, ensure_ascii=False)
    return json.dumps(value, ensure_ascii=False, default=str)


def _naive_dt(value: Any) -> datetime | None:
    """墙钟时间 → 无时区 datetime（started_at 列语义：上报什么存什么）。"""
    if value is None:
        return None
    if isinstance(value, datetime):
        return value.replace(tzinfo=None)
    if isinstance(value, str):
        try:
            return datetime.fromisoformat(value.replace("Z", "+00:00")).replace(tzinfo=None)
        except ValueError:
            return None
    return None


def _aware_dt(value: Any) -> datetime | None:
    """墙钟时间 → 带时区 datetime（created_at 是 TIMESTAMPTZ，按本地时区换算）。"""
    if value is None:
        return None
    if isinstance(value, datetime):
        return value if value.tzinfo else value.replace(tzinfo=_TZ)
    if isinstance(value, str):
        try:
            parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
        except ValueError:
            return None
        return parsed if parsed.tzinfo else parsed.replace(tzinfo=_TZ)
    return None


class IngestRepository:
    def __init__(self, session: AsyncSession):
        self._session = session

    # ---- 事件（trace / span / observation / session）----

    async def write_events(self, events: list[dict[str, Any]]) -> None:
        """按 event_type 逐条写入（同一事务内批量提交）。"""
        for ev in events:
            if not isinstance(ev, dict):
                continue
            etype = ev.get("event_type")
            if etype == "trace":
                await self._write_trace(ev)
            elif etype == "span":
                await self._write_span(ev)
            elif etype == "observation":
                await self._write_observation(ev)
            elif etype == "session":
                await self._write_session(ev)

    async def _ensure_trace(self, ev: dict[str, Any]) -> None:
        trace_id = ev.get("trace_id")
        if not trace_id:
            return
        await self._session.execute(
            text(
                """
                INSERT INTO traces (id, event_id, project_id, status, created_at)
                VALUES (:id, :id, :project_id, 'success', :created_at)
                ON CONFLICT (id) DO NOTHING
                """
            ),
            {
                "id": str(trace_id),
                "project_id": ev.get("project_id"),
                "created_at": _aware_dt(ev.get("created_at")),
            },
        )

    async def _ensure_span(self, ev: dict[str, Any]) -> None:
        span_id = ev.get("span_id")
        if not span_id:
            return
        await self._session.execute(
            text(
                """
                INSERT INTO spans (id, event_id, project_id, trace_id, name, span_type, status, created_at)
                VALUES (:id, :id, :project_id, :trace_id, 'pending', 'llm', 'success', :created_at)
                ON CONFLICT (id) DO NOTHING
                """
            ),
            {
                "id": str(span_id),
                "project_id": ev.get("project_id"),
                "trace_id": str(ev.get("trace_id")),
                "created_at": _aware_dt(ev.get("created_at")),
            },
        )

    async def _write_trace(self, ev: dict[str, Any]) -> None:
        trace_id = ev.get("id") or ev.get("trace_id")
        if not trace_id:
            return
        created_at = _aware_dt(ev.get("created_at"))
        session_id = ev.get("session_id")

        # 乱序占位：只保证行存在，不能覆盖后续到达的完整 trace
        if ev.get("trace_insert_only"):
            await self._session.execute(
                text(
                    """
                    INSERT INTO traces (id, event_id, project_id, session_id, status, created_at)
                    VALUES (:id, :event_id, :project_id, :session_id, 'success', :created_at)
                    ON CONFLICT (id) DO NOTHING
                    """
                ),
                {
                    "id": str(trace_id),
                    "event_id": str(ev.get("event_id") or trace_id),
                    "project_id": ev.get("project_id"),
                    "session_id": session_id,
                    "created_at": created_at,
                },
            )
            return

        if session_id:
            await self._session.execute(
                text(
                    """
                    INSERT INTO sessions (id, project_id, name, created_at)
                    VALUES (:id, :project_id, :name, :created_at)
                    ON CONFLICT (id) DO NOTHING
                    """
                ),
                {
                    "id": str(session_id)[:64],
                    "project_id": ev.get("project_id"),
                    "name": f"session-{str(session_id)[:8]}",
                    "created_at": created_at,
                },
            )

        result = await self._session.execute(
            text(
                """
                INSERT INTO traces (id, event_id, project_id, session_id, name, agent_name,
                                    input, output, duration_ms,
                                    react_step_count, tool_count, span_count,
                                    status, error_info, started_at, created_at)
                VALUES (:id, :event_id, :project_id, :session_id, :name, :agent_name,
                        (:input)::jsonb, (:output)::jsonb, :duration_ms,
                        :react_step_count, :tool_count, :span_count,
                        :status, (:error_info)::jsonb, :started_at, :created_at)
                ON CONFLICT (id) DO UPDATE SET
                    event_id = EXCLUDED.event_id,
                    project_id = EXCLUDED.project_id,
                    session_id = EXCLUDED.session_id,
                    name = EXCLUDED.name,
                    agent_name = EXCLUDED.agent_name,
                    input = EXCLUDED.input,
                    output = EXCLUDED.output,
                    duration_ms = EXCLUDED.duration_ms,
                    react_step_count = EXCLUDED.react_step_count,
                    tool_count = EXCLUDED.tool_count,
                    span_count = EXCLUDED.span_count,
                    status = EXCLUDED.status,
                    error_info = EXCLUDED.error_info,
                    started_at = EXCLUDED.started_at,
                    created_at = COALESCE(traces.created_at, EXCLUDED.created_at),
                    updated_at = now()
                RETURNING (xmax = 0) AS inserted
                """
            ),
            {
                "id": str(trace_id),
                "event_id": str(ev.get("event_id") or trace_id),
                "project_id": ev.get("project_id"),
                "session_id": session_id,
                "name": ev.get("name"),
                "agent_name": ev.get("agent_name"),
                "input": _json(ev.get("input")),
                "output": _json(ev.get("output")),
                "duration_ms": ev.get("duration_ms"),
                "react_step_count": int(ev.get("react_step_count") or 0),
                "tool_count": int(ev.get("tool_count") or 0),
                "span_count": int(ev.get("span_count") or 0),
                "status": ev.get("status") or "success",
                "error_info": _json(ev.get("error_info")),
                "started_at": _naive_dt(ev.get("started_at")),
                "created_at": created_at,
            },
        )
        inserted = result.scalar_one_or_none()
        # 只有首次插入才累加会话统计，重复事件不会重复计数
        if inserted and session_id:
            await self._session.execute(
                text(
                    """
                    UPDATE sessions
                    SET updated_at = now(),
                        trace_count = sessions.trace_count + 1,
                        total_tokens = sessions.total_tokens + :tokens,
                        total_duration_ms = sessions.total_duration_ms + :duration
                    WHERE id = :id
                    """
                ),
                {
                    "id": str(session_id)[:64],
                    "tokens": int(ev.get("total_tokens") or 0),
                    "duration": int(ev.get("duration_ms") or 0),
                },
            )

    async def _write_span(self, ev: dict[str, Any]) -> None:
        span_id = ev.get("id") or ev.get("span_id")
        trace_id = ev.get("trace_id")
        if not span_id or not trace_id:
            return
        await self._ensure_trace(ev)

        otel = ev.get("_otel") or {}
        attributes = otel.get("attributes") if isinstance(otel, dict) else None
        started_at = _naive_dt(ev.get("started_at"))
        created_at = _aware_dt(ev.get("created_at"))

        await self._session.execute(
            text(
                """
                INSERT INTO spans (id, event_id, project_id, trace_id, parent_span_id, name,
                                   span_type, input, output, duration_ms, model, tool_name,
                                   status, error_info, attributes, started_at, created_at)
                VALUES (:id, :event_id, :project_id, :trace_id, :parent_span_id, :name,
                        :span_type, (:input)::jsonb, (:output)::jsonb, :duration_ms,
                        :model, :tool_name, :status, (:error_info)::jsonb,
                        (:attributes)::jsonb, :started_at, :created_at)
                ON CONFLICT (id) DO UPDATE SET
                    event_id = EXCLUDED.event_id,
                    project_id = EXCLUDED.project_id,
                    trace_id = EXCLUDED.trace_id,
                    parent_span_id = EXCLUDED.parent_span_id,
                    name = EXCLUDED.name,
                    span_type = EXCLUDED.span_type,
                    input = EXCLUDED.input,
                    output = EXCLUDED.output,
                    duration_ms = EXCLUDED.duration_ms,
                    model = EXCLUDED.model,
                    tool_name = EXCLUDED.tool_name,
                    status = EXCLUDED.status,
                    error_info = EXCLUDED.error_info,
                    attributes = EXCLUDED.attributes,
                    started_at = EXCLUDED.started_at,
                    created_at = COALESCE(spans.created_at, EXCLUDED.created_at),
                    updated_at = now()
                """
            ),
            {
                "id": str(span_id),
                "event_id": str(ev.get("event_id") or span_id),
                "project_id": ev.get("project_id"),
                "trace_id": str(trace_id),
                "parent_span_id": (
                    str(ev["parent_span_id"]) if ev.get("parent_span_id") else None
                ),
                "name": ev.get("name") or "span",
                "span_type": ev.get("span_type") or "span",
                "input": _json(ev.get("input")),
                "output": _json(ev.get("output")),
                "duration_ms": ev.get("duration_ms"),
                "model": ev.get("model"),
                "tool_name": ev.get("tool_name"),
                "status": ev.get("status") or "success",
                "error_info": _json(ev.get("error_info")),
                "attributes": _json(attributes),
                "started_at": started_at,
                "created_at": created_at,
            },
        )

    async def _write_observation(self, ev: dict[str, Any]) -> None:
        obs_id = ev.get("id") or ev.get("event_id")
        trace_id = ev.get("trace_id")
        if not obs_id or not trace_id:
            return
        await self._ensure_trace(ev)
        await self._ensure_span(ev)

        await self._session.execute(
            text(
                """
                INSERT INTO observations (id, event_id, project_id, trace_id, span_id, model,
                                          prompt_tokens, completion_tokens, total_tokens,
                                          input, output, created_at)
                VALUES (:id, :event_id, :project_id, :trace_id, :span_id, :model,
                        :prompt_tokens, :completion_tokens, :total_tokens,
                        (:input)::jsonb, (:output)::jsonb, :created_at)
                ON CONFLICT (id) DO UPDATE SET
                    event_id = EXCLUDED.event_id,
                    project_id = EXCLUDED.project_id,
                    trace_id = EXCLUDED.trace_id,
                    span_id = EXCLUDED.span_id,
                    model = EXCLUDED.model,
                    prompt_tokens = EXCLUDED.prompt_tokens,
                    completion_tokens = EXCLUDED.completion_tokens,
                    total_tokens = EXCLUDED.total_tokens,
                    input = EXCLUDED.input,
                    output = EXCLUDED.output
                """
            ),
            {
                "id": str(obs_id),
                "event_id": str(ev.get("event_id") or obs_id),
                "project_id": ev.get("project_id"),
                "trace_id": str(trace_id),
                "span_id": (
                    str(ev["span_id"]) if ev.get("span_id") else None
                ),
                "model": ev.get("model"),
                "prompt_tokens": int(ev.get("prompt_tokens") or 0),
                "completion_tokens": int(ev.get("completion_tokens") or 0),
                "total_tokens": int(ev.get("total_tokens") or 0),
                "input": _json(ev.get("input")),
                "output": _json(ev.get("output")),
                "created_at": _aware_dt(ev.get("created_at")),
            },
        )

    async def _write_session(self, ev: dict[str, Any]) -> None:
        session_id = ev.get("id") or ev.get("session_id")
        if not session_id:
            return
        await self._session.execute(
            text(
                """
                INSERT INTO sessions (id, project_id, name, user_id, metadata, created_at)
                VALUES (:id, :project_id, :name, :user_id, (:metadata)::jsonb, :created_at)
                ON CONFLICT (id) DO UPDATE SET
                    name = COALESCE(EXCLUDED.name, sessions.name),
                    user_id = COALESCE(EXCLUDED.user_id, sessions.user_id),
                    metadata = COALESCE(EXCLUDED.metadata, sessions.metadata),
                    updated_at = now()
                """
            ),
            {
                "id": str(session_id)[:64],
                "project_id": ev.get("project_id"),
                "name": ev.get("name"),
                "user_id": ev.get("user_id"),
                "metadata": _json(ev.get("metadata")),
                "created_at": _aware_dt(ev.get("created_at")),
            },
        )

    # ---- OTel 指标 ----

    async def write_metric_points(self, points: list[dict[str, Any]]) -> None:
        """批量写入 data point；同 series 同窗口重放直接忽略。"""
        if not points:
            return
        rows = [
            {
                **point,
                "attributes": _json(point.get("attributes")),
                "bucket_counts": point.get("bucket_counts"),
                "explicit_bounds": point.get("explicit_bounds"),
            }
            for point in points
        ]
        await self._session.execute(
            text(
                """
                INSERT INTO metric_point (
                    project_id, service_name, metric_name, metric_type, temporality, unit,
                    ts_start, ts_end, value, sample_count, sample_sum,
                    bucket_counts, explicit_bounds,
                    operation, provider, request_model, response_model,
                    token_type, tool_name, error_type,
                    attributes, series_hash
                ) VALUES (
                    :project_id, :service_name, :metric_name, :metric_type, :temporality, :unit,
                    :ts_start, :ts_end, :value, :sample_count, :sample_sum,
                    :bucket_counts, :explicit_bounds,
                    :operation, :provider, :request_model, :response_model,
                    :token_type, :tool_name, :error_type,
                    (:attributes)::jsonb, :series_hash
                )
                ON CONFLICT (project_id, metric_name, series_hash, ts_start, ts_end)
                DO NOTHING
                """
            ),
            rows,
        )
