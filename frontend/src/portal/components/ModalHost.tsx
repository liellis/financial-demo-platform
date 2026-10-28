import { useEffect, useRef, useState, type ChangeEvent } from "react";
import AppIcon from "./AppIcon";
import { assets, demo, resetDemoState, scenarioName, useDemo } from "../store/demo";
import { confirmApproval, confirmReview, startDemo } from "../store/flow";
import { closeModal, openModal, showToast, ui, useUi } from "../store/ui";
import { goPortal } from "../router";
import { formatSkillSize } from "../services/skills";
import { skillCategories } from "../skills/categories";

const drawerTypes = ["evidence", "integrations", "skills", "events", "test-report"];

const scenarioOptions = [
  { id: "generic", icon: "folder", name: "存量业务规则增量扩展", desc: "业务无关框架 · 待绑定示例工程与增量需求" },
  { id: "card", icon: "credit", name: "信用卡消费返现增量开发", desc: "设计文档样例 · 白金客户额外 1% 返现 · 前端示例" },
];

const integrations = [
  { name: "iCode", desc: "代码修改、工具调用与测试产物" },
  { name: "Agent Teams", desc: "需求、设计与工程任务协作" },
  { name: "Talent", desc: "Skill 版本、评测与能力资产" },
  { name: "CodeGraph / 知识工程", desc: "代码关系、业务规则与影响分析" },
  { name: "质量与监控平台", desc: "测试、安全、模型与执行记录" },
  { name: "银行 Demo 业务系统", desc: "真实构建后的业务验收入口" },
];

const skillGroups = [
  { num: "01", name: "知识检索与工程理解", platform: "CodeGraph / 知识工程", desc: "业务规则、相关代码、已有测试" },
  { num: "02", name: "需求分析与实施设计", platform: "Agent Teams", desc: "验收条件、变更边界、SDD 计划" },
  { num: "03", name: "编码、规范与增量修改", platform: "iCode / Talent", desc: "已批准范围、资产引用、开发规范" },
  { num: "04", name: "质量验证与评审准备", platform: "测试 / 安全工具", desc: "新增及回归用例、问题处理、报告" },
];

const reportChain = [
  "需求验收条件 → 对应用例",
  "保护规则 → 历史回归",
  "发现问题 → 修复与复验",
  "检查结果 → 门禁与人工审核",
];

