export interface SkillCategoryDefinition {
  id: string;
  name: string;
  folder: string;
}

export const skillCategories: SkillCategoryDefinition[] = [
  { id: "requirement-analysis", name: "需求分析", folder: "需求分析" },
  { id: "existing-system-understanding", name: "存量理解", folder: "存量理解" },
  { id: "impact-assessment", name: "影响评估", folder: "影响评估" },
  { id: "controlled-development", name: "受控开发", folder: "受控开发" },
  { id: "test-validation", name: "测试验证", folder: "测试验证" },
  { id: "delivery-traceability", name: "交付追溯", folder: "交付追溯" },
];

export const skillCategoryIcons: Record<string, string> = {
  "requirement-analysis": "document",
  "existing-system-understanding": "graph",
  "impact-assessment": "branch",
  "controlled-development": "code",
  "test-validation": "shield",
  "delivery-traceability": "folder",
};
