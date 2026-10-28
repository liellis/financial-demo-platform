import { useEffect, useRef, type ComponentType } from "react";
import baseCss from "./styles/styles.css?raw";
import v3Css from "./styles/v3.css?raw";
import SidebarNav from "./components/SidebarNav";
import TopBar from "./components/TopBar";
import ContextBar from "./components/ContextBar";
import StepProgress from "./components/StepProgress";
import RunRail from "./components/RunRail";
import ModalHost from "./components/ModalHost";
import { demo, useDemo } from "./store/demo";
import { ui, useUi } from "./store/ui";
import { clearFlowTimer } from "./store/flow";
import { installI18n } from "./i18n";
import type { PortalPage } from "./router";

import OverviewPage from "./pages/OverviewPage";
import DevelopmentPage from "./pages/DevelopmentPage";
import KnowledgePage from "./pages/KnowledgePage";
import SkillsPage from "./pages/SkillsPage";
import DevelopPage from "./pages/DevelopPage";
import QualityPage from "./pages/QualityPage";
import TracePage from "./pages/TracePage";
import EfficiencyPage from "./pages/EfficiencyPage";
import BusinessPage from "./pages/BusinessPage";
import EvolutionPage from "./pages/EvolutionPage";
import PlatformPage from "./pages/PlatformPage";
import CorePage from "./pages/CorePage";
import ResourcesPage from "./pages/ResourcesPage";

const pageComponents: Record<PortalPage, ComponentType> = {
  overview: OverviewPage,
  development: DevelopmentPage,
  knowledge: KnowledgePage,
  skills: SkillsPage,
  develop: DevelopPage,
  quality: QualityPage,
  trace: TracePage,
  efficiency: EfficiencyPage,
  business: BusinessPage,
  evolution: EvolutionPage,
  platform: PlatformPage,
  core: CorePage,
  resources: ResourcesPage,
};

const detailPages: PortalPage[] = [
  "knowledge",
  "skills",
  "develop",
  "quality",
  "trace",
  "efficiency",
  "business",
];

export default function PortalApp({ page }: { page: PortalPage }) {
  useDemo();
  useUi();
  const styleRef = useRef<HTMLStyleElement | null>(null);
  const isDetail = detailPages.includes(page);
  const Page = pageComponents[page];

  useEffect(() => {
    const style = document.createElement("style");
    style.dataset.portalStyles = "true";
    style.textContent = `${baseCss}\n${v3Css}`;
    document.head.appendChild(style);
    styleRef.current = style;
    document.body.classList.add("v3");
    const uninstallI18n = installI18n();
    return () => {
      uninstallI18n();
      style.remove();
      styleRef.current = null;
      document.body.classList.remove("v3", "technical", "showcase", "mobile-nav");
      document.body.style.overflow = "";
      clearFlowTimer();
    };
  }, []);

  useEffect(() => {
    document.body.classList.toggle("technical", demo.view === "technical");
    document.body.classList.toggle("showcase", demo.showcase);
    document.body.classList.toggle("mobile-nav", demo.mobileNav);
  }, [demo.view, demo.showcase, demo.mobileNav]);

  useEffect(() => {
    demo.mobileNav = false;
    window.scrollTo(0, 0);
  }, [page]);

  return (
    <>
      <a className="skip-link" href="#main">跳至主要内容</a>
      <SidebarNav page={page} />
      <div className="v3-shell">
        <TopBar page={page} />
        <div className="v3-workspace">
          <ContextBar />
          {isDetail ? <StepProgress page={page} /> : null}
          <div className={isDetail ? "v3-layout" : "v3-layout wide"}>
            <main id="main" className="v3-main">
              <Page />
              {demo.view === "technical" ? (
                <div className="tech-panel">
                  <b>执行来源 · MOCK / 本地原型</b><br />
                  run: DEMO-RUN-001 · code_revision: pending · adapter: not_connected<br />
                  真实任务、Skill 版本、事件、产物与日志将在后台接入后绑定。
                </div>
              ) : null}
            </main>
            {isDetail ? <RunRail /> : null}
          </div>
          {page === "core" ? null : (
            <footer className="v3-footer">
              <span><i className="dot" /> Demo Environment · 交互原型，尚未连接后端</span>
              <span>统一场景 · 统一运行 · 统一证据 <b>V3.3</b></span>
            </footer>
          )}
        </div>
      </div>
      <ModalHost />
      <div id="toast" role="status" aria-live="polite" className={ui.toast ? "visible" : ""}>
        {ui.toast}
      </div>
    </>
  );
}
