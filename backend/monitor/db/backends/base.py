"""存储后端抽象：统一"读取 trace 数据"接口。

database / file 后端都实现这些方法，路由层只依赖本抽象（Repository Pattern）。
"""
from __future__ import annotations

from abc import ABC, abstractmethod
from typing import Any


class TraceRow(dict):
    """一行 trace 数据（普通 dict，便于序列化）。"""


class TraceData:
    """一次 trace 的完整数据（含 spans / observations）。"""

    def __init__(
        self,
        trace: dict[str, Any],
        spans: list[dict[str, Any]],
        observations: list[dict[str, Any]],
    ):
        self.trace = trace
        self.spans = spans
        self.observations = observations


class SpanData:
    """单个 span 的详情（含其 observations）。"""

    def __init__(
        self,
        span: dict[str, Any],
        observations: list[dict[str, Any]],
    ):
        self.span = span
        self.observations = observations


class StorageBackend(ABC):
    """存储后端统一接口（当前只读场景：查询展示）。

    项目管理（projects CRUD / agents）已废弃：项目 ID 由集成方/上报方自带。
    """

    @abstractmethod
    async def list_traces(
        self,
        project_id: str,
        page: int = 1,
        page_size: int = 20,
        agent_name: str | None = None,
        status: str | None = None,
        session_id: str | None = None,
        trace_id: str | None = None,
    ) -> tuple[list[dict[str, Any]], int]: ...

    @abstractmethod
    async def get_trace_detail(
        self,
        project_id: str,
        trace_id: str,
        session_id: str | None = None,
    ) -> TraceData | None: ...

    @abstractmethod
    async def get_llm_request(
        self, project_id: str, event_id: str
    ) -> dict[str, Any] | None: ...

    async def get_span_detail(
        self,
        project_id: str,
        trace_id: str,
        span_id: str,
        session_id: str | None = None,
    ) -> SpanData | None:
        """按 span_id 读单条 span 详情（含其 observations）。

        刻意**不标** `abstractmethod`：file 后端本期不实现懒加载（决策 2），
        若标 abstract，deps.py 里模块级 `FileBackend()` 会在 import 时抛
        TypeError，file 模式直接起不来。默认返回 None → 路由层 404，零模式分支。
        """
        return None

    async def export_trace_jsonl(
        self,
        project_id: str,
        trace_id: str,
        session_id: str | None = None,
    ) -> str | None:
        """按 trace_id 还原「文件模式」的 `<trace_id>.jsonl` 全文（每行一条事件 JSON）。

        database 模式从库表重拼行；file 模式直接回吐磁盘上的
        `<trace_id>.jsonl` 原文。`session_id` 可选，file 模式据此按
        `<pid>/<sid>/<trace_id>.jsonl` 直拼路径 O(1) 定位（未传/未命中再全盘兜底）。

        刻意**不标** `abstractmethod`（同 get_span_detail 的理由），默认返回
        None → 路由层 404。返回 None 表示 trace 不存在。
        """
        return None
