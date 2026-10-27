import AppIcon from "./AppIcon";
import { demo, useDemo } from "../store/demo";
import { openModal } from "../store/ui";
import { goPortal } from "../router";

export default function RunRail() {
  useDemo();

  const qualityLines = [
    { label: "实施计划", value: demo.approved ? "模拟已确认" : "待人工确认", green: demo.approved },
    { label: "测试与回归", value: demo.verified ? "示例已通过" : "待执行", green: demo.verified },
    { label: "安全 / DFX", value: demo.verified ? "示例通过 / 待实测" : "待真实扫描", green: demo.verified },
    { label: "交付评审", value: demo.reviewed ? "模拟已审核" : "待审核", green: demo.reviewed },
  ];

  const recentEvents = demo.events.length
    ? demo.events.slice(-3).reverse().map((event) => ({ text: event.summary, source: "本地操作" }))
    : [
        { text: "演示工作空间已就绪", source: "预设状态" },
        { text: "等待绑定代码与知识索引", source: "待接入" },
        { text: "Skills 与执行平台待连接", source: "待接入" },
      ];

  return (
    <aside className="run-rail">
      <div className="rail-heading">
        <h2>统一运行状态</h2>
        <span className="live-label">MOCK</span>
      </div>
      <div className="run-status">
        <span className="run-status-icon">
          <AppIcon name={demo.running ? "refresh" : "play"} size={21} />
        </span>
        <div>
          <b>{demo.running ? "演练运行中" : demo.approved ? "方案已模拟确认" : "Ready"}</b>
          <small>{demo.running ? "仅更新本地页面状态" : "等待演示操作 · 非实际任务"}</small>
        </div>
      </div>
      <dl className="rail-meta">
        <dt>运行标识</dt><dd>DEMO-RUN-001</dd>
        <dt>执行环境</dt><dd>Demo Environment</dd>
        <dt>代码基线</dt><dd className="muted">待绑定真实版本</dd>
        <dt>数据来源</dt><dd>本地预设 / MOCK</dd>
      </dl>
      <div className="rail-divider" />
      <div className="rail-title">
        <b>质量与安全</b>
        <button aria-label="查看质量与安全" onClick={() => goPortal("quality")}>
          <AppIcon name="arrow" size={13} />
        </button>
      </div>
      {qualityLines.map((line) => (
        <div key={line.label} className="rail-line">
          <span>{line.label}</span>
          <small className={line.green ? "green" : ""}>{line.value}</small>
        </div>
      ))}
      <div className="rail-divider" />
      <div className="rail-title">
        <b>资源用量</b>
        <button aria-label="查看资源监控" onClick={() => goPortal("resources")}>
          <AppIcon name="arrow" size={13} />
        </button>
      </div>
      <div className="rail-usage">
        <div><b>—</b><small>Tokens</small></div>
        <div><b>—</b><small>运行耗时</small></div>
      </div>
      <p className="rail-note">尚未接入真实资源采集</p>
      <div className="rail-divider" />
      <div className="rail-title">
        <b>运行事件</b>
        <button onClick={() => openModal("events")}>
          全部 <AppIcon name="arrow" size={11} />
        </button>
      </div>
      <div className="rail-events">
        {recentEvents.map((event, index) => (
          <div key={index}>
            <i />
            <p>{event.text}<small>{event.source}</small></p>
          </div>
        ))}
      </div>
      <button className="rail-source" onClick={() => openModal("integrations")}>
        <AppIcon name="plug" size={14} /> 查看平台接入状态 <AppIcon name="chevron" size={12} />
      </button>
    </aside>
  );
}
