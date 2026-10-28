import { useEffect, useRef, useState } from "react";
import PageIntro from "../components/PageIntro";
import BreakdownBars from "../components/monitor/BreakdownBars";
import TrajectoryPanel from "../components/monitor/TrajectoryPanel";
import TrendChart from "../components/monitor/TrendChart";
import {
  fetchBreakdown,
  fetchSummary,
  fetchTimeseries,
  fetchTrajectory,
  fetchTrajectorySessions,
  fmtInt,
  fmtMs,
  fmtNs,
  fmtPercent,
  fmtTime,
  projectId,
  type BarItem,
  type Row,
  type TrendPoint,
} from "../services/monitor";

type RangeKey = "1h" | "24h" | "7d";

const RANGES: { key: RangeKey; label: string; hours: number; interval: string }[] = [
  { key: "1h", label: "最近 1 小时", hours: 1, interval: "5m" },
  { key: "24h", label: "最近 24 小时", hours: 24, interval: "1h" },
  { key: "7d", label: "最近 7 天", hours: 168, interval: "1d" },
];

export default function MetricsPage() {
  const pid = projectId();
  const [rangeKey, setRangeKey] = useState<RangeKey>("24h");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [summary, setSummary] = useState<Row | null>(null);
  const [tokenPoints, setTokenPoints] = useState<TrendPoint[]>([]);
  const [llmPoints, setLlmPoints] = useState<TrendPoint[]>([]);
  const [modelItems, setModelItems] = useState<BarItem[]>([]);
  const [toolItems, setToolItems] = useState<BarItem[]>([]);
  const [errorItems, setErrorItems] = useState<BarItem[]>([]);
  const [sessions, setSessions] = useState<Row[]>([]);
  const [trajectory, setTrajectory] = useState<Row | null>(null);
  const [trajectoryLoading, setTrajectoryLoading] = useState(false);
  const [selectedSession, setSelectedSession] = useState("");
  const [reloadToken, setReloadToken] = useState(0);

  const selectedRef = useRef(selectedSession);
  useEffect(() => {
    selectedRef.current = selectedSession;
  }, [selectedSession]);

  async function loadTrajectory(sessionId: string) {
    setTrajectoryLoading(true);
    try {
      setTrajectory(await fetchTrajectory(pid, sessionId));
    } catch {
      setTrajectory(null);
    } finally {
      setTrajectoryLoading(false);
    }
  }

  useEffect(() => {
    let cancelled = false;
    const config = RANGES.find((item) => item.key === rangeKey) ?? RANGES[1];
    const to = new Date();
    const from = new Date(to.getTime() - config.hours * 3600 * 1000);
    const range = { from: from.toISOString(), to: to.toISOString() };

    setLoading(true);
    setError("");
    Promise.all([
      fetchSummary(pid, range),
      fetchTimeseries(pid, { ...range, metric: "tokens", interval: config.interval, group_by: "token_type" }),
      fetchTimeseries(pid, { ...range, metric: "llm_duration", interval: config.interval }),
      fetchBreakdown(pid, { ...range, metric: "tokens", dimension: "request_model" }),
      fetchBreakdown(pid, { ...range, metric: "tool_calls", dimension: "tool_name" }),
      fetchBreakdown(pid, { ...range, metric: "tool_failures", dimension: "error_type" }),
      fetchTrajectorySessions(pid),
    ])
      .then(([s, tokens, llm, models, tools, errors, sessionList]) => {
        if (cancelled) return;
        setSummary(s);
        setTokenPoints(tokens.points || []);
        setLlmPoints(llm.points || []);
        setModelItems(models.items || []);
        setToolItems(tools.items || []);
        setErrorItems(errors.items || []);
        setSessions(sessionList.items || []);

        const current = selectedRef.current;
        if (current && !(sessionList.items || []).some((row: Row) => row.sessionId === current)) {
          setSelectedSession("");
          setTrajectory(null);
        }
      })
      .catch((exception: Error) => {
        if (!cancelled) setError(`指标加载失败：${exception.message}`);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [pid, rangeKey, reloadToken]);

  const kpis = [
    {
      label: "会话数",
      value: fmtInt(summary?.traces?.sessions),
      note: `轨迹 ${fmtInt(summary?.traces?.count)} 条 · 成功率 ${fmtPercent(summary?.traces?.successRate)}`,
    },
    {
      label: "总 Token",
      value: fmtInt((summary?.tokens?.input ?? 0) + (summary?.tokens?.output ?? 0)),
      note: `输入 ${fmtInt(summary?.tokens?.input)} · 输出 ${fmtInt(summary?.tokens?.output)} · 缓存读 ${fmtInt(summary?.tokens?.cacheRead)}`,
    },
    {
      label: "LLM 平均耗时",
      value: fmtMs(summary?.llm?.avgDurationMs),
      note: `${fmtInt(summary?.llm?.calls)} 次调用 · 轨迹 p95 ${fmtMs(summary?.traces?.p95DurationMs)}`,
    },
    {
      label: "工具失败率",
      value: fmtPercent(summary?.tools?.failureRate),
      note: `${fmtInt(summary?.tools?.calls)} 次调用 · 平均 ${fmtMs(summary?.tools?.avgDurationMs)}`,
    },
  ];

  function selectSession(sessionId: string) {
    if (selectedSession === sessionId) {
      setSelectedSession("");
      setTrajectory(null);
      return;
    }
    setSelectedSession(sessionId);
    setTrajectory(null);
    loadTrajectory(sessionId);
  }

  return (
    <>
      <PageIntro
        kicker="06 / TRACE METRICS"
        title="监控指标"
        desc="OTel 单次调用指标、轨迹表指标与整轮 / 整会话效率。"
      >
        <div className="mon-actions">
          <div className="mon-range">
            {RANGES.map((item) => (
              <button
                key={item.key}
                className={rangeKey === item.key ? "active" : ""}
                onClick={() => {
                  setSelectedSession("");
                  setTrajectory(null);
                  setRangeKey(item.key);
                }}
              >
                {item.label}
              </button>
            ))}
          </div>
          <button className="btn" disabled={loading} onClick={() => setReloadToken((token) => token + 1)}>{loading ? "刷新中…" : "刷新"}</button>
        </div>
      </PageIntro>

      {error ? <div className="mon-error">{error}</div> : null}

      <div className="mon-kpi-grid">
        {kpis.map((kpi) => (
          <div key={kpi.label} className="mon-kpi">
            <label>{kpi.label}</label>
            <strong>{kpi.value}</strong>
            <small>{kpi.note}</small>
          </div>
        ))}
      </div>

      <div className="mon-grid-2">
        <section className="panel mon-panel">
          <div className="panel-heading">
            <h2 className="panel-title">Token 趋势（输入 / 输出）</h2>
            <span className="mini-tag">OTel · gen_ai.client.token.usage</span>
          </div>
          <div className="mon-panel-body">
            <TrendChart points={tokenPoints} unit=" tokens" decimals={0} />
          </div>
        </section>

        <section className="panel mon-panel">
          <div className="panel-heading">
            <h2 className="panel-title">LLM 平均耗时趋势</h2>
            <span className="mini-tag">SUM(sum) / SUM(count)</span>
          </div>
          <div className="mon-panel-body">
            <TrendChart points={llmPoints} unit=" ms" decimals={0} />
          </div>
        </section>
      </div>

      <div className="mon-grid-3">
        <section className="panel mon-panel">
          <div className="panel-heading"><h2 className="panel-title">Top 模型（Token）</h2></div>
          <div className="mon-panel-body"><BreakdownBars items={modelItems} unit=" tokens" decimals={0} /></div>
        </section>
        <section className="panel mon-panel">
          <div className="panel-heading"><h2 className="panel-title">Top 工具（调用次数）</h2></div>
          <div className="mon-panel-body"><BreakdownBars items={toolItems} decimals={0} /></div>
        </section>
        <section className="panel mon-panel">
          <div className="panel-heading"><h2 className="panel-title">错误类型</h2></div>
          <div className="mon-panel-body"><BreakdownBars items={errorItems} decimals={0} /></div>
        </section>
      </div>

      <section className="panel mon-panel">
        <div className="panel-heading mon-heading-row">
          <div>
            <h2 className="panel-title">轨迹效率（整轮 / 整会话）</h2>
            <p className="panel-subtitle">选择一条会话记录，查看该会话的效率明细</p>
          </div>
          <span className="mini-tag">{sessions.length} 条已导入会话</span>
        </div>
        <div className="table-wrap">
          <table>
            <thead>
              <tr><th>Session</th><th>导出时间</th><th>轮数</th><th>总耗时</th><th>实际工作</th><th>并行度</th><th>缓存命中率</th></tr>
            </thead>
            <tbody>
              {sessions.length === 0 ? (
                <tr><td colSpan={7} className="mon-table-empty">暂无已导入的轨迹分析记录</td></tr>
              ) : null}
              {sessions.map((row) => (
                <tr
                  key={row.sessionId}
                  className={row.sessionId === selectedSession ? "mon-click-row mon-row-active" : "mon-click-row"}
                  onClick={() => selectSession(row.sessionId)}
                >
                  <td><code>{row.sessionId}</code></td>
                  <td>{fmtTime(row.exportedAt)}</td>
                  <td>{fmtInt(row.turnCount)}</td>
                  <td>{fmtNs(row.elapsedNs)}</td>
                  <td>{fmtNs(row.exclusiveWorkNs)}</td>
                  <td>{fmtPercent(row.parallelism)}</td>
                  <td>{fmtPercent(row.cacheHitRate)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      {selectedSession ? (
        <section className="panel mon-panel">
          <div className="panel-heading mon-heading-row">
            <div>
              <h2 className="panel-title">轨迹效率明细</h2>
              <p className="panel-subtitle">{selectedSession}</p>
            </div>
            <button className="text-link" onClick={() => { setSelectedSession(""); setTrajectory(null); }}>收起明细</button>
          </div>
          <div className="mon-panel-body">
            <TrajectoryPanel fact={trajectory} loading={trajectoryLoading} />
          </div>
        </section>
      ) : null}
    </>
  );
}
