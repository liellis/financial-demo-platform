import { useEffect } from "react";
import AppIcon from "../components/AppIcon";
import PageIntro from "../components/PageIntro";
import { demo, useDemo } from "../store/demo";
import { openModal, showToast } from "../store/ui";
import { goPortal } from "../router";
import { formatSkillSize, loadSkillFiles, skillDownloadUrl, useSkills } from "../services/skills";
import { skillCategoryIcons } from "../skills/categories";

export default function SkillsPage() {
  useDemo();
  const skills = useSkills();

  useEffect(() => {
    loadSkillFiles();
    const refresh = () => loadSkillFiles();
    window.addEventListener("skills-updated", refresh);
    return () => window.removeEventListener("skills-updated", refresh);
  }, []);

  function next() {
    if (!demo.skillsImported) {
      showToast("请先本地导入 Skill 文件");
      return;
    }
    goPortal("develop");
  }

  return (
    <>
      <PageIntro
        kicker="02 / REUSABLE ENGINEERING SKILLS"
        title="让已有技能，成为标准研发能力"
        desc="按任务组织专业 Skills，为执行提供知识、规范和质量约束。"
      >
        <button className="btn btn-primary" onClick={() => openModal("import-skills")}>
          <AppIcon name="download" size={14} />本地导入 Skill
        </button>
      </PageIntro>

      <div className="v3-banner">
        <AppIcon name="layers" size={22} />
        <div>
          <b>本次场景能力包 <span>SDLC / 示例配置</span></b>
          <p>理解工程 → 分析需求 → 评估影响 → 增量开发 → 验证与交付</p>
        </div>
        <span className={demo.skillsImported ? "pill success" : "pill pending"}>
          {demo.skillsImported ? "已导入本地 Skill" : "待导入本地 Skill"}
        </span>
      </div>

      <section className="panel">
        <div className="panel-heading">
          <div>
            <h2 className="panel-title">SDLC Skills 清单</h2>
            <p className="panel-subtitle">按导入分类展示本地文件，可下载查看。</p>
          </div>
          <span className={skills.error ? "pill danger" : "pill neutral"}>
            {skills.error || (skills.loading ? "正在加载本地文件…" : "本地已导入文件")}
          </span>
        </div>
        {skills.error ? <p className="skill-category-error">{skills.error}</p> : null}
        <div className="skill-category-grid">
          {skills.categories.map((category) => (
            <article key={category.id} className="skill-category-card">
              <div className="skill-category-head">
                <span className="module-icon">
                  <AppIcon name={skillCategoryIcons[category.id] || "document"} size={20} />
                </span>
                <div>
                  <b>{category.name}</b>
                  <small>{category.files.length} 个文件</small>
                </div>
              </div>
              {category.files.length ? (
                <ul className="skill-category-files">
                  {category.files.map((file) => (
                    <li key={file.name}>
                      <span className="skill-category-file">
                        <code>{file.name}</code>
                        <small>{formatSkillSize(file.size)}</small>
                      </span>
                      <a
                        className="skill-category-download"
                        href={skillDownloadUrl(category.id, file.name)}
                        download={file.name}
                      >
                        <AppIcon name="download" size={13} />下载
                      </a>
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="skill-category-empty">暂无导入文件</p>
              )}
            </article>
          ))}
        </div>
      </section>

      <div className="callout blue">
        <AppIcon name="help" size={16} />
        <span>导入的文件将写入项目 Skill 对应分类目录，不会自动安装或执行。</span>
      </div>

      <div className="v3-next">
        <span>复用专业知识与规范，减少每次任务重新组织上下文</span>
        <button className="btn btn-primary" onClick={next}>
          进入需求与 AI 开发 <AppIcon name="arrow" size={13} />
        </button>
      </div>
    </>
  );
}
