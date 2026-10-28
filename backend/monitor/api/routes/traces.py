"""Trace 接口：列表 / 详情 / 调用树 / 单 span 详情（经存储后端访问数据）。"""
from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException
from fastapi.responses import Response

from ..deps import get_storage_backend
from ..helpers import (
    build_tree,
    llm_request_out,
    observation_out,
    span_brief_out,
    span_out,
    trace_out,
)
from ..schemas import (
    LlmRequestOut,
    SpanDetailOut,
    TraceDetailOut,
    TraceListOut,
    TraceTreeOut,
)
from ...config import STORAGE_BACKEND
from ...db.backends.base import StorageBackend

router = APIRouter()


@router.get("/projects/{project_id}/traces", response_model=TraceListOut)
async def list_traces(
    project_id: str,
    page: int = 1,
    page_size: int = 20,
    agent_name: str | None = None,
    status: str | None = None,
    session_id: str | None = None,
    trace_id: str | None = None,
    backend: StorageBackend = Depends(get_storage_backend),
):
    page = max(page, 1)
    page_size = min(max(page_size, 1), 100)
    rows, total = await backend.list_traces(
        project_id,
        page=page,
        page_size=page_size,
        agent_name=agent_name,
        status=status,
        session_id=session_id,
        trace_id=trace_id,
    )
    return TraceListOut(
        items=[trace_out(t) for t in rows],
        total=total,
        page=page,
        page_size=page_size,
    )


@router.get("/projects/{project_id}/traces/{trace_id}", response_model=TraceDetailOut)
async def get_trace_detail(
    project_id: str,
    trace_id: str,
    session_id: str | None = None,
    backend: StorageBackend = Depends(get_storage_backend),
):
    # 详情按 trace_id 直查；file 后端带 project_id/session_id 时按路径
    # O(1) 定位文件（不再全盘扫描），未命中回退全盘兜底。
    data = await backend.get_trace_detail(project_id, trace_id, session_id=session_id)
    if data is None:
        raise HTTPException(status_code=404, detail="trace not found")
    return TraceDetailOut(
        trace=trace_out(data.trace),
        spans=[span_out(s) for s in data.spans],
        observations=[observation_out(o) for o in data.observations],
    )


@router.get("/projects/{project_id}/traces/{trace_id}/tree", response_model=TraceTreeOut)
async def get_trace_tree(
    project_id: str,
    trace_id: str,
    session_id: str | None = None,
    backend: StorageBackend = Depends(get_storage_backend),
):
    # 调用树与详情同一定位逻辑（带 session_id 走路径直查，未命中回退兜底）
    data = await backend.get_trace_detail(project_id, trace_id, session_id=session_id)
    if data is None:
        raise HTTPException(status_code=404, detail="trace not found")
    # DB 模式：/tree 返回 brief（前端注入 fetchSpanDetail 按需补全 input/output）
    # file 模式：/tree 返回完整 SpanOut（前端不懒加载，靠这里的 input/output 渲染）
    if STORAGE_BACKEND == "database":
        span_list: list = [span_brief_out(s) for s in data.spans]
    else:
        span_list = [span_out(s) for s in data.spans]
    return TraceTreeOut(trace=trace_out(data.trace), tree=build_tree(span_list))


@router.get(
    "/projects/{project_id}/traces/{trace_id}/spans/{span_id}",
    response_model=SpanDetailOut,
)
async def get_span_detail(
    project_id: str,
    trace_id: str,
    span_id: str,
    session_id: str | None = None,
    backend: StorageBackend = Depends(get_storage_backend),
):
    # 单 span 详情的懒加载入口：DB 模式走 PK + 索引；file 模式后端默认返回
    # None（决策 2 不实现），此处天然 404，路由层无需模式分支。
    if not project_id or project_id.strip() == "":
        raise HTTPException(
            status_code=400,
            detail="project_id 不能为空，必须指定具体项目 id",
        )
    data = await backend.get_span_detail(
        project_id, trace_id, span_id, session_id=session_id
    )
    if data is None:
        raise HTTPException(status_code=404, detail="span not found")
    return SpanDetailOut(
        span=span_out(data.span),
        observations=[observation_out(o) for o in data.observations],
    )


@router.get(
    "/projects/{project_id}/llm_requests/{event_id}",
    response_model=LlmRequestOut,
)
async def get_llm_request(
    project_id: str,
    event_id: str,
    backend: StorageBackend = Depends(get_storage_backend),
):
    # 真实 LLM 请求按 project_id + event_id 定位：file 模式据此把扫描范围缩到
    # <data_dir>/<project_id> 下，避免跨项目全盘遍历（sess-bench 残留会拖慢）。
    # project_id 必须是具体项目 id，拒绝空值。
    if not project_id or project_id.strip() == "":
        raise HTTPException(
            status_code=400,
            detail="project_id 不能为空，必须指定具体项目 id",
        )
    item = await backend.get_llm_request(project_id, event_id)
    if item is None:
        raise HTTPException(status_code=404, detail="llm request not found")
    return llm_request_out(item)


@router.get("/projects/{project_id}/traces/{trace_id}/export")
async def export_trace_jsonl(
    project_id: str,
    trace_id: str,
    session_id: str | None = None,
    backend: StorageBackend = Depends(get_storage_backend),
):
    # 下载入口：DB 模式从库表重拼 `<trace_id>.jsonl` 全文；file 模式直接把
    # 磁盘上的原文回吐。带 session_id 时 file 后端按 <pid>/<sid>/<trace_id>.jsonl
    # 直拼路径 O(1) 定位，未命中再全盘兜底。
    if not project_id or project_id.strip() == "":
        raise HTTPException(
            status_code=400,
            detail="project_id 不能为空，必须指定具体项目 id",
        )
    content = await backend.export_trace_jsonl(
        project_id, trace_id, session_id=session_id
    )
    if content is None:
        raise HTTPException(status_code=404, detail="trace not found")
    return Response(
        content=content,
        media_type="application/x-ndjson",
        headers={
            "Content-Disposition": f'attachment; filename="{trace_id}.jsonl"'
        },
    )
