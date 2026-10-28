"""Pydantic 响应模型：把数据库 snake_case 字段转成前端 camelCase。"""
from __future__ import annotations

from typing import Any

from pydantic import BaseModel, ConfigDict, Field


class ConfigOut(BaseModel):
    storageBackend: str


class ErrorInfoOut(BaseModel):
    errorType: str | None = None
    errorCode: str | None = None
    errorCategory: str | None = None
    message: str | None = None
    stackTrace: str | None = None


class TraceOut(BaseModel):
    id: str
    eventId: str | None = None
    projectId: str
    sessionId: str | None = None
    name: str | None = None
    agentName: str | None = None
    input: Any | None = None
    output: Any | None = None
    durationMs: int | None = None
    totalTokens: int | None = None
    promptTokens: int | None = None
    completionTokens: int | None = None
    reactStepCount: int | None = None
    toolCount: int | None = None
    spanCount: int | None = None
    status: str
    errorInfo: ErrorInfoOut | None = None
    startedAt: str | None = None


class SpanOut(BaseModel):
    id: str
    eventId: str | None = None
    traceId: str
    parentSpanId: str | None = None
    name: str
    spanType: str
    input: Any | None = None
    output: Any | None = None
    durationMs: int | None = None
    model: str | None = None
    toolName: str | None = None
    status: str
    errorInfo: ErrorInfoOut | None = None
    startedAt: str | None = None


class SpanBriefOut(BaseModel):
    """树的轻量节点：不含 input/output/errorInfo（点击后按需单查）。

    eventId / parentSpanId 必须保留：前者驱动 Real LLM Request 懒加载，
    后者供 build_tree 组树。
    """

    id: str
    eventId: str | None = None
    traceId: str
    parentSpanId: str | None = None
    name: str
    spanType: str
    durationMs: int | None = None
    model: str | None = None
    toolName: str | None = None
    status: str
    startedAt: str | None = None


class LlmRequestOut(BaseModel):
    eventId: str
    spanId: str | None = None
    model: str | None = None
    input: Any | None = None
    output: Any | None = None
    status: str | None = None
    errorInfo: ErrorInfoOut | None = None
    startedAt: str | None = None


class ObservationOut(BaseModel):
    """observation 的精简视图：input/output 无任何前端消费者，故不下发。"""

    id: str
    spanId: str | None = None
    model: str | None = None
    promptTokens: int | None = None
    completionTokens: int | None = None
    totalTokens: int | None = None


class SpanDetailOut(BaseModel):
    """点击节点后按需返回的完整详情：span 全字段 + 其 observations。"""

    span: SpanOut
    observations: list[ObservationOut] = Field(default_factory=list)


class TraceNodeOut(BaseModel):
    # 联合类型（不是 SpanBriefOut）：DB 模式的 /tree 传 SpanBriefOut，file 模式
    # 仍传完整 SpanOut。Pydantic v2 不会把父类实例当子类接受，若这里只声明
    # SpanBriefOut，file 模式 /tree 会直接 ValidationError（500）。
    span: SpanBriefOut | SpanOut | None = None
    children: list[TraceNodeOut] = Field(default_factory=list)


class TraceListOut(BaseModel):
    items: list[TraceOut]
    total: int
    page: int
    page_size: int


class TraceDetailOut(BaseModel):
    trace: TraceOut
    spans: list[SpanOut]
    observations: list[ObservationOut]


class TraceTreeOut(BaseModel):
    trace: TraceOut
    tree: list[TraceNodeOut]
