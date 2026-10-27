import AppIcon from "./AppIcon";
import { flowItems } from "../store/demo";
import { openModal } from "../store/ui";
import { goPortal, type PortalPage } from "../router";

export default function ReferenceFlow({ scope = "overview" }: { scope?: "overview" | "development" }) {
  function go(id: PortalPage) {
    goPortal(id);
  }

  function showImage() {
    openModal("original-image", {
      image: {
        path: "assets/demo-flow-reference.png",
        title: "整体演示流程图",
        file: "demo 界面流程图",
        width: 1911,
      },
    });
  }

  const overview = scope === "overview";

  return (
    <section className="panel reference-flow-panel">
      <div className="portal-flow-head">
        <div>
          <span className="v3-kicker">{overview ? "UNIFIED DEMO PORTAL" : "INCREMENTAL DEVELOPMENT WORKFLOW"}</span>
          <h2>{overview ? "Demo 演示的统一 Portal" : "存量系统增量需求开发 · 演示流程"}</h2>
          <p>按提供的演示流程图对齐，点击模块或环节进入相应页面。</p>
        </div>
        {!overview ? (
          <button className="btn btn-small" onClick={showImage}>
            <AppIcon name="expand" size={13} />查看原始流程图
          </button>
        ) : null}
      </div>

      <div className="reference-flow-scroll">
        <div className="reference-flow-grid">
          <section className="reference-pillar icode-pillar">
            <span className="reference-number">1</span>
            <a href="#/portal/platform" onClick={(e) => { e.preventDefault(); go("platform"); }}>
              <span className="reference-pillar-icon"><AppIcon name="code" size={25} /></span>
              <h3>iCode<br />功能展示</h3>
              <p>整体架构与模块入口</p>
              <span className="reference-enter">进入查看 <AppIcon name="arrow" size={12} /></span>
            </a>
            <div className="reference-support"><span>Core</span><span>Teams</span><span>Talent</span></div>
          </section>

          <section className="reference-main">
            <div className="reference-main-title">
              <span className="reference-number">2</span>
              <a href="#/portal/development" onClick={(e) => { e.preventDefault(); go("development"); }}>
                存量系统增量需求开发 <small>信用卡 Demo · 场景可替换</small>
              </a>
            </div>
            <div className="reference-stages">
              {flowItems.map((stage, index) => (
                <div key={stage.id} className="reference-stage">
                  <a href={`#/portal/${stage.id}`} onClick={(e) => { e.preventDefault(); go(stage.id as PortalPage); }}>
                    <span className="reference-stage-top">
                      <small>0{index + 1}</small>
                      <AppIcon name={stage.icon} size={18} />
                    </span>
                    <h3>{stage.title}</h3>
                    <p>{stage.sub}</p>
                    <span className="reference-enter">进入查看 <AppIcon name="arrow" size={11} /></span>
                  </a>
                  <div className="reference-support">
                    {stage.roles.map((role) => <span key={role}>{role}</span>)}
                  </div>
                </div>
              ))}
            </div>
          </section>

          <section className="reference-pillar rsi-pillar">
            <span className="reference-number">3</span>
            <a href="#/portal/evolution" onClick={(e) => { e.preventDefault(); go("evolution"); }}>
              <span className="reference-pillar-icon"><AppIcon name="spark" size={25} /></span>
              <h3>平台 RSI<br />自演进</h3>
              <p>反馈 · 评测 · 发布</p>
              <span className="reference-enter">进入查看 <AppIcon name="arrow" size={12} /></span>
            </a>
            <div className="reference-support"><span>Talent</span></div>
          </section>

          <section className="reference-pillar token-pillar">
            <span className="reference-number">4</span>
            <a href="#/portal/resources" onClick={(e) => { e.preventDefault(); go("resources"); }}>
              <span className="reference-pillar-icon"><AppIcon name="chart" size={25} /></span>
              <h3>Token<br />资源监控</h3>
              <p>用量 · 时延 · 成本</p>
              <span className="reference-enter">进入查看 <AppIcon name="arrow" size={12} /></span>
            </a>
            <div className="reference-support"><span>监控中心</span></div>
          </section>
        </div>
      </div>

      <div className="reference-flow-note">
        <span><AppIcon name="lock" size={12} /> 实施计划人工确认 → 受控开发 → 质量门禁与交付评审</span>
        <span>点击模块或环节进入对应演示页面 · 状态仅为本地 MOCK</span>
      </div>
    </section>
  );
}
