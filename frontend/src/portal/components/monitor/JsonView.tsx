function toText(value: unknown): string {
  if (value === null || value === undefined || value === "") return "";
  if (typeof value === "string") {
    try {
      return JSON.stringify(JSON.parse(value), null, 2);
    } catch {
      return value;
    }
  }
  return JSON.stringify(value, null, 2);
}

export default function JsonView({ value }: { value: unknown }) {
  const text = toText(value);

  return (
    <div className="mon-json">
      {text ? <pre>{text}</pre> : <div className="mon-traj-muted">无内容</div>}
    </div>
  );
}
