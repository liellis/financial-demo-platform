import PageIntro from "../components/PageIntro";
import { openModal } from "../store/ui";

const forms = [
  { id: "cli", name: "CLI", path: "assets/CLI.png", file: "CLI.png", width: 1886 },
  { id: "ide", name: "IDE", path: "assets/IDE.png", file: "IDE.png", width: 3420 },
  { id: "gui", name: "GUI", path: "assets/GUI.png", file: "GUI.png", width: 2998 },
];

export default function CorePage() {
  function show(form: (typeof forms)[number]) {
    openModal("original-image", {
      image: {
        path: form.path,
        title: `iCode-Core ${form.name}`,
        file: form.file,
        width: form.width,
      },
    });
  }

  return (
    <>
      <PageIntro
        kicker="01 / ICODE-CORE"
        title="iCode-Core 三种使用形态"
        desc="CLI、IDE 与 GUI 共享同一套编码执行能力；点击图片可放大查看。"
      />

      <div className="core-forms">
        {forms.map((form) => (
          <div key={form.id} className="core-form-item">
            <button
              className="core-form-card"
              style={{ backgroundImage: `url(${form.path})` }}
              aria-label={`放大 ${form.name} 图片`}
              onClick={() => show(form)}
            />
            <span className="core-form-name">{form.name}</span>
          </div>
        ))}
      </div>
    </>
  );
}
