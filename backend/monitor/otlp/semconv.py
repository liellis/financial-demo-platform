"""OTel 语义约定：属性名常量 + span_type 推断。

`span_type` 必须收敛到现有表与前端支持的 6 类：
`chain` / `llm` / `tool` / `retriever` / `agent` / `span`。

推断采用「注册表 + 优先级」：每条规则声明 matches / resolve，
按 priority 升序取第一条命中的结果。新增厂商只需插入一条规则，
不改主流程（借自 langfuse 的 ObservationTypeMapperRegistry 设计）。
"""
from __future__ import annotations

from typing import Any, Callable

# ---- 表/前端支持的 span_type ----
SPAN_TYPES = ("chain", "llm", "tool", "retriever", "agent", "span")
DEFAULT_SPAN_TYPE = "span"

# ---- 项目 / 会话 ----
PROJECT_ID_KEYS = ("trace2.project.id", "langfuse.project.id")
# gen_ai.conversation.id 是 OTel GenAI 语义约定里的会话标识
# （iCode / OpenLLMetry 等原生埋点用它，不带 langfuse.* 前缀）。
SESSION_ID_KEYS = ("session.id", "langfuse.session.id", "gen_ai.conversation.id")

# ---- langfuse 原生属性 ----
LF_TRACE_NAME = "langfuse.trace.name"
LF_TRACE_INPUT = "langfuse.trace.input"
LF_TRACE_OUTPUT = "langfuse.trace.output"
LF_TRACE_TAGS = "langfuse.trace.tags"
LF_TRACE_PUBLIC = "langfuse.trace.public"
LF_TRACE_METADATA = "langfuse.trace.metadata"
LF_OBS_TYPE = "langfuse.observation.type"
LF_OBS_INPUT = "langfuse.observation.input"
LF_OBS_OUTPUT = "langfuse.observation.output"
LF_OBS_USAGE = "langfuse.observation.usage_details"
LF_OBS_LEVEL = "langfuse.observation.level"
LF_AS_ROOT = "langfuse.internal.as_root"

# 出现任一即认为该 span 携带 trace 级更新（需要产出 trace 事件）
TRACE_UPDATE_KEYS = (
    LF_TRACE_NAME,
    LF_TRACE_INPUT,
    LF_TRACE_OUTPUT,
    LF_TRACE_TAGS,
    LF_TRACE_PUBLIC,
    LF_TRACE_METADATA,
    "user.id",
    "langfuse.user.id",
    "session.id",
    "langfuse.session.id",
)

# ---- model ----
MODEL_KEYS = (
    "gen_ai.request.model",
    "gen_ai.response.model",
    "llm.model_name",
    "ai.model.id",
    "model",
)

# ---- tool ----
TOOL_NAME_KEYS = ("gen_ai.tool.name", "tool.name", "ai.toolCall.name")

# ---- usage ----
USAGE_INPUT_KEYS = (
    "gen_ai.usage.input_tokens",
    "gen_ai.usage.prompt_tokens",
    "llm.token_count.prompt",
    "ai.usage.promptTokens",
)
USAGE_OUTPUT_KEYS = (
    "gen_ai.usage.output_tokens",
    "gen_ai.usage.completion_tokens",
    "llm.token_count.completion",
    "ai.usage.completionTokens",
)
USAGE_TOTAL_KEYS = (
    "gen_ai.usage.total_tokens",
    "llm.token_count.total",
    "ai.usage.tokens",
)

# ---- input / output（按优先级排列，见方案 §6.6）----
INPUT_OUTPUT_KEYS = (
    # OTel GenAI 新版
    ("gen_ai.input.messages", "gen_ai.output.messages"),
    # OTel GenAI 工具调用（iCode / Microsoft Agent Framework 等工具 span 用这两个，
    # 不带 gen_ai.input.messages，缺了它工具 span 的 input/output 就全是 null）
    ("gen_ai.tool.call.arguments", "gen_ai.tool.call.result"),
    # langfuse 原生
    (LF_OBS_INPUT, LF_OBS_OUTPUT),
    (LF_TRACE_INPUT, LF_TRACE_OUTPUT),
    # OpenInference / SmolAgents
    ("input.value", "output.value"),
    ("input", "output"),
    # Vercel AI SDK
    ("ai.prompt.messages", "ai.response.text"),
    ("ai.prompt", "ai.result.text"),
    ("ai.toolCall.args", "ai.toolCall.result"),
    # Traceloop
    ("traceloop.entity.input", "traceloop.entity.output"),
    # MLFlow
    ("mlflow.spanInputs", "mlflow.spanOutputs"),
    # Google Vertex
    ("gcp.vertex.agent.llm_request", "gcp.vertex.agent.llm_response"),
    # Genkit
    ("genkit:input", "genkit:output"),
)

