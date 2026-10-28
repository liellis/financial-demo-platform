"""Trace/Span/Observation 仓储：封装列表、详情、调用树查询。

项目管理（projects CRUD / agents）已废弃：项目 ID 由集成方/上报方自带。
"""
from __future__ import annotations

from typing import Any

from sqlalchemy import String, and_, cast, func, or_, select
from sqlalchemy.ext.asyncio import AsyncSession

from ..models import LlmRequest, Observation, Session, Span, Trace


class TraceRepository:
    def __init__(self, session: AsyncSession):
        self._session = session

    async def list_traces(
        self,
        project_id: str,
        page: int = 1,
        page_size: int = 20,
        agent_name: str | None = None,
        status: str | None = None,
        session_id: str | None = None,
        trace_id: str | None = None,
    ) -> tuple[list[Trace], int]:
        """分页查询 traces，返回 (行列表, 总数)。

        project_id 支持逗号分隔的多个项目 ID。
        """
        conds: list = []
        if project_id:
            conds.append(Trace.project_id.in_(project_id.split(",")))
        if agent_name:
            conds.append(Trace.agent_name == agent_name)
        if status:
            conds.append(Trace.status == status)
        # session_id / trace_id 支持子串模糊匹配（同一输入框可查任意 ID）。
        # 二者为 OR 关系：输入一个关键词，命中 trace_id 或 session_id 任一即返回。
        # UUID 列需 cast 为文本后 ILIKE（PostgreSQL 不支持 uuid ~~* uuid）
        if session_id or trace_id:
            keyword = session_id or trace_id
            conds.append(
                or_(
                    cast(Trace.session_id, String).ilike(f"%{keyword}%"),
                    cast(Trace.id, String).ilike(f"%{keyword}%"),
                )
            )
        cond = and_(*conds)

        total = await self._session.scalar(select(func.count()).select_from(Trace).where(cond)) or 0
        stmt = (
            select(Trace)
            .where(cond)
            .order_by(Trace.created_at.desc())
            .offset((page - 1) * page_size)
            .limit(page_size)
        )
        rows = (await self._session.scalars(stmt)).all()
        return list(rows), total

    async def get_trace(self, project_id: str, trace_id: str) -> Trace | None:
        """按 id 查 trace；project_id 为空时不按项目过滤。"""
        stmt = select(Trace).where(Trace.id == trace_id)
        if project_id:
            stmt = stmt.where(Trace.project_id.in_(project_id.split(",")))
        return await self._session.scalar(stmt)

    async def get_spans(self, trace_id: str) -> list[Span]:
        stmt = select(Span).where(Span.trace_id == trace_id).order_by(Span.created_at)
        return list((await self._session.scalars(stmt)).all())

    async def get_observations(self, trace_id: str) -> list[Observation]:
        stmt = (
            select(Observation)
            .where(Observation.trace_id == trace_id)
            .order_by(Observation.created_at)
        )
        return list((await self._session.scalars(stmt)).all())

    async def get_span(self, span_id: str) -> Span | None:
        """按 spans.id 主键读单条（PK 命中，数行级开销）。"""
        return await self._session.scalar(select(Span).where(Span.id == span_id))

    async def get_observations_by_span(self, span_id: str) -> list[Observation]:
        """走 idx_observations_span(span_id) 索引。"""
        stmt = (
            select(Observation)
            .where(Observation.span_id == span_id)
            .order_by(Observation.created_at)
        )
        return list((await self._session.scalars(stmt)).all())

    async def get_token_totals(
        self, trace_ids: list[str]
    ) -> dict[str, dict[str, int]]:
        """按 trace_id 实时聚合 observations 的 token 用量。

        返回 {trace_id: {"prompt_tokens": n, "completion_tokens": n,
        "total_tokens": n}}。跨进程/子 agent 上报的 observation 都会计入，
        避免 SDK 单进程 total_tokens 汇总漏计（见多数据源方案）。
        total_tokens 取列原值之和（模型可能把缓存/系统开销计入 total 而不计入
        prompt/completion，故 total != prompt + completion 属正常）。
        """
        if not trace_ids:
            return {}
        rows = await self._session.execute(
            select(
                Observation.trace_id,
                func.coalesce(func.sum(Observation.prompt_tokens), 0),
                func.coalesce(func.sum(Observation.completion_tokens), 0),
                func.coalesce(func.sum(Observation.total_tokens), 0),
            )
            .where(Observation.trace_id.in_(trace_ids))
            .group_by(Observation.trace_id)
        )
        return {
            str(trace_id): {
                "prompt_tokens": int(prompt or 0),
                "completion_tokens": int(completion or 0),
                "total_tokens": int(total or 0),
            }
            for trace_id, prompt, completion, total in rows.all()
        }

    async def get_llm_request(self, project_id: str, event_id: str) -> LlmRequest | None:
        """按 project_id + event_id 主键读单条真实 LLM 请求。

        llm_requests 表直接冗余存储 project_id（非 join 派生），SQL 层加
        WHERE project_id = ? 过滤，与 file 模式按项目目录缩小扫描范围对齐。
        """
        row = await self._session.execute(
            select(LlmRequest).where(
                and_(LlmRequest.event_id == event_id,
                     LlmRequest.project_id == project_id)
            )
        )
        return row.scalar_one_or_none()

    async def get_session(self, session_id: str) -> Session | None:
        """按 sessions.id 主键读单条 session（导出时重拼 session 事件行用）。"""
        return await self._session.scalar(select(Session).where(Session.id == session_id))
