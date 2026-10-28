// 监控指标 / 开发会话轨迹的数据访问层。
// 数据来自本项目后端的 trace 监控子包（PostgreSQL trace_db，OTLP 采集）。
const BASE = "/api/v1";

export type Row = Record<string, any>;

export interface Range {
  from: string;
  to: string;
}

export interface TraceRow {
  id: string;
  name?: string;
  sessionId?: string;
  agentName?: string;
  durationMs?: number;
  totalTokens?: number;
  promptTokens?: number;
  completionTokens?: number;
  spanCount?: number;
  status?: string;
  startedAt?: string;
}

export interface SpanRow {
  id: string;
  name: string;
  spanType: string;
  status: string;
  durationMs?: number;
  startedAt?: string;
  model?: string;
  toolName?: string;
  input?: unknown;
  output?: unknown;
  errorInfo?: { errorType?: string; message?: string } | null;
}

export interface TreeNode {
  span: SpanRow;
  children?: TreeNode[];
}

export interface TrendPoint {
  ts: string;
  value: number | string;
  series?: string;
}

export interface BarItem {
  name?: string;
  value: number;
}

/** 当前演示固定项目；如需切换可用 ?project=xxx */
export function projectId(): string {
  const fromQuery = new URLSearchParams(window.location.search).get("project");
  return fromQuery || "icode";
}

async function get<T = Row>(path: string, params: object = {}): Promise<T> {
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== "") query.set(key, String(value));
  }
  const url = `${BASE}${path}${query.toString() ? `?${query}` : ""}`;
  const response = await fetch(url, { headers: { Accept: "application/json" } });
  if (!response.ok) {
    let detail = `${response.status}`;
    try {
      const body = await response.json();
      detail = body.detail || body.message_key || detail;
    } catch {
      // 响应体不是 JSON 时保留状态码
    }
    throw new Error(detail);
  }
  return response.json();
}

export function fetchMonitorConfig() {
  return get("/config");
}

/** KPI：会话数 / token / LLM 平均耗时 / 工具失败率 */
export function fetchSummary(pid: string, range: Range) {
  return get(`/projects/${pid}/metrics/summary`, { ...range });
}

export function fetchTimeseries(pid: string, params: Range & { metric: string; interval: string; group_by?: string }) {
  return get<{ points: TrendPoint[] }>(`/projects/${pid}/metrics/timeseries`, params);
}

export function fetchBreakdown(pid: string, params: Range & { metric: string; dimension: string }) {
  return get<{ items: BarItem[] }>(`/projects/${pid}/metrics/breakdown`, params);
}

export function fetchTopTraces(pid: string, params: Range & { limit?: number }) {
  return get(`/projects/${pid}/metrics/top-traces`, params);
}

/** 已导入轨迹分析的会话列表 */
export function fetchTrajectorySessions(pid: string, limit = 50) {
  return get<{ items: Row[] }>(`/projects/${pid}/metrics/trajectory-sessions`, { limit });
}

/** 整轮 / 整会话效率；未导入时后端返回 404 */
export function fetchTrajectory(pid: string, sessionId: string) {
  return get(`/projects/${pid}/metrics/trajectory`, { session_id: sessionId });
}

export function fetchTraces(pid: string, query: {
  page: number;
  pageSize: number;
  agentName?: string;
  status?: string;
  sessionId?: string;
  traceId?: string;
}) {
  return get<{ items: TraceRow[]; total: number }>(`/projects/${pid}/traces`, {
    page: query.page,
    page_size: query.pageSize,
    agent_name: query.agentName,
    status: query.status,
    session_id: query.sessionId,
    trace_id: query.traceId,
  });
}

export function fetchTraceTree(pid: string, traceId: string, sessionId: string) {
  return get<{ trace: TraceRow; tree: TreeNode[] }>(`/projects/${pid}/traces/${traceId}/tree`, { session_id: sessionId });
}

export function fetchTraceDetail(pid: string, traceId: string, sessionId: string) {
  return get<{ trace: TraceRow }>(`/projects/${pid}/traces/${traceId}`, { session_id: sessionId });
}

export function fetchSpanDetail(pid: string, traceId: string, spanId: string, sessionId: string) {
  return get<{ span: SpanRow; observations: Row[] }>(`/projects/${pid}/traces/${traceId}/spans/${spanId}`, { session_id: sessionId });
}

// ---- 展示格式化 ----

export function fmtInt(value: unknown): string {
  return Number(value ?? 0).toLocaleString("zh-CN");
}

export function fmtCompact(value: unknown): string {
  const n = Number(value ?? 0);
  if (Math.abs(n) >= 1e8) return `${(n / 1e8).toFixed(2)} 亿`;
  if (Math.abs(n) >= 1e4) return `${(n / 1e4).toFixed(1)} 万`;
  return fmtInt(Math.round(n));
}

export function fmtPercent(value: unknown): string {
  return `${((Number(value) || 0) * 100).toFixed(1)}%`;
}

export function fmtMs(value: unknown): string {
  const n = Number(value ?? 0);
  if (!n) return "—";
  if (n >= 1000) return `${(n / 1000).toFixed(2)} s`;
  return `${Math.round(n)} ms`;
}

export function fmtNs(value: unknown): string {
  const n = Number(value ?? 0);
  if (!n) return "—";
  const seconds = n / 1e9;
  if (seconds >= 60) return `${Math.floor(seconds / 60)} 分 ${Math.round(seconds % 60)} 秒`;
  return `${seconds.toFixed(seconds >= 10 ? 1 : 2)} 秒`;
}

export function fmtTime(value: unknown): string {
  if (!value) return "—";
  const date = new Date(String(value));
  if (Number.isNaN(date.getTime())) return String(value);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

export function fmtDuration(value: unknown): string {
  if (value === null || value === undefined || value === "") return "—";
  return `${Number(value).toFixed(1)} ms`;
}
