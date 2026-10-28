import { fmtInt, fmtNs, fmtPercent, fmtTime, type Row } from "../../services/monitor";

function bucket(source: Row | undefined, key: string): { value: number | null; estimated: boolean } {
  const item = source?.[key];
  if (item === undefined || item === null) return { value: null, estimated: false };
  if (typeof item === "object") {
    return { value: item.value ?? null, estimated: item.precision === "estimated" };
  }
  return { value: item, estimated: false };
}

const WALL_LABELS: Record<string, string> = { model: "模型", tools: "工具", wait: "等待", idle: "空闲" };
const TOKEN_LABELS: Record<string, string> = {
  input: "输入",
  output: "输出",
  reasoning: "推理",
  cache_read: "缓存读",
  cache_creation: "缓存写",
};
const OPERATION_LABELS: Record<string, string> = {
  "model.run": "模型运行",
  "model.cycle": "模型周期",
  "model.exchange": "模型交换",
  approval: "人工确认",
  preparation: "预处理",
  "tool.operation": "工具调用",
  search: "检索",
  read: "读取",
  edit: "编辑",
  verify: "验证",
};

export default function TrajectoryPanel({ fact, loading }: { fact: Row | null; loading: boolean }) {
  if (loading) return <div className="mon-empty">加载轨迹效率…</div>;
  if (!fact) {
    return (
      <div className="mon-empty">
        该会话尚未导入轨迹分析结果（`icode trajectory export --format json` 后写入 trajectory_fact）。
      </div>
    );
  }

  const cards = [
    { label: "轮数", value: fmtInt(fact.turnCount), note: `导出于 ${fmtTime(fact.exportedAt)}` },
    { label: "会话总耗时", value: fmtNs(fact.elapsedNs), note: "墙钟时间" },
    { label: "实际工作时长", value: fmtNs(fact.exclusiveWorkNs), note: "重叠部分重复计入" },
    { label: "并行度", value: `${((Number(fact.parallelism) || 0) * 100).toFixed(1)}%`, note: "工作时长 / 墙钟时间" },
    { label: "并行节省", value: fmtNs(fact.overlapGainNs), note: "相比串行省下的时间" },
    { label: "缓存命中率", value: fmtPercent(fact.cacheHitRate), note: "cache_read / input" },
  ];

  const wall = fact.wallTime || {};
  const wallTotal = Object.values(wall).reduce((sum: number, item: any) => sum + (Number(item?.value) || 0), 0) || 1;
  const wallRows = Object.entries(WALL_LABELS)
    .map(([key, label]) => {
      const value = Number(wall[key]?.value) || 0;
      return { label, value: fmtNs(value), ratio: Number(((value / wallTotal) * 100).toFixed(1)) };
    })
    .filter((row) => row.ratio > 0);

  const tokens = fact.tokens || {};
  const rawTokens = Object.entries(TOKEN_LABELS)
    .map(([key, label]) => ({ label, ...bucket(tokens, key) }))
    .filter((row) => row.value !== null && row.value !== undefined);
  const maxToken = Math.max(...rawTokens.map((row) => Number(row.value) || 0), 1);
  const tokenRows = rawTokens.map((row) => ({
    label: row.label,
    estimated: row.estimated,
    value: fmtInt(row.value),
    ratio: Math.max(((Number(row.value) || 0) / maxToken) * 100, 1),
  }));

  const operationRows = Object.entries(fact.operations || {}).map(([key, value]) => ({
    label: OPERATION_LABELS[key] || key,
    value: fmtInt(value),
  }));

  const validation = fact.validation || {};
  const validationRows: { label: string; value: string }[] = [];
  const push = (label: string, value: unknown) => {
    if (value === undefined || value === null) return;
    validationRows.push({ label, value: typeof value === "number" ? fmtInt(value) : String(value) });
  };
  push("排查工具调用", bucket(validation, "tool_count").value);
  push("工具失败次数", bucket(validation, "tool_failure_count").value);
  push("无净变化的改动", bucket(validation, "net_zero_churn_count").value);
  push("未验证的变更", bucket(validation, "unverified_change_count").value);
  push("编辑-验证循环", bucket(validation, "edit_verify_cycle_count").value);
  const funnel = validation.funnel || {};
  push(
    "检索 / 读取 / 编辑 / 验证",
    ["search", "read", "edit", "verify"].map((key) => Number(funnel[key]?.value) || 0).join(" / "),
  );

  return (
    <div className="mon-traj">
      <div className="mon-traj-kpi">
        {cards.map((card) => (
          <div key={card.label}>
            <label>{card.label}</label>
            <strong>{card.value}</strong>
            <small>{card.note}</small>
          </div>
        ))}
      </div>

      <div className="mon-traj-grid">
        <section>
          <h4>时间去向</h4>
          {wallRows.map((row) => (
            <div key={row.label} className="mon-traj-line">
              <span>{row.label}</span>
              <i><b style={{ width: `${row.ratio}%` }} /></i>
              <em>{row.value} · {row.ratio}%</em>
            </div>
          ))}
        </section>

        <section>
          <h4>Token 构成</h4>
          {tokenRows.map((row) => (
            <div key={row.label} className="mon-traj-line">
              <span>{row.label}</span>
              <i><b style={{ width: `${row.ratio}%` }} /></i>
              <em>{row.value}{row.estimated ? " · 估算" : ""}</em>
            </div>
          ))}
        </section>

        <section>
          <h4>操作分类</h4>
          {operationRows.length === 0 ? <div className="mon-traj-muted">无操作记录</div> : null}
          {operationRows.map((row) => (
            <div key={row.label} className="mon-traj-pair">
              <span>{row.label}</span><b>{row.value}</b>
            </div>
          ))}
        </section>

        <section>
          <h4>变更验证与失败恢复</h4>
          {validationRows.map((row) => (
            <div key={row.label} className="mon-traj-pair">
              <span>{row.label}</span><b>{row.value}</b>
            </div>
          ))}
        </section>
      </div>
    </div>
  );
}
