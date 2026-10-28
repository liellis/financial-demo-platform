import { fmtMs, type SpanRow, type TreeNode } from "../../services/monitor";

interface Row {
  span: SpanRow;
  depth: number;
}

export default function TraceTree({
  nodes,
  selectedId,
  onSelect,
}: {
  nodes: TreeNode[];
  selectedId: string;
  onSelect: (span: SpanRow) => void;
}) {
  const rows: Row[] = [];
  const walk = (items: TreeNode[] | undefined, depth: number) => {
    for (const node of items || []) {
      if (!node?.span) continue;
      rows.push({ span: node.span, depth });
      walk(node.children, depth + 1);
    }
  };
  walk(nodes, 0);

  return (
    <div className="mon-tree">
      {rows.length === 0 ? <div className="mon-empty">该轨迹没有调用节点</div> : null}
      {rows.map((row) => (
        <button
          key={row.span.id}
          className={row.span.id === selectedId ? "mon-tree-row active" : "mon-tree-row"}
          style={{ paddingLeft: `${10 + row.depth * 18}px` }}
          onClick={() => onSelect(row.span)}
        >
          <span className={`mon-dot ${row.span.status || ""}`} />
          <b>{row.span.name}</b>
          <span className="mon-type">{row.span.spanType}</span>
          <em>{fmtMs(row.span.durationMs)}</em>
        </button>
      ))}
    </div>
  );
}
