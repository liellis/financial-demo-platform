import AppIcon from "./AppIcon";
import { demo, scenarioName, useDemo } from "../store/demo";
import { openModal } from "../store/ui";

export default function ContextBar() {
  useDemo();

  return (
    <section className="v3-context">
      <div className="scenario-icon">
        <AppIcon name="credit" size={22} />
      </div>
      <div className="scenario-title">
        <span>
          当前演示场景 <i>{demo.scenario === "card" ? "CR-001" : "DEMO-001"} · 示例</i>
        </span>
        <b>{scenarioName()}</b>
      </div>
      <button className="text-link" onClick={() => openModal("scenario")}>
        切换场景 <AppIcon name="down" size={12} />
      </button>
      <div className="context-divider" />
      <div className="run-context">
        <span>DemoRun ID</span>
        <button onClick={() => openModal("events")}>
          DEMO-RUN-001 <AppIcon name="external" size={11} />
        </button>
      </div>
      <span className="v3-ready">
        <i className="dot" />
        {demo.running ? "演练中" : demo.reviewed ? "模拟评审完成" : "Ready · 本地原型"}
      </span>
    </section>
  );
}
