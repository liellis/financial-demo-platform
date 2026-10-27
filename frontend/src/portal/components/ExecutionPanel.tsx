import AppIcon from "./AppIcon";
import { demo, useDemo } from "../store/demo";
import { goPortal } from "../router";

export default function ExecutionPanel({ onApproval }: { onApproval: () => void }) {
  useDemo();

  const rows = [
    { icon: "graph", title: "工程知识检索", platform: "CodeGraph · 知识工程", desc: "业务规则与代码的关联上下文", chip: "12 项资产", status: "示例完成", tag: "success" },
    { icon: "document", title: "变更影响评估", platform: "Agent Teams", desc: "影响范围、复用建议与关键约束", chip: "6 个影响点", status: "示例完成", tag: "success" },
    { icon: "code", title: "增量开发与验证", platform: "iCode · Talent", desc: "使用已确认方案和已有规范执行", chip: "方案确认后执行", status: demo.approved ? "示例待执行" : "等待确认", tag: "pending" },
  ];

  return (
    <section className="panel">
      <div className="panel-heading">
        <div>
          <h2 className="panel-title">Skill 协同执行 <AppIcon name="spark" size={14} /></h2>
          <p className="panel-subtitle">让已有技能，成为连续的研发工作</p>
        </div>
        <a
          className="text-link"
          href="#/portal/skills"
          onClick={(event) => {
            event.preventDefault();
            goPortal("skills");
          }}
        >
          查看全部 <AppIcon name="arrow" size={12} />
        </a>
      </div>
      <div className="execution-list">
        {rows.map((row) => (
          <div key={row.title} className="execution-row">
            <span className="agent-icon"><AppIcon name={row.icon} size={15} /></span>
            <div className="execution-copy">
              <h3>{row.title}</h3>
              <p>{row.platform}<br />{row.desc}</p>
              <div className="asset-chips"><span className="asset-chip">{row.chip}</span></div>
            </div>
            <span className={`pill ${row.tag}`}>{row.status}</span>
          </div>
        ))}
      </div>
      <div className="approval-box">
        <AppIcon name="lock" size={15} />
        <div>
          <b>{demo.approved ? "演示方案已确认" : "执行前，先确认变更边界"}</b>
          <p>复用与新增范围、关键规则及验证计划<br />审批动作仅作用于本地原型。</p>
          <button onClick={onApproval}>
            {demo.approved ? "查看确认记录" : "查看方案并模拟确认"} <AppIcon name="arrow" size={11} />
          </button>
        </div>
      </div>
      <div className="execution-footer">
        <div>
          <small>统一关联 · DemoRun</small>
          <p style={{ marginTop: 5, fontSize: 13 }}>DEMO-RUN-001 <span style={{ color: "#8193ae" }}>/ MOCK</span></p>
        </div>
        <a
          className="link"
          href="#/portal/trace"
          onClick={(event) => {
            event.preventDefault();
            goPortal("trace");
          }}
        >
          每一步，都可追溯 <AppIcon name="arrow" size={12} />
        </a>
      </div>
    </section>
  );
}
