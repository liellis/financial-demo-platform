import { useEffect, useState } from "react";

export const portalPages = [
  "overview",
  "development",
  "knowledge",
  "skills",
  "develop",
  "quality",
  "trace",
  "efficiency",
  "business",
  "evolution",
  "platform",
  "core",
  "resources",
  "traces",
  "metrics",
] as const;

export type PortalPage = (typeof portalPages)[number];

const PORTAL_PREFIX = "#/portal";
const STUDIO_PREFIX = "#/studio";

export function portalPage(hash: string): PortalPage | null {
  if (!hash || hash === "#" || hash === "#/") return "overview";
  if (hash === STUDIO_PREFIX || hash.startsWith(`${STUDIO_PREFIX}/`)) return null;
  if (!hash.startsWith(PORTAL_PREFIX)) return null;
  const match = /^#\/portal(?:\/([\w-]+))?(?:\/([\w-]+))?(?:\?[^#]*)?\/?$/.exec(hash);
  if (!match) return null;
  const segment = match[1] || "overview";
  if (segment === "projects") return null;
  const candidate = segment as PortalPage;
  return portalPages.includes(candidate) ? candidate : "overview";
}

/** 会话轨迹详情：#/portal/traces/<traceId>?session=xxx */
export function portalTraceId(hash: string): string | null {
  const match = /^#\/portal\/traces\/([\w-]+)/.exec(hash);
  return match ? match[1] : null;
}

/** 读取 hash 中的查询参数（hash 路由下 window.location.search 不可用） */
export function hashQuery(hash: string, key: string): string {
  const index = hash.indexOf("?");
  if (index < 0) return "";
  return new URLSearchParams(hash.slice(index + 1)).get(key) || "";
}

export function goTraceDetail(traceId: string, sessionId?: string) {
  window.location.hash = `/portal/traces/${traceId}${sessionId ? `?session=${encodeURIComponent(sessionId)}` : ""}`;
  window.scrollTo(0, 0);
}

export function goPortal(page: PortalPage) {
  window.location.hash = `/portal/${page}`;
  window.scrollTo(0, 0);
}

export function exitPortal() {
  window.location.hash = "/studio";
  window.scrollTo(0, 0);
}

export function useHash(): string {
  const [hash, setHash] = useState(() => window.location.hash);
  useEffect(() => {
    const onChange = () => setHash(window.location.hash);
    window.addEventListener("hashchange", onChange);
    return () => window.removeEventListener("hashchange", onChange);
  }, []);
  return hash;
}
