import AppIcon from "../components/AppIcon";
import PageIntro from "../components/PageIntro";
import ReferenceFlow from "../components/ReferenceFlow";
import { workflowItems } from "../store/demo";
import { goPortal, type PortalPage } from "../router";

export default function DevelopmentPage() {
  return (
    <>
      <PageIntro
        kicker="02 / EXISTING SYSTEM DEVELOPMENT"
        title="存量系统开发需求"
        desc="先查看研发流程，再进入下方或左侧的二级菜单开展演示。"
      >
        <button className="btn" onClick={() => goPortal("overview")}>
          <AppIcon name="folder" size={14} />返回演示总览
        </button>
      </PageIntro>

      <ReferenceFlow scope="development" />

      <div className="portal-secondary-heading">
        <h2>研发流程入口</h2>
        <span>与左侧二级菜单一一对应</span>
      </div>
      <div className="portal-workspaces">
        {workflowItems.map((workspace, index) => (
          <a
            key={workspace.id}
            href={`#/portal/${workspace.id}`}
            onClick={(event) => {
              event.preventDefault();
              goPortal(workspace.id as PortalPage);
            }}
          >
            <span className="portal-workspace-num">02.{index + 1}</span>
            <span className="module-icon"><AppIcon name={workspace.icon} size={18} /></span>
            <div>
              <h3>{workspace.name}</h3>
              <p>{workspace.description}</p>
            </div>
            <AppIcon name="arrow" size={14} />
          </a>
        ))}
      </div>
    </>
  );
}
