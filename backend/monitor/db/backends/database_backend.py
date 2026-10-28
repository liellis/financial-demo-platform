"""database 存储后端：基于现有 SQLAlchemy Repository 的查询实现。"""
from __future__ import annotations

import json
from datetime import datetime
from typing import Any

from sqlalchemy.ext.asyncio import AsyncSession

from ..repositories.trace_repository import TraceRepository
from .base import SpanData, StorageBackend, TraceData


def _decode_jsonb(value: Any) -> Any:
    """ORM jsonb 字段默认以 str 返回，前端需要原生对象。"""
    if value is None or isinstance(value, (dict, list)):
        return value
    if isinstance(value, str):
        try:
            return json.loads(value)
        except (json.JSONDecodeError, TypeError):
            return value
    return value


class DatabaseBackend(StorageBackend):
    def __init__(self, session: AsyncSession):
        self._trace_repo = TraceRepository(session)

    # ---- Trace 查询 ----

    async def list_traces(
        self,
        project_id: str,
        page: int = 1,
        page_size: int = 20,
        agent_name: str | None = None,
        status: str | None = None,
        session_id: str | None = None,
        trace_id: str | None = None,
    ) -> tuple[list[dict[str, Any]], int]:
        rows, total = await self._trace_repo.list_traces(
            project_id,
            page=page,
            page_size=page_size,
            agent_name=agent_name,
            status=status,
            session_id=session_id,
            trace_id=trace_id,
        )
        # ORM → dict（Route 层不再依赖 ORM）
        items = [_trace_dict(t) for t in rows]
        await self._apply_token_totals(items)
        return items, total

    async def _apply_token_totals(self, items: list[dict[str, Any]]) -> None:
        """把 observations 实时聚合的 token 用量覆盖到 trace dict。

        traces 表的 total_tokens 是 SDK 单进程汇总，跨进程子 agent 会漏计；
        查询时按 trace_id SUM observations 才能得到全量正确值。
        total 取 observations.total_tokens 原值之和（模型可能把缓存/系统开销
        计入 total 而不计入 prompt/completion，故 total != prompt + completion）。
        """
        if not items:
            return
        ids = [str(t["id"]) for t in items]
        totals = await self._trace_repo.get_token_totals(ids)
        for t in items:
            agg = totals.get(t["id"])
            prompt = agg["prompt_tokens"] if agg else 0
            completion = agg["completion_tokens"] if agg else 0
            total = agg["total_tokens"] if agg else 0
            t["total_tokens"] = total or (prompt + completion)
            t["prompt_tokens"] = prompt
            t["completion_tokens"] = completion

    async def get_trace_detail(
        self,
        project_id: str,
        trace_id: str,
        session_id: str | None = None,
    ) -> TraceData | None:
        t = await self._trace_repo.get_trace(project_id, trace_id)
        if t is None:
            return None
        spans = await self._trace_repo.get_spans(trace_id)
        obs = await self._trace_repo.get_observations(trace_id)
        trace = _trace_dict(t)
        await self._apply_token_totals([trace])
        return TraceData(
            trace=trace,
            spans=[_span_dict(s) for s in spans],
            observations=[_obs_dict(o) for o in obs],
        )

    async def get_llm_request(
        self, project_id: str, event_id: str
    ) -> dict[str, Any] | None:
        r = await self._trace_repo.get_llm_request(project_id, event_id)
        return _llm_request_dict(r) if r is not None else None

    async def get_span_detail(
        self,
        project_id: str,
        trace_id: str,
        span_id: str,
        session_id: str | None = None,
    ) -> SpanData | None:
        """单 span 懒加载：spans.id 主键 + idx_observations_span 两次索引命中。"""
        s = await self._trace_repo.get_span(span_id)
        if s is None or str(s.trace_id) != trace_id:
            # 归属校验：跨 trace 串号一律按「不存在」处理，避免前端缓存 key 冲突
            return None
        obs = await self._trace_repo.get_observations_by_span(span_id)
        return SpanData(span=_span_dict(s), observations=[_obs_dict(o) for o in obs])

    # ---- 文件模式还原 ----

    async def export_trace_jsonl(
        self,
        project_id: str,
        trace_id: str,
        session_id: str | None = None,
    ) -> str | None:
        """按 trace_id 从库表重拼文件模式 <trace_id>.jsonl 的全文。

        字段顺序逐行对齐 SDK FileExporter 落盘的真实 jsonl 行。DB 未持久化的列
        （span 的 model_parameters/level/metadata/tags/time_to_first_token_ms、
        trace 的 tags、session 事件的 event_id）统一输出 null，保持行形状一致。

        不输出 llm_requests：文件模式里它以独立 llm_request/<event_id>.json 落盘，
        不在 <trace_id>.jsonl 行集合内。
        """
        t = await self._trace_repo.get_trace(project_id, trace_id)
        if t is None:
            return None
        # token 需实时聚合（traces 表不落盘 token 汇总）
        trace = _trace_dict(t)
        await self._apply_token_totals([trace])
        sid = trace.get("session_id")

        spans = await self._trace_repo.get_spans(trace_id)
        obs = await self._trace_repo.get_observations(trace_id)

        lines: list[dict[str, Any]] = []
        if sid:
            lines.append(await self._session_event_line(sid, trace))
        # 文件是 append 顺序（created_at 非单调），此处按 created_at 归并 span/observation，
        # 近似还原交错写入的顺序
        children: list[tuple[Any, dict[str, Any]]] = []
        for s in spans:
            children.append((s.created_at, _span_event_line(s, sid)))
        for o in obs:
            children.append((o.created_at, _observation_event_line(o, sid)))
        # created_at 为 None 的行排最后（对齐 Java 的 nullsLast）
        children.sort(key=lambda pair: (pair[0] is None, pair[0]))
        lines.extend(ev for _, ev in children)
        # trace 汇总事件在文件里最后写入
        lines.append(_trace_event_line(trace))
        return "".join(
            json.dumps(ev, ensure_ascii=False, default=str) + "\n" for ev in lines
        )

    async def _session_event_line(
        self, session_id: str, trace: dict[str, Any]
    ) -> dict[str, Any]:
        s = await self._trace_repo.get_session(session_id)
        return {
            "event_type": "session",
            # sessions 表未落盘 session 事件的 event_id，只能输出 null
            "event_id": None,
            "project_id": trace.get("project_id"),
            "created_at": _iso(s.created_at) if s else None,
            "session_id": session_id,
            "trace_id": trace.get("id"),
            "name": s.name if s else None,
            "user_id": s.user_id if s else None,
            "metadata": _decode_jsonb(s.metadata_json) if s else None,
        }

