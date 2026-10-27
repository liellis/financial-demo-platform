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
  "resources",
] as const;

export type PortalPage = (typeof portalPages)[number];

const PORTAL_PREFIX = "#/portal";
const STUDIO_PREFIX = "#/studio";

export function portalPage(hash: string): PortalPage | null {
  if (!hash || hash === "#" || hash === "#/") return "overview";
  if (hash === STUDIO_PREFIX || hash.startsWith(`${STUDIO_PREFIX}/`)) return null;
  if (!hash.startsWith(PORTAL_PREFIX)) return null;
  const match = /^#\/portal(?:\/([\w-]+))?\/?$/.exec(hash);
  if (!match) return null;
  const segment = match[1] || "overview";
  if (segment === "projects") return null;
  const candidate = segment as PortalPage;
  return portalPages.includes(candidate) ? candidate : "overview";
}

export function goPortal(page: PortalPage) {
  window.location.hash = `/portal/${page}`;
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
