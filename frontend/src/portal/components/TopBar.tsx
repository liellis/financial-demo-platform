import AppIcon from "./AppIcon";
import { demo, pageLabels, useDemo } from "../store/demo";
import { openModal } from "../store/ui";
import { toggleLang, useI18n } from "../i18n";
import type { PortalPage } from "../router";

const sections: PortalPage[] = ["overview", "platform", "evolution", "resources"];

export default function TopBar({ page }: { page: PortalPage }) {
  useDemo();
  const i18n = useI18n();
  const section = page === "core" ? "platform" : sections.includes(page) ? page : "development";
  const pageLabel = pageLabels[page] || "演示总览";

  return (
    <header className="v3-top">
      <div className="v3-crumb">
        <button
          className="icon-button mobile-menu"
          aria-label="展开导航"
          onClick={() => (demo.mobileNav = !demo.mobileNav)}
        >
          <AppIcon name="menu" size={19} />
        </button>
        <span>Demo Portal</span>
        <AppIcon name="chevron" size={12} />
        <b>{section === "development" ? "存量系统增量开发" : pageLabel}</b>
      </div>
      <div className="v3-tools">
        <button
          className="icon-button lang-toggle"
          aria-label={i18n.lang === "en" ? "Switch to Chinese" : "切换语言"}
          onClick={toggleLang}
        >
          <b>{i18n.lang === "en" ? "中" : "EN"}</b>
        </button>
        <button className="icon-button" aria-label="演示指南" onClick={() => openModal("guide")}>
          <AppIcon name="help" size={18} />
        </button>
        <button
          className="icon-button"
          aria-label="切换主秀布局"
          onClick={() => (demo.showcase = !demo.showcase)}
        >
          <AppIcon name="expand" size={18} />
        </button>
        <span className="v3-user">DE</span>
      </div>
    </header>
  );
}
