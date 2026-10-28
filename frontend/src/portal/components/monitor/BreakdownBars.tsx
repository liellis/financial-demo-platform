import type { BarItem } from "../../services/monitor";

export default function BreakdownBars({
  items,
  unit = "",
  decimals = 0,
}: {
  items: BarItem[];
  unit?: string;
  decimals?: number;
}) {
  const max = Math.max(...items.map((item) => Number(item.value) || 0), 1);
  const normalized = items.map((item) => {
    const value = Number(item.value) || 0;
    const shown = Math.abs(value) >= 10000 ? `${(value / 10000).toFixed(1)}万` : value.toFixed(decimals);
    return {
      name: item.name || "unknown",
      ratio: Math.max((value / max) * 100, 1),
      label: `${shown}${unit}`,
    };
  });

  return (
    <div className="mon-bars">
      {items.length === 0 ? <div className="mon-empty">当前时间范围内暂无数据</div> : null}
      {normalized.map((item) => (
        <div key={item.name} className="mon-bar-row">
          <span className="mon-bar-name" title={item.name}>{item.name}</span>
          <span className="mon-bar-track"><i style={{ width: `${item.ratio}%` }} /></span>
          <b>{item.label}</b>
        </div>
      ))}
    </div>
  );
}
