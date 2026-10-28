import { useEffect, useState } from "react";
import PageIntro from "../components/PageIntro";
import {
  fetchTraces,
  fmtInt,
  fmtMs,
  fmtTime,
  projectId,
  type TraceRow,
} from "../services/monitor";
import { goTraceDetail } from "../router";

const PAGE_SIZE = 10;

interface Filters {
  sessionId: string;
  traceId: string;
  agentName: string;
  status: string;
}

const emptyFilters: Filters = { sessionId: "", traceId: "", agentName: "", status: "" };

export default function TracesPage() {
  const pid = projectId();
  const [items, setItems] = useState<TraceRow[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [filters, setFilters] = useState<Filters>(emptyFilters);
  const [query, setQuery] = useState<Filters>(emptyFilters);

  const pageCount = Math.max(Math.ceil(total / PAGE_SIZE), 1);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError("");
    fetchTraces(pid, { page, pageSize: PAGE_SIZE, ...query })
      .then((data) => {
        if (cancelled) return;
        setItems(data.items || []);
        setTotal(data.total || 0);
      })
      .catch((exception: Error) => {
        if (cancelled) return;
        setError(`轨迹加载失败：${exception.message}`);
        setItems([]);
        setTotal(0);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [pid, page, query]);

  function search() {
    setQuery({ ...filters });
    setPage(1);
  }

  function reset() {
    setFilters(emptyFilters);
    setQuery({ ...emptyFilters });
    setPage(1);
  }

  function go(target: number) {
    setPage(Math.min(Math.max(target, 1), pageCount));
  }

  return (
    <>
      <PageIntro
        kicker="05 / DEV SESSION TRAIL"
        title="会话轨迹"
        desc="按会话查看 iCode 开发过程中的每一次模型调用、工具执行与耗时，点击可展开调用树与节点详情。"
      >
        <button className="btn" disabled={loading} onClick={search}>{loading ? "加载中…" : "刷新"}</button>
      </PageIntro>

      {error ? <div className="mon-error">{error}</div> : null}

      <section className="panel mon-panel">
        <div className="mon-filters">
          <label>
            <span>Session</span>
            <input
              value={filters.sessionId}
              placeholder="按 session_id 过滤"
              onChange={(event) => setFilters({ ...filters, sessionId: event.target.value })}
              onKeyUp={(event) => event.key === "Enter" && search()}
            />
          </label>
          <label>
            <span>轨迹 ID</span>
            <input
              value={filters.traceId}
              placeholder="按 trace_id 过滤"
              onChange={(event) => setFilters({ ...filters, traceId: event.target.value })}
              onKeyUp={(event) => event.key === "Enter" && search()}
            />
          </label>
          <label>
            <span>Agent</span>
            <input
              value={filters.agentName}
              placeholder="如 icode-local"
              onChange={(event) => setFilters({ ...filters, agentName: event.target.value })}
              onKeyUp={(event) => event.key === "Enter" && search()}
            />
          </label>
          <label>
            <span>状态</span>
            <select
              value={filters.status}
              onChange={(event) => setFilters({ ...filters, status: event.target.value })}
            >
              <option value="">全部</option>
              <option value="success">成功</option>
              <option value="error">失败</option>
            </select>
          </label>
          <div className="mon-filter-actions">
            <button className="btn btn-primary" onClick={search}>查询</button>
            <button className="btn" onClick={reset}>重置</button>
          </div>
        </div>

        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>时间</th><th>轨迹名称</th><th>Session</th><th>Agent</th>
                <th>耗时</th><th>Token</th><th>节点</th><th>状态</th>
              </tr>
            </thead>
            <tbody>
              {items.length === 0 ? (
                <tr>
                  <td colSpan={8} className="mon-table-empty">{loading ? "加载中…" : "没有符合条件的轨迹"}</td>
                </tr>
              ) : null}
              {items.map((row) => (
                <tr key={row.id} className="mon-click-row" onClick={() => goTraceDetail(row.id, row.sessionId)}>
                  <td>{fmtTime(row.startedAt)}</td>
                  <td>
                    <b className="mon-cell-title">{row.name || row.id}</b>
                    <code>{row.id}</code>
                  </td>
                  <td><code>{row.sessionId || "—"}</code></td>
                  <td>{row.agentName || "—"}</td>
                  <td>{fmtMs(row.durationMs)}</td>
                  <td>{fmtInt(row.totalTokens)}</td>
                  <td>{fmtInt(row.spanCount)}</td>
                  <td><span className={`mon-status ${row.status || ""}`}>{row.status}</span></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="mon-pager">
          <span>共 {fmtInt(total)} 条 · 第 {page} / {pageCount} 页</span>
          <div>
            <button className="btn" disabled={page <= 1 || loading} onClick={() => go(page - 1)}>上一页</button>
            <button className="btn" disabled={page >= pageCount || loading} onClick={() => go(page + 1)}>下一页</button>
          </div>
        </div>
      </section>
    </>
  );
}
