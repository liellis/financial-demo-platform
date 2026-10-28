"""指标看板 API：summary / timeseries / breakdown / top-traces / trajectory。

挂在 `/api/v1` 前缀下，与 `/projects/{project_id}/traces` 同级：
`/api/v1/projects/{project_id}/metrics/*`。

查询全部走 PG（metric_point / traces / trajectory_fact），与 STORAGE_BACKEND
是否 file 无关——只要 metric sink 指向 database，指标就能读。
"""
from __future__ import annotations

from datetime import datetime, timedelta, timezone
from typing import AsyncIterator
from zoneinfo import ZoneInfo

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.ext.asyncio import AsyncSession

from ...config import OTLP_TIMEZONE
from ...db.connection import get_session_factory
from ...db.repositories.metrics_repository import MetricsRepository

router = APIRouter()

try:  # Windows 缺 tzdata 时回落到 UTC
    _TZ = ZoneInfo(OTLP_TIMEZONE)
except Exception:  # pragma: no cover
    _TZ = timezone.utc


async def get_metrics_session() -> AsyncIterator[AsyncSession]:
    """指标查询专用 session：engine 未初始化（未配库）时返回 503。"""
    try:
        factory = get_session_factory()
    except RuntimeError as exc:
        raise HTTPException(
            status_code=503, detail="metrics storage not ready"
        ) from exc
    async with factory() as session:
        yield session


def _parse_dt(value: str | None, default: datetime) -> datetime:
    if not value:
        return default
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as exc:
        raise HTTPException(
            status_code=400, detail=f"时间格式无效: {value!r}"
        ) from exc
    # 裸墙钟按采集时区解释（与 OTLP 入库口径一致）
    return parsed if parsed.tzinfo else parsed.replace(tzinfo=_TZ)


def _range(
    from_: str | None, to: str | None
) -> tuple[datetime, datetime]:
    end = _parse_dt(to, datetime.now(timezone.utc))
    start = _parse_dt(from_, end - timedelta(hours=24))
    if start >= end:
        raise HTTPException(status_code=400, detail="from 必须早于 to")
    return start, end


def _project(project_id: str) -> str:
    pid = (project_id or "").strip()
    if not pid:
        raise HTTPException(status_code=400, detail="project_id 不能为空")
    return pid


@router.get("/projects/{project_id}/metrics/summary")
async def metrics_summary(
    project_id: str,
    from_: str | None = Query(None, alias="from"),
    to: str | None = Query(None),
    session: AsyncSession = Depends(get_metrics_session),
):
    start, end = _range(from_, to)
    return await MetricsRepository(session).summary(_project(project_id), start, end)


@router.get("/projects/{project_id}/metrics/timeseries")
async def metrics_timeseries(
    project_id: str,
    metric: str = Query("llm_duration"),
    interval: str = Query("5m"),
    group_by: str | None = Query(None),
    from_: str | None = Query(None, alias="from"),
    to: str | None = Query(None),
    session: AsyncSession = Depends(get_metrics_session),
):
    start, end = _range(from_, to)
    try:
        return await MetricsRepository(session).timeseries(
            _project(project_id), start, end, metric, interval, group_by
        )
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@router.get("/projects/{project_id}/metrics/breakdown")
async def metrics_breakdown(
    project_id: str,
    metric: str = Query("tokens"),
    dimension: str = Query("request_model"),
    limit: int = Query(10, ge=1, le=50),
    from_: str | None = Query(None, alias="from"),
    to: str | None = Query(None),
    session: AsyncSession = Depends(get_metrics_session),
):
    start, end = _range(from_, to)
    try:
        return await MetricsRepository(session).breakdown(
            _project(project_id), start, end, metric, dimension, limit
        )
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@router.get("/projects/{project_id}/metrics/top-traces")
async def metrics_top_traces(
    project_id: str,
    order_by: str = Query("duration"),
    limit: int = Query(10, ge=1, le=100),
    from_: str | None = Query(None, alias="from"),
    to: str | None = Query(None),
    session: AsyncSession = Depends(get_metrics_session),
):
    start, end = _range(from_, to)
    return await MetricsRepository(session).top_traces(
        _project(project_id), start, end, order_by, limit
    )


@router.get("/projects/{project_id}/metrics/trajectory-sessions")
async def metrics_trajectory_sessions(
    project_id: str,
    limit: int = Query(50, ge=1, le=200),
    session: AsyncSession = Depends(get_metrics_session),
):
    """已导入轨迹分析的会话列表（选中后再查单会话明细）。"""
    return await MetricsRepository(session).trajectory_sessions(
        _project(project_id), limit
    )


@router.get("/projects/{project_id}/metrics/trajectory")
async def metrics_trajectory(
    project_id: str,
    session_id: str = Query(..., alias="session_id"),
    session: AsyncSession = Depends(get_metrics_session),
):
    data = await MetricsRepository(session).trajectory(
        _project(project_id), session_id
    )
    if data is None:
        raise HTTPException(
            status_code=404,
            detail="trajectory not found（该会话尚未导入轨迹分析结果）",
        )
    return data
