import AppIcon from "../components/AppIcon";
import PageIntro from "../components/PageIntro";
import ReferenceFlow from "../components/ReferenceFlow";
import { goPortal } from "../router";

const values = [
  { icon: "chart", title: "研发生产力", desc: "专业 Skills 串联需求、实施与验证" },
  { icon: "layers", title: "存量资产复用", desc: "让业务知识、代码与测试成为任务上下文" },
  { icon: "shield", title: "质量与安全", desc: "以变更边界、门禁和证据贯穿全流程" },
];

export default function OverviewPage() {
  return (
    <>
      <PageIntro
        kicker="DEMO PORTAL / OVERVIEW"
        title="从业务需求到存量资产，理解平台如何支撑复用、研发和可信交付"
        desc="演示统一场景、统一运行与统一证据的完整路径。"
      >
        <button className="btn btn-primary" onClick={() => goPortal("development")}>
          进入存量系统开发 <AppIcon name="arrow" size={14} />
        </button>
      </PageIntro>

      <ReferenceFlow scope="overview" />

      <div className="portal-overview-values">
        {values.map((value) => (
          <div key={value.title}>
            <AppIcon name={value.icon} size={20} />
            <span>
              <b>{value.title}</b>
              <p>{value.desc}</p>
            </span>
          </div>
        ))}
      </div>
    </>
  );
}