export default function ModalHost() {
  const current = useUi();
  useDemo();
  const [imageFull, setImageFull] = useState(false);
  const [skillCategory, setSkillCategory] = useState("");
  const [skillFile, setSkillFile] = useState<File | null>(null);
  const [uploadingSkill, setUploadingSkill] = useState(false);
  const [skillError, setSkillError] = useState("");
  const [uploadedSkillPath, setUploadedSkillPath] = useState("");
  const [importedSkills, setImportedSkills] = useState<{ path: string; size: number }[]>([]);
  const skillFileInput = useRef<HTMLInputElement | null>(null);

  const type = current.modal?.type || "";
  const payload = current.modal?.payload || {};
  const isDrawer = drawerTypes.includes(type);

  useEffect(() => {
    setImageFull(false);
    setSkillCategory("");
    setSkillFile(null);
    setSkillError("");
    setUploadedSkillPath("");
    if (skillFileInput.current) skillFileInput.current.value = "";
  }, [type]);

  const eventList = demo.events.length
    ? [...demo.events].reverse()
    : [
        { sequence: 1, time: "示例", type: "KNOWLEDGE_REFERENCED", summary: "形成业务、代码与测试资产关联（预设展示内容）" },
        { sequence: 2, time: "示例", type: "IMPACT_PROPOSED", summary: "形成影响分析与待确认事项（预设展示内容）" },
      ];

  const evidenceAssetId: string | undefined = payload.id;
  const asset = assets.find((item) => item.id === evidenceAssetId);
  let evidenceTitle = asset?.name || "研发任务关联证据";
  let evidenceDesc = asset?.desc || "展示产出物、工程版本、执行来源与关联关系的详情样式。";
  if (evidenceAssetId?.startsWith("gate-")) {
    evidenceTitle = ["需求确认门禁", "设计评估门禁", "构建检查门禁", "测试验证门禁", "安全检查门禁", "人工评审门禁"][Number(evidenceAssetId.split("-")[1])];
    evidenceDesc = "门禁的范围、验证结果与审核版本，保持独立可追溯。";
  }
  if (evidenceAssetId?.startsWith("trace-")) {
    evidenceTitle = ["结构化需求", "业务规则引用", "代码位置", "提交与变更", "测试结果", "质量门禁", "审核记录"][Number(evidenceAssetId.split("-")[1])];
    evidenceDesc = "该节点关联到同一个 DemoRun；真实文件、版本和报告等待后续平台接入。";
  }

  const head = (() => {
    switch (type) {
      case "evidence":
        return { kicker: "EVIDENCE EXPLORER", title: evidenceTitle, desc: evidenceDesc };
      case "approval":
        return { kicker: "PLAN REVIEW", title: "先确认方案，再执行变更", desc: "此窗口演示方案审核体验，不创建真实平台审批。" };
      case "scenario":
        return { kicker: "SCENARIO REGISTRY", title: "选择演示场景", desc: "演示框架可复用，工程和业务规则通过配置接入。" };
      case "integrations":
        return { kicker: "PLATFORM ADAPTERS", title: "已有能力，统一接入", desc: "当前是本地高保真原型，以下平台均未连接。" };
      case "skills":
        return { kicker: "SKILL ORCHESTRATION", title: "平台如何组织已有 Skills", desc: "能力名称是展示分类；实际 Skill 名称、版本及命令待映射。" };
      case "guide":
        return { kicker: "PRESENTATION GUIDE", title: "先看懂平台，再深入工程", desc: "建议用一项增量任务，串起三个关键价值。" };
      case "events":
        return { kicker: "RUN EVENT STREAM", title: "任务事件与来源", desc: "刷新后本地操作记录不保留；接入后由平台持久化并支持断线恢复。" };
      case "test-report":
        return { kicker: "QUALITY EVIDENCE", title: "示例验证报告", desc: "此报告用于评审布局与追溯交互，不代表真实测试运行。" };
      case "review":
        return { kicker: "HUMAN REVIEW", title: "确认示例交付评审", desc: "这里只更新本地演练，不会批准真实代码合并或发布。" };
      case "import-skills":
        return { kicker: "LOCAL SKILL IMPORT", title: "本地导入 Skill", desc: "选择分类并上传文件，文件将写入项目 Skill 对应分类目录。" };
      case "skill-detail":
        return { kicker: "MOCK / PRESENTATION PROTOTYPE", title: payload.skill || "Skill 详情", desc: "能力名称为展示示例，真实版本与来源待映射。" };
      case "original-image":
        return { kicker: "PROVIDED ORIGINAL IMAGE", title: payload.image?.title || "原始图片", desc: `来源：${payload.image?.file || ""}` };
      case "resource-method":
        return { kicker: "MOCK / PRESENTATION PROTOTYPE", title: "资源指标统计口径", desc: "正式数据应关联调用 ID、DemoRun 与采集来源。" };
      case "rollback":
        return { kicker: "MOCK / PRESENTATION PROTOTYPE", title: "版本回滚与运行观察", desc: "当前无真实版本发布，以下为控制流程。" };
      case "evolution-source":
        return { kicker: "MOCK / PRESENTATION PROTOTYPE", title: "候选来源", desc: "示例流程，不引用真实运行反馈。" };
      case "evolution-evaluation":
        return { kicker: "MOCK / PRESENTATION PROTOTYPE", title: "评测示例", desc: "真实评测结果尚未导入。" };
      default:
        return { kicker: "MOCK", title: "原型详情", desc: "" };
    }
  })();

  function approve() {
    confirmApproval();
    closeModal();
  }

  function beginDemo() {
    closeModal();
    startDemo();
    goPortal("overview");
  }

  function finishReview() {
    confirmReview();
    closeModal();
  }

  function onSkillFileChange(event: ChangeEvent<HTMLInputElement>) {
    setSkillFile(event.target.files?.[0] || null);
    setSkillError("");
    setUploadedSkillPath("");
  }

  async function uploadSkill() {
    setSkillError("");
    setUploadedSkillPath("");
    if (!skillCategory) {
      setSkillError("请选择分类后再上传");
      return;
    }
    if (!skillFile) {
      setSkillError("请选择要导入的文件");
      return;
    }
    setUploadingSkill(true);
    try {
      const url = `/api/skills/upload?category=${encodeURIComponent(skillCategory)}&filename=${encodeURIComponent(skillFile.name)}`;
      const response = await fetch(url, {
        method: "POST",
        headers: { "Content-Type": "application/octet-stream" },
        body: skillFile,
      });
      let data: { ok?: boolean; error?: string; path?: string; size?: number } = {};
      try {
        data = await response.json();
      } catch {
        data = {};
      }
      if (!response.ok || !data.ok) {
        const messages: Record<string, string> = {
          INVALID_CATEGORY: "请选择分类后再上传",
          INVALID_FILENAME: "文件名不合法",
          FILE_EXISTS: "同名文件已存在",
          FILE_TOO_LARGE: "文件过大，最大支持 50 MB",
          EMPTY_FILE: "文件为空",
          WRITE_FAILED: "写入失败，请重试",
          UPLOAD_FAILED: "上传失败，请重试",
        };
        throw new Error(messages[data.error || ""] || "上传失败，请重试");
      }
      setUploadedSkillPath(data.path || "");
      setImportedSkills((prev) => [{ path: data.path || "", size: data.size || 0 }, ...prev]);
      demo.skillsImported = true;
      window.dispatchEvent(new CustomEvent("skills-updated"));
      setSkillFile(null);
      if (skillFileInput.current) skillFileInput.current.value = "";
      showToast(`已导入 ${data.path}`);
    } catch (error) {
      setSkillError(error instanceof Error ? error.message : "上传失败，请重试");
    } finally {
      setUploadingSkill(false);
    }
  }

  function selectScenario(kind: "card" | "generic") {
    resetDemoState();
    demo.scenario = kind;
    closeModal();
    showToast("已切换示例场景，并重置本地演练状态");
  }

  function goTrace() {
    closeModal();
    goPortal("trace");
  }

  if (!ui.modal) return null;

  return (
    <div
      className={isDrawer ? "overlay" : "overlay modal-overlay"}
      onClick={(event) => {
        if (event.target === event.currentTarget) closeModal();
      }}
    >
      <section
        className={
          (isDrawer ? "drawer" : "modal") + (type === "original-image" ? " provided-image-modal" : "")
        }
        role="dialog"
        aria-modal="true"
        aria-labelledby="dialog-title"
      >
        <div className="overlay-head">
          <div>
            <div className="eyebrow">{head.kicker}</div>
            <h2 id="dialog-title">{head.title}</h2>
            {head.desc ? <p>{head.desc}</p> : null}
          </div>
          <button className="icon-button" aria-label="关闭对话框" onClick={closeModal}>
            <AppIcon name="close" size={20} />
          </button>
        </div>

        {type === "evidence" ? (
          <>
            <span className="prototype-pill">MOCK · 示例证据，非真实工程数据</span>
            <div className="detail-section">
              <h3>平台为什么引用这项资产？</h3>
              <p>{evidenceDesc}{asset ? "。本次任务把相关已有能力作为上下文，用于判断复用范围和验证安排。" : ""}</p>
            </div>
            <div className="detail-section">
              <h3>来源与版本</h3>
              <dl>
                <dt>示例运行</dt><dd>DEMO-RUN-001</dd>
                <dt>来源能力</dt><dd>{asset ? "CodeGraph / 知识工程" : "平台任务与证据服务"}</dd>
                <dt>资产类型</dt><dd>{asset?.type || "研发产出物"}</dd>
                <dt>源码位置</dt><dd>{asset?.source || "待绑定真实平台产物"}</dd>
                <dt>代码 / 索引版本</dt><dd>待接入，不提供虚构提交号</dd>
                <dt>确认状态</dt><dd>{asset?.status || "示例展示，待核验"}</dd>
              </dl>
            </div>
            <div className="detail-section">
              <h3>业务、实现与验证关联</h3>
              <div className="evidence-code">{asset?.method || "Requirement → Rule → Code → Test → Approval"}
                {"\n\n"}上述路径与引用为原型示例。
                {"\n"}接入后应返回 source / revision / artifact / checksum。
              </div>
              <button className="related-link" onClick={goTrace}>展开完整追溯链 <AppIcon name="arrow" size={13} /></button>
            </div>
            <div className="detail-section">
              <h3>原始执行记录</h3>
              <div className="tech-panel" style={{ marginTop: 0 }}>
                <b>原始日志 · 未接入</b><br />没有真实 Skill 输出可展示。<br />
                <span className="line">接入后在此查看命令、退出码、日志和产物。</span>
              </div>
            </div>
            <div className="detail-footer">原型用于评审信息层级与交互，不代表已生成代码、执行测试或完成审批。所有数据必须关联实际运行后才能作为交付证据。</div>
          </>
        ) : null}

        {type === "approval" ? (
          <>
            <span className="pill pending">{demo.approved ? "本地原型已确认" : "等待模拟确认"} · DEMO-PLAN-001</span>
            <div className="detail-section">
              <h3>变更目标</h3>
              <p>{scenarioName()}。复用已有业务服务与工程规范，只扩展与本次需求有关的实现。</p>
            </div>
            <div className="detail-section">
              <h3>复用、扩展与保护</h3>
              <div className="rule-list">
                <div className="rule-item">
                  <div><p>复用已有处理入口与基础组件</p><small>知识引用与源码关联随方案传入执行</small></div>
                  <span className="pill success">复用</span>
                </div>
                <div className="rule-item">
                  <div><p>新增规则与增量业务实现</p><small>限定到已确认的任务与工作区</small></div>
                  <span className="pill progress">扩展</span>
                </div>
                <div className="rule-item">
                  <div><p>关键业务约束与原有行为</p><small>新增验证与必要回归保持关联</small></div>
                  <span className="pill pending">保护</span>
                </div>
              </div>
            </div>
            <div className="callout"><AppIcon name="alert" size={16} /><span>确认项：业务规则版本与当前工程一致。点击下方按钮只会更新浏览器中的示例状态。</span></div>
            <div className="modal-actions">
              <button className="btn" onClick={closeModal}>返回查看</button>
              <button className="btn btn-primary" onClick={approve}>
                <AppIcon name="check" size={14} />{demo.approved ? "已模拟确认" : "确认示例方案"}
              </button>
            </div>
          </>
        ) : null}

        {type === "scenario" ? (
          <>
            {scenarioOptions.map((option) => (
              <button
                key={option.id}
                className={demo.scenario === option.id ? "scenario-option active" : "scenario-option"}
                onClick={() => selectScenario(option.id as "card" | "generic")}
              >
                <span className="square-icon"><AppIcon name={option.icon} /></span>
                <span><h3>{option.name}</h3><p>{option.desc}</p></span>
                {demo.scenario === option.id ? <span className="check"><AppIcon name="check" size={16} /></span> : null}
              </button>
            ))}
            <div className="callout blue"><AppIcon name="folder" size={15} /><span>切换会重置本地原型演练状态，不创建工程、任务或业务交易。</span></div>
          </>
        ) : null}

        {type === "integrations" ? (
          <>
            <span className="prototype-pill">连接状态来自原型配置 · 不代表可用性检测</span>
            <div style={{ marginTop: 15 }}>
              {integrations.map((row) => (
                <div key={row.name} className="integration-row">
                  <div><h3>{row.name}</h3><p>{row.desc}</p></div>
                  <span className="pill neutral">待接入</span>
                </div>
              ))}
            </div>
            <div className="detail-footer">后续对接任务启动、状态、事件、产出物与权限能力。界面不直接执行终端命令，也不读取真实平台凭据。</div>
          </>
        ) : null}

        {type === "skills" ? (
          <>
            <div className="rule-list">
              {skillGroups.map((row) => (
                <div key={row.num} className="asset-row">
                  <span className="square-icon">{row.num}</span>
                  <div>
                    <h3>{row.name}</h3>
                    <p>{row.platform}</p>
                    <div className="asset-chips"><span className="asset-chip">输入 / 产出：{row.desc}</span></div>
                  </div>
                </div>
              ))}
            </div>
            <div className="callout blue"><AppIcon name="layers" size={16} /><span>平台选择 Skill、提供相关上下文、传递产出、收集反馈。主屏展示工程工作，详情保留命令与来源。</span></div>
            <div className="detail-footer">本原型不启动任何外部 Skill 或 Agent。接入后，每次调用应关联平台任务 ID、Skill 版本和产出物。</div>
          </>
        ) : null}

        {type === "guide" ? (
          <>
            <div className="detail-section">
              <h3>01 / 理解已有工程</h3>
              <p>点击关系图，追溯业务知识、代码与测试。展现平台怎样形成有来源的任务上下文。</p>
            </div>
            <div className="detail-section">
              <h3>02 / 评估影响并组织研发</h3>
              <p>展示影响范围、复用建议和受保护约束。确认示例方案，体验 Skills 协同执行的呈现。</p>
            </div>
            <div className="detail-section">
              <h3>03 / 验证与交付</h3>
              <p>查看质量门禁、追溯链和业务验收。效能指标等待真实对照实验，不预设提升百分比。</p>
            </div>
            <div className="callout"><AppIcon name="play" size={16} /><span>“体验流程”只演练页面状态，运行与审批始终标记 MOCK，不代表底层执行。</span></div>
            <div className="modal-actions">
              <button className="btn" onClick={closeModal}>自由浏览</button>
              <button className="btn btn-primary" onClick={beginDemo}><AppIcon name="play" size={14} />体验交互流程</button>
            </div>
          </>
        ) : null}

        {type === "events" ? (
          <>
            <span className="prototype-pill">MOCK / DEMO-RUN-001</span>
            {eventList.map((event) => (
              <div key={event.sequence} className="detail-section">
                <span className="mini-tag">#{event.sequence} · {event.time}</span>
                <h3 style={{ marginTop: 12 }}>{event.type}</h3>
                <p>{event.summary}</p>
                <small style={{ color: "#a4afbe" }}>来源：本地原型，不是平台执行事件</small>
              </div>
            ))}
            <div className="detail-footer">真实事件需包含 task / run / step / source / revision / artifact 等关联信息。当前没有真实执行日志。</div>
          </>
        ) : null}

        {type === "test-report" ? (
          <>
            <span className="prototype-pill">MOCK · 非真实报告</span>
            <div className="detail-section">
              <dl>
                <dt>运行来源</dt><dd>本地原型</dd>
                <dt>验证范围</dt><dd>新增需求 / 关键约束 / 关联回归 / 安全检查</dd>
                <dt>当前状态</dt><dd>{demo.verified ? "本地示例已通过" : "尚未演练"}</dd>
                <dt>真实测试数量</dt><dd>未接入</dd>
                <dt>原始报告</dt><dd>待绑定真实产物 URI 与校验信息</dd>
              </dl>
            </div>
            <div className="detail-section">
              <h3>应呈现的验证链</h3>
              {reportChain.map((text) => (
                <button key={text} className="related-link" onClick={goTrace}>
                  {text}<AppIcon name="arrow" size={12} />
                </button>
              ))}
            </div>
          </>
        ) : null}

        {type === "review" ? (
          <>
            <div className="detail-section">
              <h3>模拟交付检查</h3>
              <p>示例需求、设计、构建、测试与安全检查已具备展示结果。真实评审需绑定代码版本、报告与审核权限。</p>
            </div>
            <div className="modal-actions">
              <button className="btn" onClick={closeModal}>返回</button>
              <button className="btn btn-primary" onClick={finishReview}><AppIcon name="check" size={14} />完成模拟评审</button>
            </div>
          </>
        ) : null}

        {type === "import-skills" ? (
          <>
            <div className="skill-import-form">
              <label className="skill-import-field">
                <span>分类 <em>*</em></span>
                <select
                  className="form-input"
                  aria-label="选择分类"
                  value={skillCategory}
                  onChange={(event) => setSkillCategory(event.target.value)}
                >
                  <option value="">请选择分类</option>
                  {skillCategories.map((item) => (
                    <option key={item.id} value={item.id}>{item.name}</option>
                  ))}
                </select>
              </label>
              <label className="skill-import-field skill-import-file">
                <span>文件</span>
                <input ref={skillFileInput} type="file" aria-label="选择 Skill 文件" onChange={onSkillFileChange} />
              </label>
            </div>
            <p className="panel-subtitle" style={{ marginTop: 12 }}>
              文件将保存到 <code>Skill 分类目录</code>
            </p>
            {skillError ? <p className="skill-import-error">{skillError}</p> : uploadedSkillPath ? <p className="skill-import-success">已导入：{uploadedSkillPath}</p> : null}
            {importedSkills.length ? (
              <div className="skill-import-list">
                <b>已导入文件</b>
                <ul>
                  {importedSkills.map((item) => (
                    <li key={item.path}>
                      <code>{item.path}</code><span>{formatSkillSize(item.size)}</span>
                    </li>
                  ))}
                </ul>
              </div>
            ) : null}
            <p className="skill-import-hint">文件保存到平台数据目录，不会自动安装或执行。</p>
            <div className="modal-actions">
              <button className="btn" onClick={closeModal}>取消</button>
              <button
                className="btn btn-primary"
                disabled={uploadingSkill || !skillCategory || !skillFile}
                onClick={uploadSkill}
              >
                <AppIcon name="upload" size={14} />{uploadingSkill ? "正在上传…" : "上传"}
              </button>
            </div>
          </>
        ) : null}

        {type === "skill-detail" ? (
          <>
            <span className="prototype-pill">示例能力 · 版本待映射</span>
            <div className="detail-section">
              <h3>输入上下文</h3>
              <p>需求、源代码基线、知识索引、已确认变更范围与工程规范。</p>
              <h3>执行与产出</h3>
              <p>实际平台将记录 Skill 版本、输入引用、任务事件、执行结果与相关产物。</p>
            </div>
            <button className="btn" onClick={() => openModal("events")}>查看本地操作记录</button>
          </>
        ) : null}

        {type === "original-image" && payload.image ? (
          <div>
            <div className="provided-image-toolbar">
              <span>默认适应窗口，可切换到原始尺寸查看细节。</span>
              <button className="btn btn-small" onClick={() => setImageFull(!imageFull)}>
                {imageFull ? "适应窗口" : "原始尺寸"}
              </button>
            </div>
            <div className="provided-image-scroll">
              <img
                src={payload.image.path}
                alt={payload.image.title}
                style={{ width: imageFull ? `${payload.image.width}px` : "100%" }}
              />
            </div>
          </div>
        ) : null}

        {type === "resource-method" ? (
          <div className="detail-section">
            <p>Token 按供应方返回的输入、输出与缓存口径计数；成本按模型、价格版本与币种计算，避免跨模型直接累加单价。</p>
            <p>任务历时使用墙钟时间；阶段累计耗时与并行 Agent 工时分开统计。重试成本计入同一任务，同时展示质量、成功率和返工。</p>
            <p>当前数字均为虚构示例，没有真实采集数据或账单。</p>
          </div>
        ) : null}

        {type === "rollback" ? (
          <div className="detail-section">
            <h3>回滚条件</h3>
            <p>固定任务集出现退化，或运行观察发现失败率、质量和成本异常时提出回滚申请。</p>
            <h3>操作边界</h3>
            <p>选择已审核的历史版本，保留原因、审批人和关联任务。原型不调用真实发布或回滚接口。</p>
          </div>
        ) : null}

        {type === "evolution-source" ? (
          <div className="detail-section">
            <h3>候选来源</h3>
            <p>候选改进由运行反馈、失败用例与人工评审建议汇总形成。原型不展示真实反馈数据，也不自动修改能力版本。</p>
          </div>
        ) : null}

        {type === "evolution-evaluation" ? (
          <div className="detail-section">
            <h3>固定任务集评测</h3>
            <p>评测需在相同任务集、相同验收口径下比较新旧版本，记录收益、退化、时延与成本。当前未导入真实评测结果。</p>
          </div>
        ) : null}
      </section>
    </div>
  );
}
