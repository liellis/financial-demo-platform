import AppIcon from "../components/AppIcon";
import PageIntro from "../components/PageIntro";
import { demo, useDemo } from "../store/demo";
import { openModal, showToast } from "../store/ui";
import { goPortal, type PortalPage } from "../router";

const metrics = [
  { label: "交付总历时", desc: "需求确认至验收完成" },
  { label: "累计人工投入", desc: "包含审核、测试与返工" },
  { label: "影响分析召回率", desc: "对照独立专家参考集" },
  { label: "质量与返工", desc: "同口径门禁与缺陷记录" },
];

const bars = [
  { label: "需求分析", traditional: 80, ai: 45 },
  { label: "工程理解", traditional: 95, ai: 38 },
  { label: "开发实施", traditional: 150, ai: 100 },
  { label: "测试返工", traditional: 90, ai: 65 },
];

const efficiencySources = [
  { title: "更快理解已有工程", desc: "检索业务、代码、规则与关联测试", page: "knowledge" },
  { title: "减少重复工程工作", desc: "将已有资产和规范传入 Skill", page: "knowledge" },
  { title: "衔接开发与质量验证", desc: "编排执行、收集反馈并组织修复", page: "quality" },
];

const stageRows = [
  { stage: "理解与分析", capability: "CodeGraph / Agent Teams", artifact: "相关代码上下文、影响分析", human: "规则核实" },
  { stage: "计划与实施", capability: "Agent Teams / iCode / Talent", artifact: "SDD、变更范围、代码差异", human: "方案批准" },
  { stage: "质量与交付", capability: "测试平台 / iCode", artifact: "测试与安全报告、交付清单", human: "最终评审" },
];

export default function EfficiencyPage() {
  useDemo();

  return (
    <>
      <PageIntro
        kicker="06 / PRODUCTIVITY & BUSINESS VALUE"
        title="交付的价值，用过程与结果证明"
        desc="同任务、同基线、同验收，比较研发投入与业务结果。"
      >
        <button className="btn" onClick={() => showToast("对照实验口径为示例，真实记录待导入")}>
          <AppIcon name="help" size={14} />实验口径
        </button>
        <button className="btn btn-primary" onClick={() => goPortal("business")}>
          <AppIcon name="credit" size={14} />查看业务验收
        </button>
      </PageIntro>

      <div className="metric-four">
        {metrics.map((metric) => (
          <div key={metric.label} className="metric-box">
            <label>{metric.label}</label>
            <strong>—</strong>
            <small>{metric.desc}<br />待导入真实实验记录</small>
          </div>
        ))}
      </div>

      <div className="split">
        <section className="panel">
          <div className="panel-heading">
            <div>
              <h2 className="panel-title">相同任务，比较研发过程</h2>
              <p className="panel-subtitle">
                {demo.comparison ? "示意对照已开启 · 柱形数值为虚构示例，不是实测结论" : "对照实验数据尚未导入，不计算提效百分比"}
              </p>
            </div>
            <button className="btn btn-small" onClick={() => (demo.comparison = !demo.comparison)}>
              {demo.comparison ? "关闭示意" : "查看示意对照"}
            </button>
          </div>
          <div className="panel-body">
            <div className="chart-box">
              <div className="bar-chart">
                {bars.map((bar) => (
                  <div key={bar.label} className="bar-group">
                    <div className="bar" style={{ height: `${bar.traditional}px` }}>
                      <span>{demo.comparison ? `${bar.traditional} min` : "—"}</span>
                    </div>
                    <div className="bar ai" style={{ height: `${bar.ai}px` }}>
                      <span>{demo.comparison ? `${bar.ai} min` : "—"}</span>
                    </div>
                    <label>{bar.label}</label>
                  </div>
                ))}
                {!demo.comparison ? (
                  <div className="chart-overlay">
                    导入同基线、同验收的实验记录<br /><small>用真实任务证明价值，不预设提升幅度</small>
                  </div>
                ) : null}
              </div>
              <div className="chart-legend">
                <span><i />传统研发</span>
                <span><i className="ai" />AI 辅助研发</span>
              </div>
            </div>
          </div>
        </section>

        <section className="panel">
          <div className="panel-heading"><h2 className="panel-title">效率来自哪些工作？</h2></div>
          <div className="panel-body">
            <div className="rule-list">
              {efficiencySources.map((item) => (
                <button key={item.title} className="rule-item" onClick={() => goPortal(item.page as PortalPage)}>
                  <div><p>{item.title}</p><small>{item.desc}</small></div>
                  <AppIcon name="arrow" size={13} />
                </button>
              ))}
            </div>
            <div className="callout blue">
              <AppIcon name="clock" size={15} />
              <span>模型响应速度、Token 和工具调用次数，可在技术深潜中查看；它们不直接等于研发提效。</span>
            </div>
          </div>
        </section>
      </div>

      <section className="panel" style={{ marginTop: 20 }}>
        <div className="panel-heading"><h2 className="panel-title">阶段与交付产物 <small>示例流程</small></h2></div>
        <div className="table-wrap">
          <table>
            <thead><tr><th>阶段</th><th>执行能力</th><th>产出物</th><th>人工参与</th><th>证据</th></tr></thead>
            <tbody>
              {stageRows.map((row) => (
                <tr key={row.stage}>
                  <td>{row.stage}</td><td>{row.capability}</td><td>{row.artifact}</td><td>{row.human}</td>
                  <td><button className="table-link" onClick={() => openModal("evidence", { id: "run" })}>查看示例 <AppIcon name="arrow" size={10} /></button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      {demo.view === "technical" ? (
        <div className="technical-only tech-panel">
          <b>技术指标 · 待接入监控平台</b><br />Token: — &nbsp;&nbsp; TTFT: — &nbsp;&nbsp; Tool calls: — &nbsp;&nbsp; Retries: —<br />
          指标将关联同一 DemoRun，不显示为实际已采集数据。
        </div>
      ) : null}
    </>
  );
}