# 已被 input/output 消费的属性前缀（用于从「残留属性」里剔除，避免 metadata 重复）
INPUT_OUTPUT_PREFIXES = (
    "gen_ai.prompt",
    "gen_ai.completion",
    "gen_ai.input.messages",
    "gen_ai.output.messages",
    "gen_ai.system_instructions",
    "gen_ai.tool.call.arguments",
    "gen_ai.tool.call.result",
    "llm.input_messages",
    "llm.output_messages",
    LF_OBS_INPUT,
    LF_OBS_OUTPUT,
    LF_TRACE_INPUT,
    LF_TRACE_OUTPUT,
    LF_TRACE_METADATA,
    "langfuse.observation.metadata",
    "ai.telemetry.metadata",
)

_LANGFUSE_TYPE_MAP = {
    "span": "span",
    "generation": "llm",
    "event": "span",
    "embedding": "span",
    "agent": "agent",
    "tool": "tool",
    "chain": "chain",
    "retriever": "retriever",
    "guardrail": "span",
    "evaluator": "span",
}

_OPENINFERENCE_MAP = {
    "LLM": "llm",
    "CHAIN": "chain",
    "RETRIEVER": "retriever",
    "AGENT": "agent",
    "TOOL": "tool",
    "EMBEDDING": "span",
    "GUARDRAIL": "span",
    "EVALUATOR": "span",
}

_GENAI_OP_MAP = {
    "chat": "llm",
    "completion": "llm",
    "text_completion": "llm",
    "generate_content": "llm",
    "generate": "llm",
    "embeddings": "span",
    "invoke_agent": "agent",
    "create_agent": "agent",
    "execute_tool": "tool",
}


def _meaningful(value: Any) -> bool:
    """值非 None / 空串 / 空数组 / 空对象。"""
    if value is None or value == "":
        return False
    if isinstance(value, (list, tuple, dict)):
        return len(value) > 0
    return True


def _map_value(attrs: dict[str, Any], key: str, table: dict[str, str]) -> str | None:
    raw = attrs.get(key)
    if not _meaningful(raw):
        return None
    key_str = raw if isinstance(raw, str) else str(raw)
    mapped = table.get(key_str)
    if mapped:
        return mapped
    # 大小写不敏感兜底（部分 SDK 用大写）
    mapped = table.get(key_str.upper()) or table.get(key_str.lower())
    return mapped


def _langfuse_type(attrs: dict[str, Any], **_: Any) -> str | None:
    return _map_value(attrs, LF_OBS_TYPE, _LANGFUSE_TYPE_MAP)


def _openinference(attrs: dict[str, Any], **_: Any) -> str | None:
    return _map_value(attrs, "openinference.span.kind", _OPENINFERENCE_MAP)


def _genai_operation(attrs: dict[str, Any], **_: Any) -> str | None:
    return _map_value(attrs, "gen_ai.operation.name", _GENAI_OP_MAP)


def _genai_tool_call(attrs: dict[str, Any], **_: Any) -> str | None:
    if _meaningful(attrs.get("gen_ai.tool.name")) or _meaningful(
        attrs.get("gen_ai.tool.call.id")
    ):
        return "tool"
    return None


def _vercel_tool_call(attrs: dict[str, Any], **_: Any) -> str | None:
    for key in ("operation.name", "ai.operationId"):
        value = attrs.get(key)
        if isinstance(value, str) and value.startswith("ai.toolCall"):
            return "tool"
    return None


def _model_based(attrs: dict[str, Any], **_: Any) -> str | None:
    for key in MODEL_KEYS:
        if _meaningful(attrs.get(key)):
            return "llm"
    return None


_RULES: list[tuple[int, Callable[..., str | None]]] = [
    (1, _langfuse_type),
    (2, _openinference),
    (3, _genai_operation),
    (4, _genai_tool_call),
    (5, _vercel_tool_call),
    (6, _model_based),
]


def infer_span_type(
    attrs: dict[str, Any],
    resource_attrs: dict[str, Any] | None = None,
    scope_name: str | None = None,
    span_name: str | None = None,
) -> str:
    """按优先级推断 span_type，未命中回落到 `span`。"""
    ctx = {
        "resource_attrs": resource_attrs or {},
        "scope_name": scope_name,
        "span_name": span_name,
    }
    for _, rule in sorted(_RULES, key=lambda r: r[0]):
        result = rule(attrs, **ctx)
        if result in SPAN_TYPES:
            return result
    return DEFAULT_SPAN_TYPE


def has_trace_updates(attrs: dict[str, Any]) -> bool:
    """该 span 是否携带 trace 级属性（需要产出 trace 事件）。"""
    return any(_meaningful(attrs.get(k)) for k in TRACE_UPDATE_KEYS)
