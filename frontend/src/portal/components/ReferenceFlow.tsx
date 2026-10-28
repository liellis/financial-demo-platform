import AppIcon from "./AppIcon";
import { flowItems } from "../store/demo";
import { openModal } from "../store/ui";
import { goPortal, type PortalPage } from "../router";

const TALENT_KNOWLEDGE_URL =
  "http://127.0.0.1:45980/talents/?role=owner#/projects?project=carddemo&id=a-cg-java";

const TALENT_RSI_URL =
  "http://127.0.0.1:45980/talents/?role=owner#/projects?project=carddemo&id=a-traj";

const TEAMS_TOKENS_URL =
  "http://127.0.0.1:45970/teams?demo-role=owner#/home?tab=tokens";

const TEAMS_TRACE_URL =
  "http://127.0.0.1:45970/teams?demo-role=owner#/team-work";

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
            {!overview ? <span className="reference-number">1</span> : null}
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
              {!overview ? <span className="reference-number">2</span> : null}
              <a href="#/portal/development" onClick={(e) => { e.preventDefault(); go("development"); }}>
                存量系统增量需求开发 <small>信用卡 Demo · 场景可替换</small>
              </a>
            </div>
            <div className="reference-stages">
              {flowItems.map((stage, index) => {
                const externalUrl =
                  overview && stage.id === "knowledge"
                    ? TALENT_KNOWLEDGE_URL
                    : overview && stage.id === "trace"
                      ? TEAMS_TRACE_URL
                      : "";
                return (
                <div key={stage.id} className="reference-stage">
                  <a
                    href={externalUrl || `#/portal/${stage.id}`}
                    target={externalUrl ? "_blank" : undefined}
                    rel={externalUrl ? "noopener noreferrer" : undefined}
                    onClick={(e) => {
                      if (externalUrl) return;
                      e.preventDefault();
                      go(stage.id as PortalPage);
                    }}
                  >
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
                );
              })}
            </div>
          </section>

          <section className="reference-pillar rsi-pillar">
            {!overview ? <span className="reference-number">3</span> : null}
            <a
              href={overview ? TALENT_RSI_URL : "#/portal/evolution"}
              target={overview ? "_blank" : undefined}
              rel={overview ? "noopener noreferrer" : undefined}
              onClick={(e) => {
                if (overview) return;
                e.preventDefault();
                go("evolution");
              }}
            >
              <span className="reference-pillar-icon"><AppIcon name="spark" size={25} /></span>
              <h3>平台 RSI<br />自演进</h3>
              <p>反馈 · 评测 · 发布</p>
              <span className="reference-enter">进入查看 <AppIcon name="arrow" size={12} /></span>
            </a>
            <div className="reference-support"><span>Talent</span></div>
          </section>

          <section className="reference-pillar token-pillar">
            {!overview ? <span className="reference-number">4</span> : null}
            <a
              href={overview ? TEAMS_TOKENS_URL : "#/portal/resources"}
              target={overview ? "_blank" : undefined}
              rel={overview ? "noopener noreferrer" : undefined}
              onClick={(e) => {
                if (overview) return;
                e.preventDefault();
                go("resources");
              }}
            >
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
