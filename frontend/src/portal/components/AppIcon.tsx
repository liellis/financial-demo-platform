import { icons } from "../icons";

export default function AppIcon({
  name = "document",
  size = 18,
}: {
  name?: string;
  size?: number | string;
}) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.65"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      dangerouslySetInnerHTML={{ __html: icons[name] || icons.document }}
    />
  );
}
