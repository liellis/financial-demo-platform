import { goPortal, type PortalPage } from "../router";

const steps: Array<{ id: PortalPage; name: string }> = [
  { id: "knowledge", name: "知识准备" },
  { id: "skills", name: "导入 Skills" },
  { id: "develop", name: "需求与开发" },
  { id: "quality", name: "质量验证" },
  { id: "trace", name: "追溯分析" },
];

export default function StepProgress({ page }: { page: PortalPage }) {
  return (
    <div className="v3-progress" aria-label="演示步骤">
      {steps.map((step, index) => (
        <button
          key={step.id}
          className={page === step.id ? "active" : ""}
          onClick={() => goPortal(step.id)}
        >
          <span>{index + 1}</span>
          {step.name}
          {index < steps.length - 1 ? <i /> : null}
        </button>
      ))}
    </div>
  );
}
