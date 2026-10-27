import AppIcon from "../components/AppIcon";
import PageIntro from "../components/PageIntro";
import { demo, useDemo } from "../store/demo";
import { requestReview, runVerification } from "../store/flow";
import { openModal } from "../store/ui";
import { goPortal } from "../router";

const protections = [
  { text: "隔离工程工作区", icon: "folder" },
  { text: "明确可修改目录与分支", icon: "branch" },
  { text: "保护关键业务约束", icon: "lock" },
  { text: "敏感信息与日志脱敏", icon: "shield" },
  { text: "保留审批与执行记录", icon: "document" },
];

export default function QualityPage() {
  useDemo();

  const gates = [
    { name: "需求确认", desc: "需求、关键规则与验收条件", color: "success", status: "示例已确认", icon: "document", evidence: "gate-0" },
    { name: "设计评估", desc: "影响范围、复用方案与风险", color: demo.approved ? "success" : "pending", status: demo.approved ? "模拟确认" : "待方案确认", icon: "graph", evidence: "gate-1" },
    { name: "构建检查", desc: "构建结果与开发规范", color: demo.verified ? "success" : "neutral", status: demo.verified ? "示例通过" : "待演练", icon: "code", evidence: "gate-2" },
    { name: "测试验证", desc: "新增、边界和必要历史回归", color: demo.verified ? "success" : "neutral", status: demo.verified ? "示例通过" : "待演练", icon: "target", evidence: "gate-3" },
    { name: "安全检查", desc: "权限、敏感信息与依赖风险", color: demo.verified ? "success" : "neutral", status: demo.verified ? "示例通过" : "待演练", icon: "shield", evidence: "gate-4" },
    { name: "人工评审", desc: "审核明确版本的交付结果", color: demo.reviewed ? "success" : "pending", status: demo.reviewed ? "模拟已审核" : "待人工评审", icon: "users", evidence: "gate-5" },
  ];

  function showEvidence(id: string) {
    openModal("evidence", { id });
  }

  return (
    <>
      <PageIntro
        kicker="04 / QUALITY & SECURITY"
        title="质量可验证，交付有边界"
        desc="从代码评审、功能回归到 DFX 与安全检查，保留证据与人工把关。"
      >
        <button className="btn" onClick={() => openModal("approval")}>
          <AppIcon name="lock" size={14} />变更边界
        </button>
        <button className="btn btn-primary" onClick={runVerification}>
          <AppIcon name="shield" size={14} />{demo.verified ? "示例检查已完成" : "运行示例验证"}
        </button>
      </PageIntro>

      <div className="v3-quality-tags">
        <span>代码 Review</span><span>功能与回归</span><span>DFX 检查</span><span>安全扫描</span><span>质量门禁</span>
      </div>

      <div className="split">
        <div className="stack">
          <section className="panel">
            <div className="panel-heading">
              <div>
                <h2 className="panel-title">六道质量门禁</h2>
                <p className="panel-subtitle">以下为本地原型状态，必选门禁未满足时，不标记交付完成</p>
              </div>
              <span className={demo.reviewed ? "pill success" : "pill pending"}>
                {demo.reviewed ? "示例门禁满足" : "等待验证与审核"}
              </span>
            </div>
            <div className="panel-body">
              <div className="gate-grid">
                {gates.map((gate) => (
                  <button key={gate.name} className="gate-card" onClick={() => showEvidence(gate.evidence)}>
                    <span className="gate-icon"><AppIcon name={gate.icon} size={20} /></span>
                    <h3>{gate.name}</h3>
                    <p>{gate.desc}</p>
                    <span className={`pill ${gate.color}`}>{gate.status}</span>
                  </button>
                ))}
              </div>
              <div className="callout blue">
                <AppIcon name="shield" size={16} />
                <span>Skill 退出成功 ≠ 验证通过 ≠ 允许交付。质量状态与人工审批分开呈现。</span>
              </div>
              <div style={{ display: "flex", gap: 9 }}>
                <button className="btn btn-small" onClick={() => openModal("test-report")}>
                  <AppIcon name="document" size={13} />查看示例测试报告
                </button>
                <button className="btn btn-small" onClick={requestReview}>
                  <AppIcon name="users" size={13} />模拟最终评审
                </button>
              </div>
            </div>
          </section>
        </div>

        <div className="stack">
          <section className="panel">
            <div className="panel-heading"><h2 className="panel-title">开发过程保护范围</h2></div>
            <div className="panel-body">
              {protections.map((item) => (
                <div key={item.text} className="risk-row">
                  <AppIcon name={item.icon} size={15} />
                  <span>{item.text}</span>
                  <span style={{ marginLeft: "auto", fontSize: 12, color: "#a1acba" }}>策略示意</span>
                </div>
              ))}
            </div>
          </section>
          <section className="panel">
            <div className="panel-heading"><h2 className="panel-title">业务规则如何被验证？</h2></div>
            <div className="panel-body">
              <p className="subheading">从验收条件到测试用例，再到当前版本的执行结果，形成关联。</p>
              <button className="related-link" onClick={() => goPortal("trace")}>需求 → 规则 → 代码 → 测试 <AppIcon name="arrow" size={13} /></button>
              <button className="related-link" onClick={() => goPortal("business")}>查看增量业务体验 <AppIcon name="arrow" size={13} /></button>
            </div>
          </section>
        </div>
      </div>
    </>
  );
}
