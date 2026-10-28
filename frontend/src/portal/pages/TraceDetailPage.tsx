import { useCallback, useEffect, useState } from "react";
import AppIcon from "../components/AppIcon";
import JsonView from "../components/monitor/JsonView";
import TraceTree from "../components/monitor/TraceTree";
import {
  fetchSpanDetail,
  fetchTraceDetail,
  fetchTraceTree,
  fmtInt,
  fmtMs,
  fmtTime,
  projectId,
  type SpanRow,
  type TraceRow,
  type TreeNode,
} from "../services/monitor";
import { goPortal } from "../router";

export default function TraceDetailPage({
  traceId,
  sessionId,
}: {
  traceId: string;
  sessionId: string;
}) {
  const pid = projectId();
  const [trace, setTrace] = useState<TraceRow | null>(null);
  const [tree, setTree] = useState<TreeNode[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [selectedId, setSelectedId] = useState("");
  const [spanDetail, setSpanDetail] = useState<SpanRow | null>(null);

  const selectSpan = useCallback(
    async (span: SpanRow) => {
      setSelectedId(span.id);
      try {
        const detail = await fetchSpanDetail(pid, traceId, span.id, sessionId);
        setSpanDetail(detail.span);
      } catch {
        setSpanDetail(span);
      }
    },
    [pid, traceId, sessionId],
  );

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError("");
    Promise.all([
      fetchTraceTree(pid, traceId, sessionId),
      fetchTraceDetail(pid, traceId, sessionId),
    ])
      .then(async ([treeData, detail]) => {
        if (cancelled) return;
        setTrace(treeData.trace || detail.trace);
        setTree(treeData.tree || []);
        const root = treeData.tree?.[0]?.span;
        if (root) await selectSpan(root);
      })
      .catch((exception: Error) => {
        if (cancelled) return;
        setError(`轨迹详情加载失败：${exception.message}`);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [pid, traceId, sessionId, selectSpan]);

  return (
    <>
      <div className="mon-detail-head">
        <button className="text-link" onClick={() => goPortal("traces")}>
          <AppIcon name="arrow" size={12} /> 返回轨迹列表
        </button>
      </div>

      {error ? <div className="mon-error">{error}</div> : null}

      {trace ? (
        <div className="mon-kpi-grid">
          <div className="mon-kpi">
            <label>轨迹</label>
            <strong className="mon-kpi-text">{trace.name || trace.id}</strong>
            <small>{fmtTime(trace.startedAt)} · {trace.agentName || "—"}</small>
          </div>
          <div className="mon-kpi">
            <label>耗时</label>
            <strong>{fmtMs(trace.durationMs)}</strong>
            <small>Trace 端到端</small>
          </div>
          <div className="mon-kpi">
            <label>Token</label>
            <strong>{fmtInt(trace.totalTokens)}</strong>
            <small>输入 {fmtInt(trace.promptTokens)} · 输出 {fmtInt(trace.completionTokens)}</small>
          </div>
          <div className="mon-kpi">
            <label>节点 / 会话</label>
            <strong>{fmtInt(trace.spanCount)}</strong>
            <small className="mon-ellipsis">{trace.sessionId || "未关联会话"}</small>
          </div>
        </div>
      ) : null}

      <div className="mon-detail-grid">
        <section className="panel mon-panel">
          <div className="panel-heading"><h2 className="panel-title">调用树</h2></div>
          <div className="mon-panel-body">
            {loading ? <div className="mon-empty">加载调用树…</div> : <TraceTree nodes={tree} selectedId={selectedId} onSelect={selectSpan} />}
          </div>
        </section>

        <section className="panel mon-panel">
          <div className="panel-heading mon-heading-row">
            <h2 className="panel-title">节点详情</h2>
            {spanDetail ? <span className={`mon-status ${spanDetail.status || ""}`}>{spanDetail.status}</span> : null}
          </div>
          <div className="mon-panel-body">
            {!spanDetail ? (
              <div className="mon-empty">从左侧选择一个节点</div>
            ) : (
              <>
                <dl className="mon-span-meta">
                  <dt>名称</dt><dd>{spanDetail.name}</dd>
                  <dt>类型</dt><dd>{spanDetail.spanType}</dd>
                  <dt>耗时</dt><dd>{fmtMs(spanDetail.durationMs)}</dd>
                  {spanDetail.model ? <><dt>模型</dt><dd>{spanDetail.model}</dd></> : null}
                  {spanDetail.toolName ? <><dt>工具</dt><dd>{spanDetail.toolName}</dd></> : null}
                  <dt>开始</dt><dd>{fmtTime(spanDetail.startedAt)}</dd>
                </dl>

                {spanDetail.errorInfo ? (
                  <div className="mon-error">
                    {spanDetail.errorInfo.errorType || "错误"}：{spanDetail.errorInfo.message}
                  </div>
                ) : null}

                <h4 className="mon-sub-title">Input</h4>
                <JsonView value={spanDetail.input} />

                <h4 className="mon-sub-title">Output</h4>
                <JsonView value={spanDetail.output} />
              </>
            )}
          </div>
        </section>
      </div>
    </>
  );
}
