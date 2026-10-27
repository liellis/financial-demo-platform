import { icons } from "../icons";

const nodes = [
  { id: "rule", x: 30, y: 72, w: 150, label: "客户与业务规则", sub: "BUSINESS KNOWLEDGE", icon: "book" },
  { id: "service", x: 246, y: 72, w: 160, label: "已有业务处理服务", sub: "CHANGE CANDIDATE", icon: "code", hot: true },
  { id: "rule", x: 246, y: 199, w: 160, label: "新增规则与约束", sub: "INCREMENTAL CHANGE", icon: "spark", hot: true },
  { id: "ledger", x: 477, y: 15, w: 154, label: "账户与账务组件", sub: "PROTECTED ASSET", icon: "database" },
  { id: "service", x: 477, y: 129, w: 154, label: "事件与通知服务", sub: "REUSABLE SERVICE", icon: "layers" },
  { id: "tests", x: 477, y: 243, w: 154, label: "已有回归测试", sub: "TEST ASSET", icon: "shield" },
];

export default function KnowledgeGraph({ onEvidence }: { onEvidence: (id: string) => void }) {
  return (
    <div className="graph-wrap">
      <svg className="graph-svg" viewBox="0 0 674 330" role="group" aria-label="示例业务知识与代码资产关系图">
        <defs>
          <marker id="end-arrow" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="4" markerHeight="4" orient="auto">
            <path d="M0 0 10 5 0 10" fill="#c3cedf" />
          </marker>
        </defs>
        <path className="wire" d="M180 104H246" markerEnd="url(#end-arrow)" />
        <path className="wire-hot" d="M326 135V199" />
        <path className="wire" d="M406 104h27V47h44" markerEnd="url(#end-arrow)" />
        <path className="wire" d="M406 104h27v57h44" markerEnd="url(#end-arrow)" />
        <path className="wire wire-dash" d="M406 231h27v44h44" markerEnd="url(#end-arrow)" />
        <path className="wire wire-dash" d="M180 104h22v171h275" />
        <text className="link-label" x="194" y="93">关联</text>
        <text className="link-label" x="337" y="171">增量扩展</text>
        <text className="link-label" x="437" y="269">验证</text>
        {nodes.map((node, index) => (
          <g
            key={`${node.id}-${index}`}
            className={node.hot ? "graph-node hot" : "graph-node"}
            tabIndex={0}
            role="button"
            aria-label={`查看${node.label}的示例证据`}
            onClick={() => onEvidence(node.id)}
            onKeyDown={(event) => {
              if (event.key === "Enter") {
                event.preventDefault();
                onEvidence(node.id);
              }
            }}
          >
            <rect className="node-bg" x={node.x} y={node.y} width={node.w} height="63" rx="8" />
            <rect className="icon-bg" x={node.x + 11} y={node.y + 16} width="25" height="25" rx="6" />
            <g
              className="node-icon"
              transform={`translate(${node.x + 16} ${node.y + 21}) scale(.62)`}
              dangerouslySetInnerHTML={{ __html: icons[node.icon] }}
            />
            <text className="node-label" x={node.x + 45} y={node.y + 26}>{node.label}</text>
            <text className="node-small" x={node.x + 45} y={node.y + 43}>{node.sub}</text>
          </g>
        ))}
      </svg>
      <div className="graph-legend">
        <span><i className="legend-square red" />候选变更</span>
        <span><i className="legend-square green" />复用 / 保护</span>
        <span><i className="legend-square" />关联依赖</span>
        <span style={{ marginLeft: "auto" }}>示例关系图 · 点击节点查看依据</span>
      </div>
    </div>
  );
}
