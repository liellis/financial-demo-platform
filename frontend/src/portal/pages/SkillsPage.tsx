import AppIcon from "../components/AppIcon";
import PageIntro from "../components/PageIntro";
import { demo, skillRows, useDemo } from "../store/demo";
import { openModal, showToast } from "../store/ui";
import { goPortal } from "../router";

export default function SkillsPage() {
  useDemo();

  function next() {
    if (!demo.skillsImported) {
      showToast("请先导入本地示例 Skills 清单");
      return;
    }
    goPortal("develop");
  }

  return (
    <>
      <PageIntro
        kicker="02 / REUSABLE ENGINEERING SKILLS"
        title="把已有技能，变成标准研发动作"
        desc="按任务组织专业 Skills，为执行提供知识、规范和质量约束。"
      >
        <button className="btn btn-primary" onClick={() => openModal("import-skills")}>
          <AppIcon name="download" size={14} />{demo.skillsImported ? "查看本地清单" : "导入本地示例"}
        </button>
      </PageIntro>

      <div className="v3-banner">
        <AppIcon name="layers" size={22} />
        <div>
          <b>本轮场景能力包 <span>SDLC / 示例能力</span></b>
          <p>需求工作 → 存量理解 → 影响评估 → 受控开发 → 验证与交付</p>
        </div>
        <span className={demo.skillsImported ? "pill success" : "pill pending"}>
          {demo.skillsImported ? "已加入本地演示" : "待导入示例"}
        </span>
      </div>

      <section className="panel">
        <div className="panel-heading">
          <h2 className="panel-title">SDLC Skills 清单</h2>
          <span className="pill neutral">能力示例 · 版本待映射</span>
        </div>
        <div className="v3-skill-grid">
          {skillRows.map((skill) => (
            <button key={skill.skill} onClick={() => openModal("skill-detail", { skill: skill.skill })}>
              <span className="module-icon"><AppIcon name={skill.icon} size={20} /></span>
              <b>{skill.name}</b>
              <code>{skill.skill}</code>
              <p>{skill.desc}</p>
              <span className="text-link">查看能力详情 <AppIcon name="arrow" size={12} /></span>
            </button>
          ))}
        </div>
      </section>

      <div className="callout blue">
        <AppIcon name="help" size={16} />
        <span>此处只维护本地演示清单；真实 Skill 文件、版本与执行命令将在平台接入时映射。</span>
      </div>

      <div className="v3-next">
        <span>把专业知识与规范，变成每个任务可组织的能力</span>
        <button className="btn btn-primary" onClick={next}>
          进入需求与 AI 开发 <AppIcon name="arrow" size={13} />
        </button>
      </div>
    </>
  );
}
