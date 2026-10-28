import AppIcon from "./AppIcon";
import { demo, isCompleted, useDemo, workflowItems } from "../store/demo";
import { openModal } from "../store/ui";
import { exitPortal, goPortal, type PortalPage } from "../router";

const navItems = [
  { id: "platform", number: "01", name: "iCode 整体功能", desc: "能力全景与模块入口" },
  { id: "development", number: "02", name: "存量系统增量开发", desc: "完整研发流程与工作区" },
  { id: "evolution", number: "03", name: "RSI 自演进", desc: "反馈驱动的版本改进" },
  { id: "resources", number: "04", name: "Token 资源监控", desc: "用量与时延成本" },
  { id: "traces", number: "05", name: "会话轨迹", desc: "调用树 · 节点详情" },
  { id: "metrics", number: "06", name: "监控指标", desc: "OTel 指标 · 轨迹效率" },
];

const tabPages: PortalPage[] = ["overview", "platform", "evolution", "resources", "traces", "metrics"];

const TOKEN_MONITOR_URL = "http://127.0.0.1:45970/teams?demo-role=owner#/home?tab=tokens";

function sectionOf(page: PortalPage): string {
  if (page === "core") return "platform";
  if (tabPages.includes(page)) return page;
  return "development";
}

export default function SidebarNav({ page }: { page: PortalPage }) {
  useDemo();
  const section = sectionOf(page);

  function go(id: PortalPage) {
    demo.mobileNav = false;
    goPortal(id);
  }

  function goTokenMonitor() {
    demo.mobileNav = false;
    window.location.href = TOKEN_MONITOR_URL;
  }

  function goPlatformHome() {
    demo.platformTab = "overview";
    go("platform");
  }

  function openStudio() {
    try {
      sessionStorage.setItem("bos.demo.autologin", "1");
    } catch {
      /* session storage may be unavailable */
    }
    exitPortal();
  }

  return (
    <aside className="sidebar v3-sidebar">
      <a
        className="v3-brand"
        href="#/portal/overview"
        onClick={(event) => {
          event.preventDefault();
          go("overview");
        }}
      >
        <span className="brand-logo">
          <AppIcon name="code" size={22} />
        </span>
        <div>
          <b>
            AI-Coding <em>Demo Portal</em>
          </b>
          <small>BANKING AI SOFTWARE ENGINEERING</small>
        </div>
      </a>

      <div className="portal-nav-scroll">
        <button
          className={section === "overview" ? "portal-overview-nav active" : "portal-overview-nav"}
          onClick={() => go("overview")}
        >
          <AppIcon name="grid" size={17} />
          <b>演示总览</b>
          <span>协作入口</span>
        </button>

        <div className="space-label portal-nav-label">
          DEMO MODULES <span>功能导航</span>
        </div>

        <nav aria-label="一级功能导航">
          {navItems.map((item) => (
            <div key={item.id} className="portal-nav-group">
              <button
                className={section === item.id ? "theme-nav selected" : "theme-nav"}
                aria-current={page === item.id ? "page" : undefined}
                onClick={() =>
                  item.id === "platform"
                    ? goPlatformHome()
                    : item.id === "resources"
                      ? goTokenMonitor()
                      : go(item.id as PortalPage)
                }
              >
                <span className="theme-number">{item.number}</span>
                <div>
                  <b>{item.name}</b>
                  <small>{item.desc}</small>
                </div>
                {item.id === "development" ? (
                  <AppIcon name={section === item.id ? "down" : "chevron"} size={12} />
                ) : section === item.id ? (
                  <span className="selected-dot" />
                ) : null}
              </button>

              {item.id === "development" && section === "development" ? (
                <nav className="portal-subnav" aria-label="存量系统增量开发二级导航">
                  {workflowItems.map((workflow, index) => (
                    <button
                      key={workflow.id}
                      className={
                        page === workflow.id || (page === "business" && workflow.id === "efficiency")
                          ? "portal-subitem active"
                          : "portal-subitem"
                      }
                      onClick={() => go(workflow.id as PortalPage)}
                    >
                      <span>02.{index + 1}</span>
                      <b>{workflow.name}</b>
                      {isCompleted(workflow.id) ? <AppIcon name="check" size={11} /> : null}
                    </button>
                  ))}
                </nav>
              ) : null}
            </div>
          ))}
        </nav>
      </div>

      <div className="v3-sidebar-bottom">
        <button className="project-management-nav" onClick={openStudio} title="返回 Bank Ontology Studio 首页">
          <AppIcon name="folder" size={16} /> 代码工程管理
          <AppIcon name="arrow" size={13} />
        </button>
        <button onClick={() => openModal("integrations")}>
          <AppIcon name="plug" size={15} /> 演示接入 <span><i className="dot" /> 待接入</span>
        </button>
        <div className="v3-profile">
          <span className="avatar">DE</span>
          <div>
            <b>演示工作空间</b>
            <small>原型环境 · 合成演示</small>
          </div>
          <button aria-label="演示帮助" onClick={() => openModal("guide")}>
            <AppIcon name="help" size={16} />
          </button>
        </div>
      </div>
    </aside>
  );
}