# ---- ORM 对象 → JSONL 事件行（字段顺序对齐 SDK events.py 的 to_dict()）----

def _span_event_line(s: Any, session_id: str | None) -> dict[str, Any]:
    return {
        "event_type": "span",
        "event_id": str(s.event_id) if s.event_id else None,
        "project_id": str(s.project_id),
        "created_at": _iso(s.created_at),
        "id": str(s.id),
        "trace_id": str(s.trace_id),
        "span_id": str(s.id),
        "parent_span_id": str(s.parent_span_id) if s.parent_span_id else None,
        "name": s.name,
        "span_type": s.span_type,
        "input": _decode_jsonb(s.input),
        "output": _decode_jsonb(s.output),
        "duration_ms": s.duration_ms,
        "model": s.model,
        "model_parameters": None,       # DB 未持久化
        "tool_name": s.tool_name,
        "status": s.status,
        "level": None,                  # DB 未持久化
        "metadata": None,               # DB 未持久化
        "tags": None,                   # DB 未持久化
        "error_info": _decode_jsonb(s.error_info),
        "time_to_first_token_ms": None, # DB 未持久化
        "started_at": _iso(s.started_at),
        "session_id": session_id,
    }


def _observation_event_line(o: Any, session_id: str | None) -> dict[str, Any]:
    return {
        "event_type": "observation",
        "event_id": str(o.event_id) if o.event_id else None,
        "project_id": str(o.project_id),
        "created_at": _iso(o.created_at),
        "id": str(o.id),
        "trace_id": str(o.trace_id),
        "span_id": str(o.span_id) if o.span_id else None,
        "model": o.model,
        "prompt_tokens": o.prompt_tokens,
        "completion_tokens": o.completion_tokens,
        "total_tokens": o.total_tokens,
        "input": _decode_jsonb(o.input),
        "output": _decode_jsonb(o.output),
        "session_id": session_id,
    }


