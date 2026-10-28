import type { TrendPoint } from "../../services/monitor";

const W = 640;
const H = 210;
const PAD = { left: 46, right: 12, top: 14, bottom: 28 };
const COLORS = ["#cf0a2c", "#4b6b93", "#6f9c86", "#b08d4f"];

export default function TrendChart({
  points,
  unit = "",
  decimals = 0,
}: {
  points: TrendPoint[];
  unit?: string;
  decimals?: number;
}) {
  if (points.length === 0) {
    return (
      <div className="mon-chart">
        <div className="mon-empty">当前时间范围内暂无数据</div>
      </div>
    );
  }

  const parsed = points
    .map((point) => ({ ...point, time: new Date(point.ts).getTime(), value: Number(point.value) || 0 }))
    .filter((point) => !Number.isNaN(point.time));
  const seriesName = (point: { series?: string }) => point.series || unit || "value";
  const names = [...new Set(parsed.map(seriesName))];

  const times = parsed.map((point) => point.time);
  const values = parsed.map((point) => point.value);
  const minT = Math.min(...times);
  const maxTRaw = Math.max(...times);
  const maxT = maxTRaw === minT ? minT + 1 : maxTRaw;
  const maxV = Math.max(...values, 1);
  const innerW = W - PAD.left - PAD.right;
  const innerH = H - PAD.top - PAD.bottom;

  const fmtNum = (value: number) =>
    Math.abs(value) >= 10000 ? `${(value / 10000).toFixed(1)}万` : value.toFixed(decimals);

  const series = names.map((name, index) => {
    const rows = parsed
      .filter((point) => seriesName(point) === name)
      .sort((a, b) => a.time - b.time);
    const dots = rows.map((row) => ({
      x: PAD.left + ((row.time - minT) / (maxT - minT)) * innerW,
      y: PAD.top + innerH - (row.value / maxV) * innerH,
    }));
    return {
      name,
      color: COLORS[index % COLORS.length],
      dots,
      path: dots.map((dot) => `${dot.x.toFixed(1)},${dot.y.toFixed(1)}`).join(" "),
    };
  });

  const gridLines = [0, 0.33, 0.66, 1].map((ratio) => ({
    y: PAD.top + innerH - ratio * innerH,
    label: fmtNum(maxV * ratio),
  }));

  const pad = (n: number) => String(n).padStart(2, "0");
  const timeTicks = [0, 0.5, 1].map((ratio, index) => {
    const time = new Date(minT + (maxT - minT) * ratio);
    const anchor: "start" | "middle" | "end" = index === 0 ? "start" : index === 2 ? "end" : "middle";
    return {
      x: PAD.left + innerW * ratio,
      anchor,
      label: `${pad(time.getMonth() + 1)}-${pad(time.getDate())} ${pad(time.getHours())}:${pad(time.getMinutes())}`,
    };
  });

  return (
    <div className="mon-chart">
      <svg className="mon-chart-svg" viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" role="img">
        <g className="mon-grid">
          {gridLines.map((line) => (
            <line key={line.y} x1={PAD.left} x2={W - PAD.right} y1={line.y} y2={line.y} />
          ))}
          {gridLines.map((line) => (
            <text key={`label-${line.y}`} x={PAD.left - 8} y={line.y + 4} textAnchor="end">{line.label}</text>
          ))}
        </g>
        {series.map((item) => (
          <g key={item.name}>
            <polyline
              points={item.path}
              fill="none"
              stroke={item.color}
              strokeWidth="2"
              strokeLinejoin="round"
              vectorEffect="non-scaling-stroke"
            />
            {item.dots.map((dot, index) => (
              <circle key={index} cx={dot.x} cy={dot.y} r="2.6" fill={item.color} />
            ))}
          </g>
        ))}
        <g className="mon-axis">
          {timeTicks.map((tick) => (
            <text key={tick.x} x={tick.x} y={H - 8} textAnchor={tick.anchor}>{tick.label}</text>
          ))}
        </g>
      </svg>
      <div className="mon-legend">
        {series.map((item) => (
          <span key={item.name}>
            <i style={{ background: item.color }} />{item.name}
          </span>
        ))}
      </div>
    </div>
  );
}
