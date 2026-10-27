import type { ReactNode } from "react";

export default function PageIntro({
  kicker = "",
  title = "",
  desc = "",
  children,
}: {
  kicker?: string;
  title?: string;
  desc?: string;
  children?: ReactNode;
}) {
  return (
    <div className="v3-intro">
      <div>
        <span className="v3-kicker">{kicker}</span>
        <h1>{title}</h1>
        <p>{desc}</p>
      </div>
      <div className="heading-actions">{children}</div>
    </div>
  );
}