def _trace_event_line(t: dict[str, Any]) -> dict[str, Any]:
    return {
        "event_type": "trace",
        "event_id": t.get("event_id"),
        "project_id": t.get("project_id"),
        "created_at": _iso(t.get("created_at")),
        "id": t.get("id"),
        "trace_id": t.get("id"),
        "session_id": t.get("session_id"),
        "name": t.get("name"),
        "agent_name": t.get("agent_name"),
        "input": t.get("input"),
        "output": t.get("output"),
        "duration_ms": t.get("duration_ms"),
        "total_tokens": t.get("total_tokens"),
        "prompt_tokens": t.get("prompt_tokens"),
        "completion_tokens": t.get("completion_tokens"),
        "react_step_count": t.get("react_step_count"),
        "tool_count": t.get("tool_count"),
        "span_count": t.get("span_count"),
        "status": t.get("status"),
        "error_info": t.get("error_info"),
        "tags": None,                   # DB 未持久化
        "started_at": _iso(t.get("started_at")),
    }


def _iso(value: Any) -> str | None:
    """datetime → ISO 字符串。

    `started_at` 为无时区 TIMESTAMP（无偏移），`created_at`/`updated_at` 为
    TIMESTAMPTZ（UTC 读回后以 `Z` 结尾）。
    """
    if isinstance(value, datetime):
        return value.isoformat().replace("+00:00", "Z")
    return value


# ---- ORM 对象 → dict ----

def _trace_dict(t: Any) -> dict:
    return {
        "id": str(t.id),
        "event_id": str(t.event_id) if t.event_id else None,
        "project_id": str(t.project_id),
        "name": t.name,
        "agent_name": t.agent_name,
        "input": _decode_jsonb(t.input),
        "output": _decode_jsonb(t.output),
        "duration_ms": t.duration_ms,
        "react_step_count": t.react_step_count,
        "tool_count": t.tool_count,
        "span_count": t.span_count,
        "session_id": t.session_id,
        "status": t.status,
        "error_info": t.error_info,
        "started_at": t.started_at,
        "created_at": t.created_at,
    }


def _span_dict(s: Any) -> dict:
    return {
        "id": str(s.id),
        "event_id": str(s.event_id),
        "trace_id": str(s.trace_id),
        "parent_span_id": str(s.parent_span_id) if s.parent_span_id else None,
        "name": s.name,
        "span_type": s.span_type,
        "input": _decode_jsonb(s.input),
        "output": _decode_jsonb(s.output),
        "duration_ms": s.duration_ms,
        "model": s.model,
        "tool_name": s.tool_name,
        "status": s.status,
        "error_info": s.error_info,
        "started_at": s.started_at,
        "created_at": s.created_at,
    }


def _llm_request_dict(r: Any) -> dict:
    return {
        "event_id": str(r.event_id),
        "id": str(r.id),
        "trace_id": str(r.trace_id),
        "span_id": str(r.span_id) if r.span_id else None,
        "model": r.model,
        "input": _decode_jsonb(r.input),
        "output": _decode_jsonb(r.output),
        "status": r.status,
        "error_info": r.error_info,
        "started_at": r.started_at,
    }


def _obs_dict(o: Any) -> dict:
    return {
        "id": str(o.id),
        "span_id": str(o.span_id) if o.span_id else None,
        "model": o.model,
        "prompt_tokens": o.prompt_tokens,
        "completion_tokens": o.completion_tokens,
        "total_tokens": o.total_tokens,
        "input": _decode_jsonb(o.input),
        "output": _decode_jsonb(o.output),
    }
