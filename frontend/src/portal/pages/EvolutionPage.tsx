import AppIcon from "../components/AppIcon";
import PageIntro from "../components/PageIntro";
import { demo, useDemo } from "../store/demo";
import { openModal, showToast } from "../store/ui";

const stages = ["运行反馈", "改进候选", "离线评测", "人工审核", "版本发布"];
const rules = [
  { title: "先评测", desc: "固定任务集与相同验收口径" },
  { title: "再审核", desc: "比较收益、退化与风险" },
  { title: "可回滚", desc: "保留版本与发布记录" },
];

export default function EvolutionPage() {
  useDemo();

  const evolutionStatus = ["待评测", "待评测", "评测演练", "待模拟审核", "模拟已审核"][demo.evolution] || "待评测";

  function evolve() {
    if (demo.evolution < 2) {
      demo.evolution = 2;
      showToast("已切换至离线评测示意，真实评测结果待导入");
    } else if (demo.evolution < 4) {
      demo.evolution = 4;
      showToast("已展示审核后的版本状态；没有真实发布");
    } else {
      showToast("原型流程已到发布示意；没有发生真实发布");
    }
  }

  function openEvaluation() {
    demo.evolution = 2;
    openModal("evolution-evaluation");
  }

  return (
    <>
      <PageIntro
        kicker="03 / CONTROLLED PLATFORM EVOLUTION"
        title="让研发反馈，变成可验证的能力改进"
        desc="问题 → 候选改进 → 固定任务集评测 → 人工审核 → 发布与观察。"
      >
        <button className="btn" onClick={() => showToast("评测口径为示例，真实评测结果待导入")}>
          <AppIcon name="help" size={14} />评测口径
        </button>
        <button className="btn btn-primary" onClick={evolve}>
          <AppIcon name="spark" size={14} />体验改进流程
        </button>
      </PageIntro>

      <div className="evolution-flow">
        {stages.map((step, index) => (
          <span key={step} style={{ display: "contents" }}>
            <div className={demo.evolution === index ? "evolution-step active" : "evolution-step"}>
              <span className="num">0{index + 1}</span>{step}
            </div>
            {index < stages.length - 1 ? <AppIcon name="arrow" size={13} /> : null}
          </span>
        ))}
      </div>

      <div className="split">
        <section className="panel">
          <div className="panel-heading">
            <div>
              <h2 className="panel-title">Skill 版本改进候选</h2>
              <p className="panel-subtitle">版本及改进内容均为示例；平台不会自动发布未经审核的能力</p>
            </div>
            <span className="pill progress">{demo.evolution === 4 ? "模拟审核后发布" : "候选评审"}</span>
          </div>
          <div className="panel-body">
            <div className="version-pair">
              <div className="version-card">
                <span className="eyebrow">CURRENT · 示例</span>
                <h3>v1.2</h3>
                <p className="subheading">存量系统影响分析 Skill</p>
                <ul>
                  <li>基于代码依赖定位变更范围</li>
                  <li>检索已有业务规则</li>
                  <li>关联主要回归用例</li>
                </ul>
              </div>
              <div className="version-card proposed">
                <span className="eyebrow">CANDIDATE · 示例</span>
                <h3>v1.3 <small style={{ fontSize: 13, color: "#8c9abb" }}>{evolutionStatus}</small></h3>
                <p className="subheading">补强规则与测试的关联覆盖</p>
                <ul>
                  <li>新增跨模块影响检查提示</li>
                  <li>明确未确认规则处理方式</li>
                  <li>补充失败用例回归检索</li>
                </ul>
              </div>
            </div>
            <div className="callout">
              <AppIcon name="alert" size={15} />
              <span>尚未导入真实新旧版本评测结果，不展示改进收益，不允许发布真实版本。</span>
            </div>
            <div style={{ display: "flex", gap: 9 }}>
              <button className="btn btn-small" onClick={() => openModal("evolution-source")}>
                <AppIcon name="document" size={13} />查看候选来源
              </button>
              <button className="btn btn-small" onClick={openEvaluation}>
                <AppIcon name="chart" size={13} />查看评测示例
              </button>
            </div>
          </div>
        </section>

        <section className="panel">
          <div className="panel-heading"><h2 className="panel-title">改进过程保持可控</h2></div>
          <div className="panel-body">
            <div className="rule-list">
              {rules.map((rule) => (
                <div key={rule.title} className="rule-item">
                  <div><p>{rule.title}</p><small>{rule.desc}</small></div>
                  <AppIcon name="shield" size={15} />
                </div>
              ))}
            </div>
            <div className="tech-panel">
              <b>Talent · 能力资产</b><br />版本管理 / 评测 / 审核 / 回滚<br />
              <span className="line">接口状态：未连接</span>
            </div>
          </div>
        </section>
      </div>

      <div className="v3-next">
        <span>版本发布与回滚均需保留审核和评测依据</span>
        <button className="btn" onClick={() => openModal("rollback")}>查看回滚方案</button>
      </div>
    </>
  );
}
