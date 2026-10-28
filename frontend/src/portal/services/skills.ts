import { createStore, useStoreVersion } from "../store/store";
import { skillCategories, type SkillCategoryDefinition } from "../skills/categories";

export interface SkillFile {
  name: string;
  size: number;
  updatedAt?: number;
}

export interface SkillCategory extends SkillCategoryDefinition {
  files: SkillFile[];
}

export interface SkillsState {
  ready: boolean;
  loading: boolean;
  error: string;
  categories: SkillCategory[];
}

function emptyCategories(): SkillCategory[] {
  return skillCategories.map((category) => ({ ...category, files: [] }));
}

const skillsStore = createStore<SkillsState>({
  ready: false,
  loading: false,
  error: "",
  categories: emptyCategories(),
});

export const skills = skillsStore.state;

export function useSkills(): SkillsState {
  useStoreVersion(skillsStore);
  return skills;
}

export async function loadSkillFiles(): Promise<void> {
  skills.loading = true;
  try {
    const response = await fetch("/api/skills/files");
    const data = await response.json();
    if (!response.ok || !data.ok) throw new Error(data.error || "LOAD_FAILED");
    skills.categories = skillCategories.map((category) => {
      const found = (data.categories || []).find((item: { id: string }) => item.id === category.id);
      return { ...category, files: found?.files || [] };
    });
    skills.error = "";
  } catch {
    skills.categories = emptyCategories();
    skills.error = "加载本地文件失败";
  } finally {
    skills.loading = false;
    skills.ready = true;
  }
}

export function skillDownloadUrl(categoryId: string, filename: string): string {
  return `/api/skills/download?category=${encodeURIComponent(categoryId)}&filename=${encodeURIComponent(filename)}`;
}

export function formatSkillSize(bytes: number): string {
  if (!bytes) return "0 B";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

export function notifySkillsUpdated(): void {
  window.dispatchEvent(new CustomEvent("skills-updated"));
}
