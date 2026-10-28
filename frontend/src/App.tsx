import {
  useCallback,
  useRef,
  useEffect,
  useState,
  Suspense,
  lazy,
  useId,
  cloneElement,
  isValidElement,
  useMemo,
} from "react";
import {
  Database,
  Network,
  Layers,
  ShieldCheck,
  ListTodo,
  History,
  Plug,
  ArrowRight,
  FolderUp,
  GitBranch,
  RefreshCw,
  LogOut,
  FileCode2,
  CircleCheck,
  AlertCircle,
  Settings,
} from "lucide-react";
import { request, type Row } from "./api";
import { modelErrorHint } from "./modelErrors";
import SubtaskManager from "./SubtaskManager";
import ContextPanel from "./ContextPanel";
import OntologyEditor from "./OntologyEditor";
import EngineSettings from "./EngineSettings";
import WorkspaceSettings from "./WorkspaceSettings";
import EvaluationPanel from "./EvaluationPanel";
const Graph = lazy(() => import("./Graph"));

const pages = [
  "sources",
  "settings",
  "tasks",
  "subtasks",
  "review",
  "ontology",
  "graph",
  "versions",
  "agents",
] as const;
type Page = (typeof pages)[number];
const labels: Record<Page, [string, string]> = {
  sources: ["数据源接入", "Data sources"],
  settings: ["配置中心", "Configuration center"],
  tasks: ["任务与确认中心", "Tasks & confirmation"],
  subtasks: ["规则抽取子任务", "Extraction subtasks"],
  review: ["知识审核", "Knowledge review"],
  ontology: ["本体建模与审核", "Ontology modeling & review"],
  graph: ["本体工作台", "Ontology workspace"],
  versions: ["版本与审计", "Releases & audit"],
  agents: ["Code Agent 联调", "Code Agent integration"],
};
const descriptions: Record<Page, [string, string]> = {
  sources: [
    "接入代码与文档，建立可追溯的不可变来源快照。",
    "Connect code and documents with traceable, immutable snapshots.",
  ],
  tasks: [
    "每个任务固定来源与配置，明确请求状态和实际结果。",
    "Every job pins its sources and configuration; requests are distinct from results.",
  ],
  subtasks: [
    "独立管理源码范围、进度和失败恢复，保留成功结果与完整尝试记录。",
    "Manage source scopes, progress and recovery independently, retaining successful output and attempt history.",
  ],
  settings: [
    "统一管理模型连接与验证、抽取与执行参数、界面偏好和账号密码。",
    "Manage model connections and validation, extraction and execution parameters, interface preferences and account passwords.",
  ],
  review: [
    "对照原始证据，确认完整条件、适用范围和例外。",
    "Review complete conditions, scope and exceptions against original evidence.",
  ],
  ontology: ["管理业务模块、概念与别名，审核其规则和代码关联。", "Define and review business modules, concepts, aliases and memberships."],
  graph: [
    "理解业务语义，追溯代码实体，查看两者之间的关联。",
    "Explore business semantics, code entities and the mappings between them.",
  ],
  versions: [
    "审核修订、发布清单与当前默认指针分别可追溯。",
    "Trace review revisions, immutable manifests and the current release pointer.",
  ],
  agents: [
    "通过固定发布版本检索规则、追踪代码和证据，调测只读 Context 与 OpenCode。",
    "Search pinned published rules, trace code and evidence, and test read-only Context with OpenCode.",
  ],
};
const icons = [Database, Settings, ListTodo, GitBranch, ShieldCheck, Layers, Network, History, Plug];
const statusNames: Record<string, [string, string]> = {
  CREATED: ["待确认启动", "Awaiting start"],
  QUEUED: ["排队中", "Queued"],
  RUNNING: ["执行中", "Running"],
  SUCCEEDED: ["已完成", "Succeeded"],
  FAILED: ["失败", "Failed"],
  CANCEL_REQUESTED: ["取消处理中", "Cancel requested"],
  CANCELLED: ["已取消", "Cancelled"],
  PAUSED: ["已暂停", "Paused"],
  PAUSE_REQUESTED: ["暂停处理中", "Pause requested"],
  EXECUTION_UNKNOWN: ["执行结果未知", "Execution unknown"],
  WAITING_RETRY: ["等待退避重试", "Waiting for retry"],
  PARTIAL_SUCCEEDED: ["部分完成，存在缺口", "Partially completed"],
  NEEDS_ATTENTION: ["需要处理未知批次", "Needs attention"],
  WAITING_SUBTASKS: ["等待子任务操作", "Waiting for subtask actions"],
  PENDING: ["待执行", "Pending"],
  RETRY_WAIT: ["等待重试", "Retry scheduled"],
  INVALID: ["校验未通过，已隔离", "Invalid / quarantined"],
  UNKNOWN: ["结果未知，未自动重发", "Unknown / not replayed"],
  CANDIDATE: ["待审核", "Candidate"],
  APPROVED: ["已批准", "Approved"],
  REJECTED: ["已拒绝", "Rejected"],
  PUBLISHED: ["已发布", "Published"],
  SNAPSHOT_READY: ["快照已固定", "Snapshot ready"],
};
const short = (s: string | undefined) => (s ? s.slice(0, 17) + "…" : "—");

function Field({
  label,
  children,
}: {
  label: string;
  children: React.ReactNode;
}) {
  const id = useId();
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      {isValidElement(children)
        ? cloneElement(children as React.ReactElement<{ id?: string }>, { id })
        : children}
    </div>
  );
}
function Empty({ text }: { text: string }) {
  return (
    <div className="empty">
      <Layers size={30} />
      <p>{text}</p>
    </div>
  );
}
function Panel({
  title,
  children,
  extra,
}: {
  title: string;
  children: React.ReactNode;
  extra?: React.ReactNode;
}) {
  return (
    <section className="panel">
      <div className="panel-head">
        <h2>{title}</h2>
        {extra}
      </div>
      <div className="pad">{children}</div>
    </section>
  );
}

export default function App() {
  const [lang, setLang] = useState(
    () => localStorage.getItem("bos.locale") || "zh",
  );
  const [theme, setTheme] = useState(
    () => localStorage.getItem("bos.theme") || "light",
  );
  const [dark, setDark] = useState(false),
    [page, setPage] = useState<Page>("sources");
  const [settingsSection, setSettingsSection] = useState<"models" | "extraction" | "appearance" | "account" | "engine" | "workspace">("models");
  const graphLinkHandled = useRef(false);
  const autoLoginTried = useRef(false);
  const t = (zh: string, en: string) => (lang === "zh" ? zh : en);
  const [token, setToken] = useState(""),
    [loginUsername, setLoginUsername] = useState("admin"),
    [loginPassword, setLoginPassword] = useState(""),
    [me, setMe] = useState<Row | null>(null);
  const [autoLoggingIn, setAutoLoggingIn] = useState(false);
  const [health, setHealth] = useState<Row>({}),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [message, setMessage] = useState("");
  const [snapshots, setSnapshots] = useState<Row[]>([]),
    [projections, setProjections] = useState<Row[]>([]),
    [models, setModels] = useState<Row[]>([]),
    [jobs, setJobs] = useState<Row[]>([]),
    [candidates, setCandidates] = useState<Row[]>([]),
    [releases, setReleases] = useState<Row[]>([]),
    [audits, setAudits] = useState<Row[]>([]),
    [pointer, setPointer] = useState<Row>({ revision: 0 });
  const [sourceMode, setSourceMode] = useState("zip"),
    [sourceName, setSourceName] = useState(""),
    [zip, setZip] = useState<File | null>(null),
    [directory, setDirectory] = useState<File[]>([]),
    [gitURL, setGitURL] = useState(""),
    [gitRef, setGitRef] = useState("main");
  const [modelDraft, setModelDraft] = useState({
    name: "",
    endpoint: "",
    model_id: "",
    credential_env: "BOS_MODEL_API_KEY",
    allow_source: false,
    max_input_chars: 24000,
    max_output_tokens: 4000,
    temperature: 0.1,
  });
  const [passwordDraft, setPasswordDraft] = useState({current: "", next: "", confirm: ""});
  const [hostConsent, setHostConsent] = useState(false);
  const [extractionDefaults, setExtractionDefaults] = useState<Row>({max_source_lines: 100, revision: 0});
  const [settingsDraft, setSettingsDraft] = useState<string | null>(null);
  const [policyDraft, setPolicyDraft] = useState<Row>({});
  const [replayUnknownJob, setReplayUnknownJob] = useState("");
  const [credentialMode, setCredentialMode] = useState<
    "api_key" | "environment"
  >("api_key");
  const [apiKey, setApiKey] = useState("");
  let endpointHost = "";
  try {
    const url = new URL(modelDraft.endpoint.trim());
    if (
      ["https:", "http:"].includes(url.protocol) &&
      !url.username &&
      !url.password
    )
      endpointHost = url.hostname
        .replace(/^\[|\]$/g, "")
        .replace(/\.$/, "")
        .toLowerCase();
  } catch {
    /* Endpoint remains a draft until it is a complete URL. */
  }
  const hostAllowed =
    !!endpointHost && (me?.model_hosts || []).includes(endpointHost);
  const [snapshotId, setSnapshotId] = useState(""),
    [projectionId, setProjectionId] = useState(""),
    [modelId, setModelId] = useState(""),
    [jobType, setJobType] = useState("ANALYZE"),
    [jobId, setJobId] = useState("");
  const [reviewId, setReviewId] = useState(""),
    [reviewDraft, setReviewDraft] = useState<Row>({}),
    [reviewReason, setReviewReason] = useState(""),
    [evidenceChecked, setEvidenceChecked] = useState(false),
    [sourceEvidence, setSourceEvidence] = useState<Row | null>(null);
  const [selectedCandidates, setSelectedCandidates] = useState<string[]>([]),
    [publishReason, setPublishReason] = useState(""),
    [releaseId, setReleaseId] = useState("");
  const [view, setView] = useState("code"),
    [graph, setGraph] = useState<Row>({ nodes: [], edges: [] }),
    [node, setNode] = useState<Row | null>(null),
    [search, setSearch] = useState(""),
    [kind, setKind] = useState("all"),
    [graphRelease, setGraphRelease] = useState("");
  const [graphJobId, setGraphJobId] = useState("");
  const [auditTab, setAuditTab] = useState(false);
  const admin = me?.role === "admin",
    builder = admin || me?.role === "developer",
    reviewer = admin || me?.role === "reviewer";
  useEffect(() => {
    const media = matchMedia("(prefers-color-scheme: dark)");
    const update = () => {
      const d = theme === "dark" || (theme === "system" && media.matches);
      setDark(d);
      document.documentElement.dataset.theme = d ? "dark" : "light";
    };
    update();
    media.addEventListener("change", update);
    localStorage.setItem("bos.theme", theme);
    return () => media.removeEventListener("change", update);
  }, [theme]);
  useEffect(() => {
    localStorage.setItem("bos.locale", lang);
    document.documentElement.lang = lang === "zh" ? "zh-CN" : "en";
  }, [lang]);
  useEffect(() => {
    request("/health", "")
      .then(setHealth)
      .catch(() =>
        setError(
          t(
            "无法连接后端，请启动 API 服务。",
            "Cannot reach backend. Start the API server.",
          ),
        ),
      );
  }, []);
  const refresh = useCallback(async (auth: string, role: string) => {
    const a = (p: string) => request(p, auth);
    const [r, p] = await Promise.all([
      a("/releases"),
      a("/release-pointers/current"),
    ]);
    setReleases(r);
    setPointer(p);
    if (role === "agent") return;
    const [s, pr, j, c, m, au, defaults] = await Promise.all([
      a("/source-snapshots"),
      a("/code/projections"),
      a("/knowledge-jobs"),
      a("/ontology/candidates"),
      a("/model-connections"),
      role === "admin" || role === "reviewer"
        ? a("/audit-events")
        : Promise.resolve([]),
      a("/settings/extraction"),
    ]);
    setSnapshots(s);
    setProjections(pr);
    setJobs(j);
    setCandidates(c);
    setModels(m);
    setAudits(au);
    setExtractionDefaults(defaults);
  }, []);
  useEffect(() => {
    if (me || autoLoginTried.current) return;
    autoLoginTried.current = true;
    let demoEntry = window.location.hash.includes("autologin=1");
    if (!demoEntry) {
      try {
        demoEntry = sessionStorage.getItem("bos.demo.autologin") === "1";
      } catch {
        /* session storage may be unavailable */
      }
    }
    if (!demoEntry) return;
    setAutoLoggingIn(true);
    (async () => {
      try {
        const session = await request("/auth/login", "", "POST", {
          username: "admin",
          password: "admin123",
        });
        const user = await request("/me", session.access_token);
        await refresh(session.access_token, user.role);
        setToken(session.access_token);
        setMe(user);
        if (user.role === "agent") setPage("versions");
        try {
          sessionStorage.setItem("bos.demo.autologin", "1");
        } catch {
          /* session storage may be unavailable */
        }
        if (window.location.hash.includes("autologin=1")) {
          history.replaceState(null, "", `${window.location.pathname}${window.location.search}#/studio`);
        }
      } catch {
        /* Fall back to the sign-in form when automatic entry is unavailable. */
      } finally {
        setAutoLoggingIn(false);
      }
    })();
  }, [me, refresh]);
  useEffect(() => {
    if (!token || !me) return;
    const timer = setInterval(() => {
      refresh(token, me.role).catch((error: Error) => {
        if (error.message === "UNAUTHENTICATED") {
          clearSession();
          setError(t("登录已过期，请重新登录。", "Session expired. Please sign in again."));
        }
      });
    }, 4000);
    return () => clearInterval(timer);
  }, [token, me, refresh]);
  async function act(work: () => Promise<void>, reload = true) {
    if (busy) return;
    setBusy(true);
    setError("");
    setMessage("");
    try {
      await work();
      if (reload && token && me) await refresh(token, me.role);
    } catch (e) {
      const exc = e as Error & { trace?: string; params?: Row };
      if (exc.message === "UNAUTHENTICATED") clearSession();
      const hints: Record<string, string> = {
        INVALID_LOGIN: t("用户名或密码错误。", "Incorrect username or password."),
        LOGIN_RATE_LIMITED: t("登录尝试过于频繁，请一分钟后重试。", "Too many login attempts. Try again in one minute."),
        UNAUTHENTICATED: t("登录已过期，请重新登录。", "Session expired. Please sign in again."),
        CURRENT_PASSWORD_INVALID: t("当前密码不正确。", "Current password is incorrect."),
        PASSWORD_UNCHANGED: t("新密码不能与当前密码相同。", "Choose a password different from the current one."),
        MODEL_ENDPOINT_NOT_ALLOWED:
          exc.params?.policy === "environment"
            ? t(
                "该模型主机不在部署环境的固定白名单中，请由部署管理员更新 BOS_MODEL_HOSTS 后重启服务。表单输入已保留。",
                "This host is outside the deployment allowlist. Ask the deployment administrator to update BOS_MODEL_HOSTS and restart. Your draft is preserved.",
              )
            : t(
                "该模型主机尚未登记。请在模型表单下方勾选并登记这个确切主机，再保存配置。",
                "This model host is not registered. Explicitly authorize the exact host below the form, then save the configuration.",
              ),
        MODEL_ENDPOINT_INVALID: t(
          "请输入完整的 http(s) Endpoint；地址不能包含用户名、密码、查询参数或片段，端口须有效。",
          "Enter a complete HTTP(S) endpoint with a valid port and no username, password, query or fragment.",
        ),
        MODEL_HTTPS_REQUIRED: t(
          "非本机模型地址必须使用 HTTPS。",
          "Non-loopback model endpoints require HTTPS.",
        ),
        MODEL_ENDPOINT_POLICY_LOCKED: t(
          "部署环境的固定白名单不能通过界面扩大，请联系部署管理员。",
          "The deployment allowlist cannot be expanded through this interface.",
        ),
        MODEL_CREDENTIAL_MISSING: t(
          "地址已允许，但后端未找到所填写环境变量中的密钥。请在 API 与 Worker 的运行环境中配置，不要发到聊天。",
          "The address is allowed, but the referenced key environment variable is missing. Configure it for the API and Worker; do not send it in chat.",
        ),
      };
      setError(
        (modelErrorHint(exc.message, lang) || hints[exc.message]
          ? (modelErrorHint(exc.message, lang) || hints[exc.message]) + " · "
          : "") +
          exc.message +
          (["MODEL_INPUT_UNIT_TOO_LARGE", "MODEL_INPUT_BUDGET_EXCEEDED"].includes(exc.message) && exc.params
            ? " · " + JSON.stringify(exc.params) : "") +
          (exc.trace ? " · Trace " + exc.trace : ""),
      );
    } finally {
      setBusy(false);
    }
  }
  const api = (p: string, method = "GET", body?: unknown) =>
    request(p, token, method, body);
  const status = (s: string) => (
    <span
      className={
        "badge " +
        (["SUCCEEDED", "PUBLISHED", "APPROVED", "SNAPSHOT_READY"].includes(s)
          ? "success"
          : ["FAILED", "EXECUTION_UNKNOWN", "REJECTED"].includes(s)
            ? "warn"
            : "")
      }
    >
      <i />
      {statusNames[s] ? t(...statusNames[s]) : s}
    </span>
  );
  async function login() {
    const session = await request("/auth/login", "", "POST", {username: loginUsername, password: loginPassword});
    setLoginPassword("");
    try {
      const user = await request("/me", session.access_token);
      await refresh(session.access_token, user.role);
      setToken(session.access_token);
      setMe(user);
      if (user.role === "agent") setPage("versions");
    } catch (error) {
      await request("/auth/logout", session.access_token, "POST").catch(() => {});
      throw error;
    }
  }
  async function logout() {
    if (busy) return;
    setBusy(true);
    try {
      await api("/auth/logout", "POST");
      clearSession();
    } catch (error) {
      if ((error as Error).message === "UNAUTHENTICATED") clearSession();
      else setError(t("退出失败，请重试。", "Sign-out failed. Please retry."));
    } finally { setBusy(false); }
  }
  function clearSession() {
    try {
      sessionStorage.removeItem("bos.demo.autologin");
    } catch {
      /* session storage may be unavailable */
    }
    setPasswordDraft({current: "", next: "", confirm: ""});
    setLoginPassword("");
    setHostConsent(false);
    setApiKey("");
    setCredentialMode("api_key");
    setModelDraft((draft) => ({
      ...draft,
      credential_env: "BOS_MODEL_API_KEY",
    }));
    setToken("");
    setMe(null);
    setSnapshots([]);
    setProjections([]);
    setModels([]);
    setJobs([]);
    setCandidates([]);
    setReleases([]);
    setAudits([]);
    setGraph({ nodes: [], edges: [] });
    setNode(null);
    setSourceEvidence(null);
    setReviewDraft({});
    setReviewId("");
    setJobId("");
    setModelId("");
    setProjectionId("");
    setSnapshotId("");
    setReleaseId("");
    setGraphRelease("");
    setSelectedCandidates([]);
    setMessage("");
    setError("");
  }
  async function importSource() {
    let result: Row;
    if (sourceMode === "git") {
      result = await api("/sources/git", "POST", {
        name: sourceName,
        url: gitURL,
        ref: gitRef,
      });
    } else {
      const form = new FormData();
      form.append("name", sourceName);
      if (sourceMode === "zip" && zip) form.append("file", zip);
      if (sourceMode === "directory")
        directory.forEach((f) =>
          form.append("files", f, f.webkitRelativePath || f.name),
        );
      result = await api(
        sourceMode === "zip" ? "/sources/upload" : "/sources/directory",
        "POST",
        form,
      );
    }
    setSnapshotId(result.id);
    setMessage(
      t(
        "快照已固定。请到任务中心创建并确认解析任务。",
        "Snapshot captured. Create and confirm an analysis job in Tasks.",
      ),
    );
  }
  async function createJob() {
    const result = await api("/knowledge-jobs", "POST", {
      type: jobType,
      snapshot_id: snapshotId,
      ...(jobType === "EXTRACT"
        ? { projection_id: projectionId, model_id: modelId }
        : {}),
    });
    setJobId(result.id);
    setMessage(
      t(
        "任务已创建，点击“确认启动”后才执行。",
        "Job created. It executes only after Confirm start.",
      ),
    );
  }
  async function loadGraph() {
    const result = graphRelease
      ? await api("/releases/" + graphRelease + "/graph?limit=80")
      : await api(
          "/code/subgraph?include_candidates=true&limit=80&projection_id=" + encodeURIComponent(projectionId) + (graphJobId ? "&job_id=" + encodeURIComponent(graphJobId) : ""),
        );
    setGraph(result);
    setNode(null);
  }
  useEffect(() => {
    if (!token || graphLinkHandled.current) return;
    const target = new URLSearchParams(location.search).get("ontology_job");
    if (!target) return;
    const job = jobs.find(j => j.id === target);
    if (!job?.projection_id) return;
    graphLinkHandled.current = true;
    setJobId(job.id); setProjectionId(job.projection_id); setGraphJobId(job.id);
    setPage("graph"); setGraphRelease(""); setView("mapping");
    request("/code/subgraph?include_candidates=true&limit=80&projection_id=" + encodeURIComponent(job.projection_id) + "&job_id=" + encodeURIComponent(job.id), token)
      .then(result => {setGraph(result);setNode(null);}).catch(e => setError((e as Error).message));
  }, [jobs, token]);
  async function expandGraph() {
    if (!node || !graph.projection_id) return;
    const result = await api("/code/subgraph?include_candidates=" + !!graph.preview + "&limit=100&projection_id=" + encodeURIComponent(graph.projection_id) + (graph.job_id ? "&job_id=" + encodeURIComponent(graph.job_id) : "") + "&root=" + encodeURIComponent(node.id));
    const nodes = Array.from(new Map([...graph.nodes,...result.nodes].map(n=>[n.id,n])).values()).slice(0,300);
    const ids = new Set(nodes.map(n=>n.id));
    const edges = Array.from(new Map([...graph.edges,...result.edges].map(e=>[e.id,e])).values()).filter(e=>ids.has(e.source)&&ids.has(e.target));
    setGraph({...graph,nodes,edges,truncated:true,candidate_shown:nodes.filter(n=>n.kind==="BusinessRule").length});
  }
  const selectedReview = candidates.find((c) => c.id === reviewId);
  function selectReview(item: Row) {
    setReviewId(item.id);
    setReviewDraft({
      name: item.name,
      condition: item.condition,
      outcome: item.outcome,
      scope: item.scope,
      exceptions: item.exceptions.join("\n"),
      uncertainties: item.uncertainties.join("\n"),
    });
    setReviewReason("");
    setEvidenceChecked(false);
    setSourceEvidence(null);
  }
  async function decision(d: string) {
    await api("/reviews/" + reviewId + "/decision", "POST", {
      decision: d,
      expected_revision: selectedReview?.revision,
      reason: reviewReason,
      ...(d === "EDIT"
        ? {
            changes: {
              ...reviewDraft,
              exceptions: String(reviewDraft.exceptions)
                .split("\n")
                .filter(Boolean),
              uncertainties: String(reviewDraft.uncertainties)
                .split("\n")
                .filter(Boolean),
            },
          }
        : {}),
    });
    setEvidenceChecked(false);
    setMessage(
      t(
        "审核修订已保存。内容编辑后必须再次审核。",
        "Review revision saved. Edited content requires a new review.",
      ),
    );
  }
  const shownNodes: Row[] = useMemo(
    () => {
      const businessKinds = ["BusinessRule", "BusinessModule", "BusinessConcept"];
      const mapped = new Set<string>(graph.nodes.filter((n: Row) => businessKinds.includes(n.kind)).map((n: Row) => n.id));
      for (const edge of graph.edges) {
        if (["IMPLEMENTED_BY", "CONTAINS_RULE"].includes(edge.type) && mapped.has(edge.source)) mapped.add(edge.target);
      }
      // Include available containing classes/files without pulling unrelated code into the mapping view.
      let added = true;
      while (added) {
        added = false;
        for (const edge of graph.edges) {
          if (edge.type === "DECLARES" && mapped.has(edge.target) && !mapped.has(edge.source)) {
            mapped.add(edge.source); added = true;
          }
        }
      }
      return graph.nodes.filter(
        (n: Row) =>
          (view === "code"
            ? !["BusinessRule","BusinessModule","BusinessConcept"].includes(n.kind)
            : view === "business"
              ? ["BusinessRule","BusinessModule","BusinessConcept"].includes(n.kind)
              : mapped.has(n.id)) &&
          (kind === "all" || n.kind === kind) &&
          (!search ||
            (n.name + " " + n.signature + " " + n.id)
              .toLowerCase()
              .includes(search.toLowerCase())),
      );
    },
    [graph.nodes, graph.edges, view, kind, search],
  );
  const shownEdges: Row[] = useMemo(() => {
    const shownIds = new Set(shownNodes.map((n) => n.id));
    return graph.edges.filter(
      (e: Row) => shownIds.has(e.source) && shownIds.has(e.target),
    );
  }, [graph.edges, shownNodes]);
  const activeJob = jobs.find((j) => j.id === jobId);
  const globalSettings = (
    <div className="global-settings">
      <select
        aria-label="Language"
        value={lang}
        onChange={(e) => setLang(e.target.value)}
      >
        <option value="zh">简体中文</option>
        <option value="en">English</option>
      </select>
      <select
        aria-label="Theme"
        value={theme}
        onChange={(e) => setTheme(e.target.value)}
      >
        <option value="light">{t("浅色", "Light")}</option>
        <option value="dark">{t("深色", "Dark")}</option>
        <option value="system">{t("跟随系统", "System")}</option>
      </select>
    </div>
  );
  const openPortal = () => {
    window.location.hash = "/portal/overview";
  };
  if (!me && autoLoggingIn) return null;
  if (!me)
    return (
      <div className="login">
        <header>
          <div className="brand">
            <div className="logo">
              <Network />
            </div>
            Bank Ontology Studio
          </div>
          <div className="row">
            <button onClick={openPortal}>
              <Plug size={15} />
              {t("AI-Coding 演示平台", "AI-Coding Demo Portal")}
            </button>
            {globalSettings}
          </div>
        </header>
        <main className="login-grid">
          <div>
            <div className="eyebrow">ENGINEERING KNOWLEDGE / M1</div>
            <h1>
              {t(
                "从代码事实，构建可信业务知识。",
                "From code facts to trusted business knowledge.",
              )}
            </h1>
            <p>
              {t(
                "接入仓库、分析实体、审核规则。让每一条业务知识都能回到它的来源。",
                "Connect repositories, analyze entities and review rules. Trace every business assertion back to its source.",
              )}
            </p>
            <div className="login-path">
              SOURCE <ArrowRight size={16} /> FACTS <ArrowRight size={16} />{" "}
              REVIEW <ArrowRight size={16} /> RELEASE
            </div>
            <div className="note">
              {t(
                "真实开发环境 · 初始无业务数据 · 不预置模型连接",
                "Development environment · No initial business data or model connections",
              )}
              <br />
              {health.storage || "…"} / {health.graph_projection || "…"}
            </div>
          </div>
          <Panel title={t("进入工作区", "Enter workspace")}>
            <form
              onSubmit={(e) => {
                e.preventDefault();
                act(login);
              }}
            >
              <Field label={t("用户名", "Username")}>
                <input autoComplete="username" required maxLength={100} value={loginUsername}
                  onChange={e => setLoginUsername(e.target.value)} />
              </Field>
              <Field label={t("密码", "Password")}>
                <input type="password" autoComplete="current-password" required maxLength={1024}
                  value={loginPassword} onChange={e => setLoginPassword(e.target.value)} />
              </Field>
              <p className="muted">
                {t(
                  "使用工作区账号登录，访问权限由服务器验证。",
                  "Sign in with a workspace account. Permissions are verified by the server.",
                )}
              </p>
              <button className="primary wide" disabled={busy || !loginUsername || !loginPassword}>
                {t("进入平台", "Enter studio")} <ArrowRight size={15} />
              </button>
            </form>
            <p className="muted">
              {t("初始管理员：admin / admin123。可在配置中心 → 账号与密码修改密码。", "Initial administrator: admin / admin123. Change the password in Configuration center → Account & password.")}
            </p>
            {message && <div role="status" className="notice">{message}</div>}
            {error && (
              <div role="alert" className="notice error">
                {error}
              </div>
            )}
          </Panel>
        </main>
      </div>
    );
  return (
    <div className="studio">
      <header className="topbar">
        <div className="brand">
          <div className="logo">
            <Network />
          </div>
          <div>
            Bank Ontology Studio
            <small>BUSINESS KNOWLEDGE · ENGINEERING CONTEXT</small>
          </div>
        </div>
        <div className="row">
          <span className="badge warn">M2 · {t("开发版", "Development")}</span>
          <span className="muted">{me.username || me.role} · {me.role}</span>
          <button title={t("退出", "Sign out")} onClick={logout}>
            <LogOut size={16} />
          </button>
        </div>
      </header>
      <div className="frame">
        <aside className="rail">
          <div className="project">
            <div className="project-avatar">BK</div>
            <div>
              {t("本地工作区", "Local workspace")}
              <small>
                {t("单项目 · 真实数据", "Single project · Live data")}
              </small>
            </div>
          </div>
          <div className="group">
            {t("资产与配置", "ASSETS & CONFIGURATION")}
          </div>
          {pages.map((p, i) => {
            const Icon = icons[i];
            return (
              <ReactFragment key={p}>
                <button
                  className={page === p ? "nav active" : "nav"}
                  onClick={() => {
                    setPage(p);
                    if (p === "subtasks" && jobs.find(j => j.id === jobId)?.type !== "EXTRACT") setJobId("");
                    setError("");
                  }}
                >
                  <Icon size={16} />
                  <span>{t(...labels[p])}</span>
                  {p === "agents" && <small>M2</small>}
                </button>
                {p === "settings" && (
                  <div className="group">
                    {t("知识构建与治理", "BUILD & GOVERN")}
                  </div>
                )}
                {p === "versions" && (
                  <div className="group">{t("集成", "INTEGRATION")}</div>
                )}
              </ReactFragment>
            );
          })}
          <div className="group">{t("演示", "DEMO")}</div>
          <button className="nav" onClick={openPortal}>
            <Plug size={16} />
            <span>{t("AI-Coding 演示平台", "AI-Coding Demo Portal")}</span>
          </button>
          <div className="rail-note">
            <CircleCheck size={14} />
            {t(
              "来源有版本，知识有证据。",
              "Versioned sources. Evidenced knowledge.",
            )}
            <br />
            <span>
              {health.storage} · {health.graph_projection}
            </span>
          </div>
        </aside>
        <main className="workspace">
          <div className="breadcrumb">
            {t("工作区", "Workspace")} <span>/</span> {t(...labels[page])}
          </div>
          <div className="heading">
            <div>
              <h1>{t(...labels[page])}</h1>
              <p>{t(...descriptions[page])}</p>
            </div>
            <button
              disabled={busy}
              onClick={() =>
                act(async () => {
                  await refresh(token, me.role);
                })
              }
            >
              <RefreshCw size={14} />
              {t("刷新", "Refresh")}
            </button>
          </div>
          {busy && (
            <div role="status" className="notice">
              {t("正在处理，请稍候…", "Working, please wait…")}
            </div>
          )}
          {error && (
            <div role="alert" className="notice error">
              <AlertCircle size={16} />
              <div>
                {error}
                <p>
                  {t(
                    "请检查输入、权限与后端配置；错误码和 Trace 可用于定位。",
                    "Check inputs, permissions and server configuration. Use the code and Trace for diagnosis.",
                  )}
                </p>
              </div>
            </div>
          )}
          {message && (
            <div role="status" className="notice success">
              {message}
            </div>
          )}

          {page === "sources" && (
            <>
              <div className="stats">
                <div>
                  <small>{t("来源快照", "SOURCE SNAPSHOTS")}</small>
                  <strong>{snapshots.length}</strong>
                </div>
                <div>
                  <small>{t("已解析投影", "ANALYSIS PROJECTIONS")}</small>
                  <strong>{projections.length}</strong>
                </div>
                <div>
                  <small>{t("来源身份", "SOURCE IDENTITY")}</small>
                  <strong className="small">SHA-256</strong>
                </div>
              </div>
              <div className="two-columns">
                <Panel title={t("接入新来源", "Connect a source")}>
                  <div className="tabs">
                    {[
                      ["zip", t("上传 ZIP", "ZIP archive")],
                      ["directory", t("本地目录", "Local directory")],
                      ["git", t("Git 链接", "Git URL")],
                    ].map(([v, n]) => (
                      <button
                        key={v}
                        className={sourceMode === v ? "active" : ""}
                        onClick={() => setSourceMode(v)}
                      >
                        {n}
                      </button>
                    ))}
                  </div>
                  <form
                    onSubmit={(e) => {
                      e.preventDefault();
                      act(importSource);
                    }}
                  >
                    <Field
                      label={t("仓库/来源名称", "Repository / source name")}
                    >
                      <input
                        required
                        maxLength={100}
                        value={sourceName}
                        onChange={(e) => setSourceName(e.target.value)}
                        placeholder="consumer-credit"
                      />
                    </Field>
                    {sourceMode === "git" ? (
                      <>
                        <Field label={t("HTTPS Git 地址", "HTTPS Git URL")}>
                          <input
                            required
                            type="url"
                            value={gitURL}
                            onChange={(e) => setGitURL(e.target.value)}
                            placeholder="https://github.com/team/repo.git"
                          />
                        </Field>
                        <Field
                          label={t(
                            "分支 / Tag / Commit",
                            "Branch / tag / commit",
                          )}
                        >
                          <input
                            required
                            value={gitRef}
                            onChange={(e) => setGitRef(e.target.value)}
                          />
                        </Field>
                        <p className="muted">
                          {t(
                            "允许的 Git 域名由服务端配置；固定实际 Commit，不运行构建脚本。",
                            "Server-configured Git hosts only. Pins the actual commit and runs no build scripts.",
                          )}
                          <br />
                          {(me.git_hosts || []).join(", ")}
                        </p>
                      </>
                    ) : (
                      <div className="upload-area">
                        <FolderUp size={30} />
                        <h3>
                          {sourceMode === "zip"
                            ? t("选择代码仓 ZIP 包", "Choose a repository ZIP")
                            : t(
                                "选择并上传本地目录",
                                "Choose a local directory to upload",
                              )}
                        </h3>
                        {sourceMode === "zip" ? (
                          <input
                            aria-label="ZIP"
                            type="file"
                            accept=".zip"
                            required
                            onChange={(e) =>
                              setZip(e.target.files?.[0] || null)
                            }
                          />
                        ) : (
                          <input
                            aria-label="Directory"
                            type="file"
                            {...({ webkitdirectory: "", directory: "" } as any)}
                            multiple
                            required
                            onChange={(e) =>
                              setDirectory(Array.from(e.target.files || []))
                            }
                          />
                        )}
                        <p className="muted">
                          {t(
                            "仅上传选择的文件内容，不把浏览器路径当作服务器路径。",
                            "Uploads selected contents; browser paths are never treated as server filesystem paths.",
                          )}
                        </p>
                      </div>
                    )}
                    <div className="note">
                      {t(
                        "支持 UTF-8 Java、Markdown、TXT、SQL、XML。单文件 ≤1 MiB，累计 ≤25 MiB，≤1000文件；排除 .git、构建输出与 .env。完整内容清单固定后才可进入解析。",
                        "UTF-8 Java, Markdown, TXT, SQL, XML. ≤1 MiB per file, ≤25 MiB total, ≤1000 files. Excludes .git, build output and .env. Analysis uses a fixed content manifest.",
                      )}
                    </div>
                    <button className="primary" disabled={busy || !builder}>
                      <Database size={15} />
                      {t("预检并创建快照", "Preflight & capture snapshot")}
                    </button>
                  </form>
                </Panel>
                <Panel
                  title={t("已固定的来源快照", "Captured snapshots")}
                  extra={<span className="badge">{snapshots.length}</span>}
                >
                  {snapshots.length === 0 ? (
                    <Empty
                      text={t(
                        "等待上传代码或提供 Git 链接。",
                        "Upload code or provide a Git URL to begin.",
                      )}
                    />
                  ) : (
                    snapshots.map((s) => (
                      <div className="source-item" key={s.id}>
                        <div className="row">
                          <div className="entity-icon">
                            <GitBranch size={17} />
                          </div>
                          <div>
                            <strong>{s.name}</strong>
                            <small className="muted">
                              {s.mode} · {s.files.length} {t("文件", "files")}
                            </small>
                          </div>
                          {status(s.status)}
                        </div>
                        <dl>
                          <dt>Snapshot</dt>
                          <dd title={s.id}>{short(s.id)}</dd>
                          <dt>Commit</dt>
                          <dd>
                            {s.commit
                              ? short(s.commit)
                              : t(
                                  "上传内容，不声明 Git Commit",
                                  "Uploaded content, no Git commit claimed",
                                )}
                          </dd>
                          <dt>Hash</dt>
                          <dd title={s.content_hash}>
                            {short(s.content_hash)}
                          </dd>
                        </dl>
                        <button
                          disabled={!builder}
                          onClick={() => {
                            setSnapshotId(s.id);
                            setJobType("ANALYZE");
                            setPage("tasks");
                          }}
                        >
                          {t("创建解析任务", "Create analysis job")}{" "}
                          <ArrowRight size={14} />
                        </button>
                      </div>
                    ))
                  )}
                </Panel>
              </div>
            </>
          )}

          {page === "settings" && (
            <nav className="tabs" aria-label={t("配置分类", "Configuration categories")}>
              {([
                ["models", t("模型连接与验证", "Model connections & validation")],
                ["extraction", t("抽取与执行参数", "Extraction & execution parameters")],
                ["engine", t("CodeGraph 解析引擎", "CodeGraph analysis engine")],
                ["workspace", t("Agent 工作区校验", "Agent workspace verification")],
                ["appearance", t("界面偏好", "Appearance & language")],
                ["account", t("账号与密码", "Account & password")],
              ] as const).map(([section, label]) => (
                <button key={section} className={settingsSection === section ? "active" : ""}
                  aria-current={settingsSection === section ? "page" : undefined}
                  onClick={() => { setSettingsSection(section); setError(""); setMessage(""); }}>
                  {label}
                </button>
              ))}
            </nav>
          )}
          {page === "settings" && settingsSection === "workspace" && <WorkspaceSettings token={token} lang={lang}/>}
          {page === "settings" && settingsSection === "engine" && <EngineSettings token={token} lang={lang} admin={!!admin}/>}
          {page === "settings" && settingsSection === "account" && (
            <Panel title={t("账号与密码", "Account & password")}>
              <p className="muted">{t("当前账号：", "Signed in as: ")}{me.username || me.role}</p>
              {me.auth_method === "password" ? <form onSubmit={e => {
                e.preventDefault();
                if (passwordDraft.next !== passwordDraft.confirm) {
                  setError(t("两次输入的新密码不一致。", "New passwords do not match.")); return;
                }
                act(async () => {
                  await api("/auth/password", "POST", {current_password: passwordDraft.current, new_password: passwordDraft.next});
                  clearSession();
                  setMessage(t("密码已修改，所有账号会话已失效，请使用新密码登录。", "Password changed. All account sessions were invalidated. Sign in with the new password."));
                }, false);
              }}>
                <div className="form-grid">
                  <Field label={t("当前密码", "Current password")}>
                    <input type="password" autoComplete="current-password" required maxLength={1024} value={passwordDraft.current} onChange={e => setPasswordDraft({...passwordDraft, current: e.target.value})} />
                  </Field>
                  <Field label={t("新密码（至少8位）", "New password (at least 8 characters)")}>
                    <input type="password" autoComplete="new-password" required minLength={8} maxLength={1024} value={passwordDraft.next} onChange={e => setPasswordDraft({...passwordDraft, next: e.target.value})} />
                  </Field>
                  <Field label={t("确认新密码", "Confirm new password")}>
                    <input type="password" autoComplete="new-password" required minLength={8} maxLength={1024} value={passwordDraft.confirm} onChange={e => setPasswordDraft({...passwordDraft, confirm: e.target.value})} />
                  </Field>
                </div>
                <p className="muted">{t("修改后所有该账号的登录会话将失效，需要重新登录。", "Changing the password invalidates all sessions for this account and requires signing in again.")}</p>
                <button className="primary" disabled={busy}>{t("修改密码", "Change password")}</button>
              </form> : <p className="muted">{t("请使用用户名和密码登录后修改账号密码。", "Sign in with a username and password to change the account password.")}</p>}
            </Panel>
          )}
          {page === "settings" && settingsSection === "appearance" && (
            <Panel title={t("界面偏好", "Appearance & language")}>
              <div className="form-grid">
                <Field label={t("界面语言", "Interface language")}>
                  <select value={lang} onChange={e => setLang(e.target.value)}>
                    <option value="zh">简体中文</option><option value="en">English</option>
                  </select>
                </Field>
                <Field label={t("背景主题", "Color theme")}>
                  <select value={theme} onChange={e => setTheme(e.target.value)}>
                    <option value="light">{t("浅色", "Light")}</option>
                    <option value="dark">{t("深色", "Dark")}</option>
                    <option value="system">{t("跟随系统", "System")}</option>
                  </select>
                </Field>
              </div>
              <p className="muted">{t("修改立即生效，并自动保存在当前浏览器。登录页也可切换语言和主题。", "Changes apply immediately and are saved in this browser. Language and theme can also be changed on the sign-in page.")}</p>
            </Panel>
          )}
          {page === "settings" && settingsSection === "models" && (
            <div className="two-columns">
              <Panel
                title={t(
                  "新增不可变模型配置",
                  "New immutable model configuration",
                )}
              >
                <form
                  onSubmit={(e) => {
                    e.preventDefault();
                    act(async () => {
                      const m = await api("/model-connections", "POST", {
                        ...modelDraft,
                        credential_mode: credentialMode,
                        ...(credentialMode === "api_key"
                          ? { api_key: apiKey }
                          : {}),
                      });
                      setApiKey("");
                      setModelId(m.id);
                      setMessage(
                        t(
                          "配置已保存，尚未验证。",
                          "Configuration saved; not validated yet.",
                        ),
                      );
                    });
                  }}
                >
                  <div
                    className="toolbar"
                    style={{ marginBottom: 16, marginTop: 0 }}
                  >
                    <button
                      type="button"
                      disabled={busy || !admin}
                      onClick={() => {
                        setModelDraft((d) => ({
                          ...d,
                          name: "DeepSeek",
                          endpoint: "https://api.deepseek.com",
                          model_id: "deepseek-flash",
                        }));
                        setHostConsent(false);
                        setApiKey("");
                      }}
                    >
                      {t("使用 DeepSeek 配置", "Use DeepSeek settings")}
                    </button>
                  </div>
                  <div className="form-grid">
                    {[
                      ["name", t("连接名称", "Connection name")],
                      ["endpoint", t("API 基础地址", "API base URL")],
                      ["model_id", t("模型 ID", "Model ID")],
                    ].map(([k, n]) => (
                      <Field key={k} label={n}>
                        <input
                          required
                          value={String((modelDraft as Row)[k])}
                          onChange={(e) => {
                            if (k === "endpoint") setHostConsent(false);
                            setModelDraft({
                              ...modelDraft,
                              [k]: e.target.value,
                            });
                          }}
                        />
                      </Field>
                    ))}
                    <Field label={t("凭据方式", "Credential mode")}>
                      <select
                        value={credentialMode}
                        onChange={(e) => {
                          setCredentialMode(
                            e.target.value as "api_key" | "environment",
                          );
                          setApiKey("");
                        }}
                      >
                        <option value="api_key">
                          {t("直接填写 API Key", "Direct API Key")}
                        </option>
                        <option value="environment">
                          {t(
                            "引用环境变量（高级）",
                            "Environment variable (advanced)",
                          )}
                        </option>
                      </select>
                    </Field>
                    {credentialMode === "api_key" ? (
                      <Field label="API Key">
                        <input
                          type="password"
                          autoComplete="new-password"
                          spellCheck={false}
                          maxLength={4096}
                          required
                          value={apiKey}
                          placeholder={t(
                            "在此填写密钥，保存后不会回显",
                            "Enter key here; never returned after saving",
                          )}
                          onChange={(e) => setApiKey(e.target.value)}
                        />
                      </Field>
                    ) : (
                      <Field
                        label={t(
                          "密钥环境变量名",
                          "API key environment variable",
                        )}
                      >
                        <input
                          required
                          pattern="[A-Z][A-Z0-9_]{0,100}"
                          title={t(
                            "填写变量名称，如 BOS_MODEL_API_KEY；不要填写实际 API Key",
                            "Variable name, e.g. BOS_MODEL_API_KEY; never the actual key",
                          )}
                          maxLength={101}
                          value={modelDraft.credential_env}
                          onChange={(e) =>
                            setModelDraft({
                              ...modelDraft,
                              credential_env: e.target.value,
                            })
                          }
                        />
                      </Field>
                    )}
                    {[
                      [
                        "max_input_chars",
                        t("每次调用最大输入字符预算", "Maximum input characters per call"),
                      ],
                      [
                        "max_output_tokens",
                        t("最大输出 Token", "Maximum output tokens"),
                      ],
                      ["temperature", t("温度", "Temperature")],
                    ].map(([k, n]) => (
                      <Field key={k} label={n}>
                        <input
                          type="number"
                          required
                          step={k === "temperature" ? "0.1" : "1"}
                          value={Number((modelDraft as Row)[k])}
                          onChange={(e) =>
                            setModelDraft({
                              ...modelDraft,
                              [k]: Number(e.target.value),
                            })
                          }
                        />
                      </Field>
                    ))}
                  </div>
                  <div className="note">
                    OpenAI-compatible ·{" "}
                    {t("允许的模型域名", "Allowed model hosts")}:{" "}
                    {(me.model_hosts || []).join(", ") ||
                      t("尚未登记模型主机", "No model hosts registered")}
                    <br />
                    {t(
                      "API Key 通过密码框提交，在本机服务端独立凭据库加密保存；配置、审计和返回结果仅保存引用，不回显密钥。高级模式填写的是环境变量名称，不是实际密钥。基础地址会自动拼接 /chat/completions。",
                      "API Keys are submitted through a password field and encrypted in a separate local server vault. Configurations, audit and responses contain references only. Advanced mode expects a variable name, not the key. /chat/completions is appended to the base URL.",
                    )}
                  </div>
                  {endpointHost && !hostAllowed && (
                    <div className="note">
                      <strong>
                        {t("待登记主机", "Unregistered host")}: {endpointHost}
                      </strong>
                      {me.model_host_policy === "environment" ? (
                        <p>
                          {t(
                            "该部署已设置 BOS_MODEL_HOSTS 固定白名单，界面不能扩大范围。请由部署管理员添加此主机并重启；当前表单会保留。",
                            "BOS_MODEL_HOSTS is configured by this deployment and cannot be expanded here. Ask the deployment administrator to add this host and restart. Your draft is preserved.",
                          )}
                        </p>
                      ) : (
                        <>
                          <label className="check">
                            <input
                              type="checkbox"
                              checked={hostConsent}
                              disabled={!admin || busy}
                              onChange={(e) => setHostConsent(e.target.checked)}
                            />
                            {t(
                              "我确认这是允许平台调用的模型主机（仅登记此确切主机）",
                              "I authorize the platform to call this exact model host",
                            )}
                          </label>
                          <button
                            type="button"
                            disabled={!admin || busy || !hostConsent}
                            onClick={() =>
                              act(async () => {
                                await api("/model-endpoint-hosts", "POST", {
                                  host: endpointHost,
                                });
                                setMe(await api("/me"));
                                setHostConsent(false);
                                setMessage(
                                  t(
                                    "模型主机已登记并持久保存。现在可以保存连接；登记本身不会调用模型。",
                                    "Model host registered persistently. You can now save the connection. Registration itself does not call the model.",
                                  ),
                                );
                              })
                            }
                          >
                            {t("登记此模型主机", "Register model host")}
                          </button>
                        </>
                      )}
                    </div>
                  )}
                  <label className="check">
                    <input
                      type="checkbox"
                      checked={modelDraft.allow_source}
                      onChange={(e) =>
                        setModelDraft({
                          ...modelDraft,
                          allow_source: e.target.checked,
                        })
                      }
                    />
                    {t(
                      "确认此连接允许接收本工作区上传的样例源码与文档",
                      "This connection is allowed to receive uploaded sample code and documents in this workspace",
                    )}
                  </label>
                  <button
                    className="primary"
                    disabled={
                      busy ||
                      !admin ||
                      (credentialMode === "api_key"
                        ? !apiKey.trim()
                        : !/^[A-Z][A-Z0-9_]{0,100}$/.test(
                            modelDraft.credential_env,
                          ))
                    }
                  >
                    {t("保存配置", "Save configuration")}
                  </button>
                </form>
              </Panel>
              <Panel
                title={t(
                  "配置与能力验证",
                  "Configuration & capability validation",
                )}
              >
                {!admin ? (
                  <Empty
                    text={t(
                      "模型配置由管理员管理。",
                      "Model configuration is managed by administrators.",
                    )}
                  />
                ) : models.length === 0 ? (
                  <Empty
                    text={t(
                      "尚未接入模型。不会使用模拟连接代替真实验证。",
                      "No models configured. Simulated connections are never marked validated.",
                    )}
                  />
                ) : (
                  models.map((m) => (
                    <div className="source-item" key={m.id}>
                      <div className="row between">
                        <strong>{m.name}</strong>
                        <span
                          className={
                            "badge " +
                            (!m.eligibility_error ? "success" : "warn")
                          }
                        >
                          {!m.eligibility_error
                            ? t("正式抽取可用", "Eligible")
                            : m.eligibility_error}
                        </span>
                      </div>
                      <p>
                        {m.model_id} · r{m.revision}
                      </p>
                      <code>{m.endpoint}</code>
                      <p className="muted">
                        {short(m.fingerprint)} ·{" "}
                        {t("结构化规则抽取用途", "Rule extraction purpose")}
                      </p>
                      {m.validation && (
                        <>
                          <div className="validation-grid">
                            {Object.entries(m.validation.checks).map(
                              ([k, v]) => (
                                <div key={k}>
                                  <span>{k}</span>
                                  <strong>{String(v)}</strong>
                                </div>
                              ),
                            )}
                          </div>
                          <p className="muted">
                            {t("有效至", "Valid until")}:{" "}
                            {new Date(m.validation.expires_at).toLocaleString()}
                          </p>
                        </>
                      )}
                      <div className="row">
                        <button
                          disabled={busy || !m.enabled}
                          onClick={() =>
                            act(async () => {
                              const result = await api(
                                "/model-connections/" + m.id + "/validations",
                                "POST",
                              );
                              setMessage(
                                result.eligible
                                  ? t(
                                      "最小样例验证通过；不代表总体抽取质量达标。",
                                      "Fixture validation passed; this does not establish overall extraction quality.",
                                    )
                                  : t("验证未通过：", "Validation failed: ") +
                                      (modelErrorHint(result.error, lang) ||
                                        result.error) +
                                      " · " +
                                      result.error,
                              );
                            })
                          }
                        >
                          {t("真实能力验证", "Run real validation")}
                        </button>
                        <button
                          disabled={busy || !m.enabled}
                          onClick={() =>
                            act(async () => {
                              await api(
                                "/model-connections/" + m.id + "/disable",
                                "POST",
                              );
                            })
                          }
                        >
                          {t("停用", "Disable")}
                        </button>
                      </div>
                    </div>
                  ))
                )}
              </Panel>
            </div>
          )}

          {page === "settings" && settingsSection === "extraction" && (
            <Panel title={t("抽取与执行参数", "Extraction & execution parameters")}>
              <form onSubmit={e => {
                e.preventDefault();
                act(async () => {
                  const saved = await api("/settings/extraction", "PUT", {max_source_lines: Number(settingsDraft ?? extractionDefaults.max_source_lines), model_timeout_seconds: Number(policyDraft.model_timeout_seconds ?? extractionDefaults.model_timeout_seconds ?? 120), transient_retries: Number(policyDraft.transient_retries ?? extractionDefaults.transient_retries ?? 2), retry_backoff_seconds: Number(policyDraft.retry_backoff_seconds ?? extractionDefaults.retry_backoff_seconds ?? 5), expected_revision: extractionDefaults.revision});
                  setExtractionDefaults(saved);
                  setSettingsDraft(null);
                  setPolicyDraft({});
                  setMessage(t("配置已保存，仅影响后续新建及重试任务；已有任务继续使用各自快照。", "Settings saved for newly created and retry jobs. Existing jobs keep their pinned settings."));
                });
              }}>
                <div className="form-grid">
                  <Field label={t("每次模型调用最多源码/文档行数", "Maximum source/document lines per model call")}>
                    <input type="number" min={1} max={2000} step={1} required disabled={!admin} value={settingsDraft ?? String(extractionDefaults.max_source_lines)} onChange={e => setSettingsDraft(e.target.value)} />
                  </Field>
                  {([
                    ["model_timeout_seconds", t("模型响应等待超时（秒）", "Model response timeout (seconds)"), 10, 300, 120],
                    ["transient_retries", t("临时故障额外重试次数", "Additional retries for transient failures"), 0, 5, 2],
                    ["retry_backoff_seconds", t("重试退避基数（秒）", "Retry backoff base (seconds)"), 1, 60, 5],
                  ] as const).map(([key, label, min, max, fallback]) => <Field key={key} label={label}>
                    <input type="number" min={min} max={max} step={1} required disabled={!admin} value={policyDraft[key] ?? extractionDefaults[key] ?? fallback} onChange={e => setPolicyDraft({...policyDraft, [key]: e.target.value})} />
                  </Field>)}
                </div>
                <p className="muted">{t("当前默认：", "Current default: ")}{extractionDefaults.max_source_lines}{t(" 行；配置版本：", " lines; settings revision: ")}{extractionDefaults.revision || t("内置默认", "built-in default")}</p>
                <div className="note">
                  <p>{t("默认 100 行，范围 1–2000 行。这是每次模型请求中所有文件的源码与文档总行数上限，包含空行和注释；整个任务会分为多个批次，不会只处理前 100 行。", "Default: 100 lines, range: 1–2000. This caps total source/document lines across all files in each model call, including comments and blank lines. Jobs process all selected content over multiple batches.")}</p>
                  <p>{t("同时受模型配置的输入字符预算约束，先达到任一上限就拆批。短方法优先完整发送；长方法按片段发送，并在候选中标注需核对完整条件与例外。较小批次会增加请求次数和重复上下文开销。", "Both the line cap and model input-character budget apply. Short methods are kept together when possible; long methods are split and their candidates require full-method review. Smaller batches increase request counts and repeated context overhead.")}</p>
                  <p>{t("代码 AST 解析仍在本地执行。只对模型规则抽取应用此设置。修改后，请重新创建任务或从失败任务创建重试，并核对新的批次计划。", "AST parsing remains local. This setting applies to model rule extraction. Create a new or retry job after changes and review its batch plan.")}</p>
                  <p>{t("临时连接故障、429及500/502/503/504会进行有限次数的指数退避重试；退避期间释放Worker，不持续占用等待。校验失败隔离当前批次并继续后续批次。响应超时或连接中断可能已被提供方处理，标记未知，不自动重复付费请求。", "Connection failures, 429 and 500/502/503/504 use bounded exponential backoff. Waiting releases the worker. Invalid batches are quarantined while others continue. Response timeouts or interruptions may already have been processed; unknown requests are not replayed automatically.")}</p>
                </div>
                <button className="primary" type="submit" disabled={busy || !admin || !Number.isInteger(Number(settingsDraft ?? extractionDefaults.max_source_lines)) || Number(settingsDraft ?? extractionDefaults.max_source_lines) < 1 || Number(settingsDraft ?? extractionDefaults.max_source_lines) > 2000}>{t("保存抽取配置", "Save extraction settings")}</button>
                {!admin && <p className="muted">{t("仅管理员可以修改；其他角色可查看。", "Only administrators can change these settings.")}</p>}
              </form>
            </Panel>
          )}
          {page === "tasks" && (
            <>
              <Panel title={t("创建知识任务", "Create knowledge job")}>
                <div className="form-grid">
                  <Field label={t("类型", "Type")}>
                    <select
                      value={jobType}
                      onChange={(e) => setJobType(e.target.value)}
                    >
                      <option value="ANALYZE">
                        {t("CodeGraph / Java 代码解析", "CodeGraph / Java analysis")}
                      </option>
                      <option value="EXTRACT">
                        {t("模型规则抽取", "Model rule extraction")}
                      </option>
                    </select>
                  </Field>
                  <Field label={t("固定来源快照", "Fixed source snapshot")}>
                    <select
                      required
                      value={snapshotId}
                      onChange={(e) => {
                        setSnapshotId(e.target.value);
                        setProjectionId("");
                      }}
                    >
                      <option value="">{t("请选择", "Select")}</option>
                      {snapshots.map((s) => (
                        <option key={s.id} value={s.id}>
                          {s.name} · {short(s.id)}
                        </option>
                      ))}
                    </select>
                  </Field>
                  {jobType === "EXTRACT" && (
                    <>
                      <Field label={t("代码事实投影", "Code fact projection")}>
                        <select
                          value={projectionId}
                          onChange={(e) => setProjectionId(e.target.value)}
                        >
                          <option value="">{t("请选择", "Select")}</option>
                          {projections
                            .filter((p) => p.snapshot_id === snapshotId)
                            .map((p) => (
                              <option key={p.id} value={p.id}>
                                {p.analyzer} · {short(p.id)} · {p.entity_count} entities
                              </option>
                            ))}
                        </select>
                      </Field>
                      <Field
                        label={t(
                          "已验证模型配置",
                          "Validated model configuration",
                        )}
                      >
                        <select
                          value={modelId}
                          onChange={(e) => setModelId(e.target.value)}
                        >
                          <option value="">{t("请选择", "Select")}</option>
                          {models.map((m) => (
                            <option
                              key={m.id}
                              value={m.id}
                              disabled={
                                !!m.eligibility_error || !m.allow_source
                              }
                            >
                              {m.name} · {m.eligibility_error || "PASS"}
                            </option>
                          ))}
                        </select>
                      </Field>
                    </>
                  )}
                </div>
                <div className="toolbar">
                  <button
                    className="primary"
                    disabled={
                      busy ||
                      !builder ||
                      !snapshotId ||
                      (jobType === "EXTRACT" && (!projectionId || !modelId))
                    }
                    onClick={() => act(createJob)}
                  >
                    {t("保存任务，等待确认", "Create job for confirmation")}
                  </button>
                  {jobType === "EXTRACT" && <button onClick={() => {
                    setPage("settings"); setSettingsSection("models"); setError(""); setMessage("");
                  }}>{t("前往配置中心管理模型", "Manage models in Configuration center")}</button>}
                  <span className="muted">
                    {t(
                      "解析使用配置中心指定的引擎，不运行项目构建。抽取会调用指定真实模型。",
                      "Analysis uses the configured engine without running project builds. Extraction invokes the selected real model.",
                    )}
                    {jobType === "EXTRACT" && t(` 当前每次最多 ${extractionDefaults.max_source_lines} 行，可在配置中心调整。`, ` Current limit: ${extractionDefaults.max_source_lines} lines per call; adjustable in Configuration center.`)}
                  </span>
                </div>
              </Panel>
              <div className="two-columns">
                <Panel title={t("任务列表", "Jobs")}>
                  {jobs.length === 0 ? (
                    <Empty
                      text={t(
                        "暂无任务，请先上传代码。",
                        "No jobs. Upload code first.",
                      )}
                    />
                  ) : (
                    <div className="scroll">
                      <table>
                        <thead>
                          <tr>
                            <th>{t("任务", "Job")}</th>
                            <th>{t("状态", "Status")}</th>
                            <th>{t("类型", "Type")}</th>
                          </tr>
                        </thead>
                        <tbody>
                          {jobs.map((j) => (
                            <tr key={j.id}>
                              <td>
                                <button
                                  className="link"
                                  onClick={() => setJobId(j.id)}
                                >
                                  {short(j.id)}
                                </button>
                              </td>
                              <td>{status(j.status)}</td>
                              <td>{j.type}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  )}
                </Panel>
                <Panel
                  title={t("任务详情与确认", "Job details & confirmation")}
                >
                  {activeJob ? (
                    <>
                      <dl>
                        <dt>ID</dt>
                        <dd>{activeJob.id}</dd>
                        <dt>{t("阶段", "Stage")}</dt>
                        <dd>{activeJob.stage}</dd>
                        <dt>Snapshot</dt>
                        <dd>{short(activeJob.snapshot_id)}</dd>
                        <dt>{t("配置", "Configuration")}</dt>
                        <dd>
                          {activeJob.model_config
                            ? activeJob.model_config.model_id +
                              " · " +
                              short(activeJob.model_config.fingerprint)
                            : "javac-ast-1"}
                        </dd>
                      </dl>
                      {status(activeJob.status)}
                      {activeJob.batch_summary && <div className="note">
                        {t("批次状态：成功 ", "Batch states: successful ")}{activeJob.batch_summary.SUCCEEDED}
                        {t("，校验隔离 ", ", invalid ")}{activeJob.batch_summary.INVALID}
                        {t("，重试耗尽 ", ", failed ")}{activeJob.batch_summary.FAILED}
                        {t("，未知 ", ", unknown ")}{activeJob.batch_summary.UNKNOWN}
                        {t("，等待重试 ", ", retry scheduled ")}{activeJob.batch_summary.RETRY_WAIT}
                        {t("，暂停 ", ", held ")}{activeJob.batch_summary.HELD||0}
                        {t("，跳过 ", ", skipped ")}{activeJob.batch_summary.SKIPPED||0}
                        {activeJob.status === "WAITING_SUBTASKS" && <p>{t("请到子任务管理继续暂停或未调度的范围；显式跳过会保留覆盖缺口。", "Resume held or undispatched scopes in Subtasks. Explicitly skipping retains a coverage gap.")}</p>}
                        {activeJob.next_retry_at && <p>{t("下次重试时间：", "Next retry: ")}{new Date(activeJob.next_retry_at).toLocaleString()}</p>}
                      </div>}
                      {(activeJob.batch_summary?.UNKNOWN > 0 || activeJob.status === "EXECUTION_UNKNOWN") && <label className="check note">
                        <input type="checkbox" checked={replayUnknownJob === activeJob.id} onChange={e => setReplayUnknownJob(e.target.checked ? activeJob.id : "")} />
                        <span>{t("创建重试任务时包含未知批次。我了解原请求可能已被提供方处理，重发可能重复计费；未勾选时，同一计划会保留未知批次并只重试已知失败批次。", "Include unknown batches in the retry job. The provider may already have processed them, so replay may charge again. Otherwise unknown batches are retained for the same plan and only known failures are retried.")}</span>
                      </label>}
                      {activeJob.extraction_plan && (
                        <div className="notice batch-plan">
                          <strong>{t("分批抽取计划", "Extraction batch plan")}</strong>
                          {activeJob.execution_policy && <p>{t("本任务执行策略：响应等待 ", "Pinned execution policy: response timeout ")}{activeJob.execution_policy.model_timeout_seconds}{t(" 秒，临时故障最多额外重试 ", " seconds; up to ")}{activeJob.execution_policy.transient_retries}{t(" 次，退避基数 ", " additional transient retries; backoff base ")}{activeJob.execution_policy.retry_backoff_seconds}{t(" 秒。剩余自动请求上限（含重试）：", " seconds. Remaining automatic request ceiling including retries: ")}{activeJob.remaining_attempts_upper_bound}{t(" 次。重试可能增加调用费用。", " calls. Retries may increase cost.")}</p>}
                          {!activeJob.extraction_plan.max_source_lines && <p>{t("这是旧版任务，保留原字符预算计划。若要使用配置中心的行数上限，请创建新的抽取任务或从失败任务创建重试。", "This legacy job keeps its original character-based plan. Create a new or retry job to use the line cap from Configuration center.")}</p>}
                          {activeJob.extraction_plan.max_source_lines && <p>{t("本任务固定行数上限：每次 ", "Pinned line cap: ")}{activeJob.extraction_plan.max_source_lines}{t(" 行源码/文档（含空行和注释），并同时遵守字符预算。", " source/document lines per call, including comments and blank lines; the character budget also applies.")}</p>}
                          {activeJob.extraction_plan.partial_method_batches > 0 && <p>{t("包含长方法片段的批次：", "Batches containing partial methods: ")}{activeJob.extraction_plan.partial_method_batches}{t("。相关候选会强制标注完整性待核对。", ". Their candidates require full-method review.")}</p>}
                          <p>{t("已完成", "Completed")} {activeJob.extraction_plan.completed_batches} / {activeJob.extraction_plan.total_batches}
                            {t(" 批；每次输入上限 ", " batches; per-call input limit ")}{activeJob.extraction_plan.max_input_chars}
                            {t(" 字符（序列化后的用户消息，含源码、实体及契约）。", " characters (serialized user message including source, entities and schema).")}</p>
                          <p>{t("尚未成功的范围批次：", "Batches not yet successful: ")}{activeJob.extraction_plan.total_batches - activeJob.extraction_plan.completed_batches}
                            {t("；每次最多输出 ", "; up to ")}{activeJob.extraction_plan.max_output_tokens_per_call}
                            {t(" Token；不含重试的基础输出上限合计 ", " output tokens per call; base output ceiling excluding retries ")}{activeJob.extraction_plan.max_total_output_tokens} Token。</p>
                          <p>{t("确认启动后逐批调用模型，可能产生费用。保留原始行号及已校验批次。新版任务隔离异常批次并继续执行，其余已校验结果可生成带覆盖缺口标记的候选；部分完成不代表全部成功。旧任务仍使用原执行方式。跨批次业务依赖需人工核对。", "Confirm start runs sequential model calls and may incur charges. New jobs quarantine bad batches and continue. Validated output can become candidates with explicit coverage gaps; partial completion is not complete success. Legacy jobs retain their original execution. Review cross-batch dependencies manually.")}</p>
                          <details>
                            <summary>{t("查看批次源码范围", "View batch source ranges")}</summary>
                            {activeJob.extraction_plan.batches.map((batch: Row, i: number) => (
                              <p key={i}>{i + 1}. {batch.source_lines !== undefined && `${batch.source_lines} ${t("行", "lines")} · `}{batch.input_chars} {t("字符", "characters")} · {Object.entries(batch.ranges).map(([path, range]) => `${path}:${(range as Row).start_line}–${(range as Row).end_line}`).join(" · ")}</p>
                            ))}
                          </details>
                        </div>
                      )}
                      <div className="toolbar">
                        {activeJob.type === "EXTRACT" && ["FAILED", "PARTIAL_SUCCEEDED", "NEEDS_ATTENTION", "EXECUTION_UNKNOWN"].includes(activeJob.status) && (
                          <button disabled={busy || !builder} onClick={() => act(async () => {
                            const result = await api("/knowledge-jobs/" + activeJob.id + "/retry", "POST", {expected_revision: activeJob.revision, include_unknown: replayUnknownJob === activeJob.id});
                            setJobId(result.id);
                            setMessage(t("重试任务已创建。请核对批次数，再确认启动；已校验批次会在计划与配置一致时复用。", "Retry job created. Review the batch count before confirming start. Validated batches are reused only when the plan and configuration match."));
                          })}>{t("创建重试任务", "Create retry job")}</button>
                        )}
                        {[
                          [
                            "START",
                            t("确认启动", "Confirm start"),
                            ["CREATED"],
                          ],
                          ["PAUSE", t("暂停", "Pause"), ["QUEUED", "WAITING_RETRY", "RUNNING"]],
                          ["RESUME", t("继续", "Resume"), ["PAUSED", "WAITING_SUBTASKS"]],
                          [
                            "CANCEL",
                            t("请求取消", "Request cancel"),
                            [
                              "CREATED",
                              "QUEUED",
                              "WAITING_RETRY",
                              "WAITING_SUBTASKS",
                              "RUNNING",
                              "PAUSED",
                              "PAUSE_REQUESTED",
                            ],
                          ],
                        ].map(
                          ([cmd, name, states]) =>
                            (states as string[]).includes(activeJob.status) && (
                              <button
                                key={cmd as string}
                                className={cmd === "START" ? "primary" : ""}
                                disabled={busy || !builder}
                                onClick={() =>
                                  act(async () => {
                                    await api(
                                      "/knowledge-jobs/" +
                                        activeJob.id +
                                        "/commands",
                                      "POST",
                                      {
                                        command: cmd,
                                        expected_revision: activeJob.revision,
                                      },
                                    );
                                  })
                                }
                              >
                                {name as string}
                              </button>
                            ),
                        )}
                      </div>
                      {activeJob.error && (
                        <div className="notice error task-error">
                          <strong>{activeJob.error}</strong>
                          <p>{modelErrorHint(activeJob.error, lang)}</p>
                          {activeJob.error_params && Object.keys(activeJob.error_params).length > 0 && <pre>{JSON.stringify(activeJob.error_params, null, 2)}</pre>}
                        </div>
                      )}
                      {activeJob.type === "ANALYZE" && <p className="note">{t("固定解析引擎：", "Pinned analysis engine: ")}{activeJob.analyzer_engine || "javac (legacy)"}</p>}
                      {activeJob.type === "EXTRACT" && <div className="note">
                        <button onClick={() => setPage("subtasks")}>{t("打开子任务管理", "Open subtask manager")}</button>
                        <p>{t("按状态和源码范围筛选子任务，查看尝试记录，单独或批量重试失败范围。", "Filter subtasks by state and source scope, inspect attempt history, and retry individual or selected failed scopes.")}</p>
                      </div>}
                      {activeJob.checkpoint?.evidence_line_corrections?.length > 0 && <details className="note">
                        <summary>{t("查看原文引用行号校正记录", "View exact-quote line corrections")}</summary>
                        <p>{t("仅校正当前批次中唯一逐字原文的行号，不修改引用文本；校正后仍须通过源码与实体映射校验。", "Only line numbers of unique verbatim quotes within the batch are corrected. Quote text stays unchanged; source and entity mapping checks still apply.")}</p>
                        <pre>{JSON.stringify(activeJob.checkpoint.evidence_line_corrections, null, 2)}</pre>
                      </details>}
                      {activeJob.projection_id && (
                        <button
                          onClick={() => {
                            setProjectionId(activeJob.projection_id);
                            setGraphJobId(activeJob.type === "EXTRACT" ? activeJob.id : "");
                            setView(activeJob.type === "EXTRACT" ? "mapping" : "code");
                            act(async () => {setGraph(await api("/code/subgraph?include_candidates=true&limit=80&projection_id=" + encodeURIComponent(activeJob.projection_id) + (activeJob.type === "EXTRACT" ? "&job_id=" + encodeURIComponent(activeJob.id) : "")));setNode(null);});
                            setPage("graph");
                            setGraphRelease("");
                          }}
                        >
                          {t(
                            "查看业务本体与代码拓扑预览",
                            "Preview ontology & code topology",
                          )}
                        </button>
                      )}
                      {activeJob.candidate_ids.length > 0 && (
                        <button
                          onClick={() => {
                            setPage("review");
                            const item = candidates.find(
                              (c) => c.id === activeJob.candidate_ids[0],
                            );
                            if (item) selectReview(item);
                          }}
                        >
                          {t("审核候选知识", "Review candidate knowledge")}
                        </button>
                      )}
                      <div className="timeline">
                        {activeJob.events.map((e: Row, i: number) => (
                          <div key={i}>
                            <span>{new Date(e.at).toLocaleTimeString()}</span>
                            {status(e.status)}
                          </div>
                        ))}
                      </div>
                      <div className="note">
                        {t(
                          "任务完成仅表示产物就绪，不能代替知识批准或发布。未知状态不会自动重跑模型请求。",
                          "Completion means artifacts are ready, not knowledge approval or publication. Unknown requests are not retried automatically.",
                        )}
                      </div>
                    </>
                  ) : (
                    <Empty
                      text={t(
                        "选择任务查看配置和实际状态。",
                        "Select a job to inspect its pinned configuration and actual state.",
                      )}
                    />
                  )}
                </Panel>
              </div>
            </>
          )}

          {page === "subtasks" && <SubtaskManager token={token} lang={lang} jobs={jobs} builder={!!builder} jobId={jobId} onJob={setJobId} onParent={id => {setJobId(id); setPage("tasks");}} />}
          {page === "review" && (
            <div className="review-layout">
              <Panel title={t("候选规则", "Candidate rules")}>
                {candidates.length === 0 ? (
                  <Empty
                    text={t(
                      "真实模型抽取完成后，候选将在这里出现。",
                      "Candidates appear after real model extraction.",
                    )}
                  />
                ) : (
                  candidates.map((c) => (
                    <button
                      key={c.id}
                      className={
                        "list-item " + (c.id === reviewId ? "selected" : "")
                      }
                      onClick={() => selectReview(c)}
                    >
                      <strong>{c.name}</strong>
                      {status(c.status)}
                      <small>
                        r{c.content_revision} · {short(c.snapshot_id)}
                      </small>
                    </button>
                  ))
                )}
              </Panel>
              <Panel title={t("规则与原始证据", "Rule & original evidence")}>
                {selectedReview ? (
                  <>
                    <div className="row between">
                      <strong>{selectedReview.name}</strong>
                      {status(selectedReview.status)}
                    </div>
                    <p className="muted">
                      {selectedReview.id} · {t("内容修订", "Content revision")}{" "}
                      {selectedReview.content_revision}
                    </p>
                    <div className="form-grid">
                      {[
                        ["name", t("名称", "Name")],
                        ["scope", t("适用范围", "Scope")],
                        ["condition", t("完整条件", "Complete condition")],
                        ["outcome", t("业务结果", "Outcome")],
                        [
                          "exceptions",
                          t("例外（每行一项）", "Exceptions (one per line)"),
                        ],
                        [
                          "uncertainties",
                          t(
                            "未解决问题（每行一项）",
                            "Uncertainties (one per line)",
                          ),
                        ],
                      ].map(([k, n]) => (
                        <Field key={k} label={n}>
                          <textarea
                            rows={k === "condition" || k === "outcome" ? 3 : 2}
                            value={reviewDraft[k] || ""}
                            readOnly={selectedReview.status === "PUBLISHED"}
                            onChange={(e) => {
                              setReviewDraft({
                                ...reviewDraft,
                                [k]: e.target.value,
                              });
                              setEvidenceChecked(false);
                            }}
                          />
                        </Field>
                      ))}
                    </div>
                    <div className="evidence-list">
                      {selectedReview.evidence.map((ev: Row, i: number) => (
                        <div key={i}>
                          <div className="row between">
                            <strong>
                              <FileCode2 size={14} /> {ev.path}:{ev.start_line}–
                              {ev.end_line}
                            </strong>
                            <button
                              onClick={() =>
                                act(async () => {
                                  setSourceEvidence(
                                    await api(
                                      "/source-snapshots/" +
                                        selectedReview.snapshot_id +
                                        "/files?path=" +
                                        encodeURIComponent(ev.path),
                                    ),
                                  );
                                })
                              }
                            >
                              {t("查看固定源码", "View pinned source")}
                            </button>
                          </div>
                          <pre>{ev.quote}</pre>
                        </div>
                      ))}
                    </div>
                    {sourceEvidence && (
                      <details open>
                        <summary>
                          {sourceEvidence.path} ·{" "}
                          {short(sourceEvidence.snapshot_id)}
                        </summary>
                        <pre className="code-lines">
                          {sourceEvidence.content
                            .split("\n")
                            .map(
                              (line: string, i: number) => `${i + 1}  ${line}`,
                            )
                            .join("\n")}
                        </pre>
                      </details>
                    )}
                    <Field
                      label={t(
                        "审核意见 / 修改说明",
                        "Review reason / edit note",
                      )}
                    >
                      <textarea
                        value={reviewReason}
                        onChange={(e) => setReviewReason(e.target.value)}
                        rows={2}
                      />
                    </Field>
                    <label className="check">
                      <input
                        type="checkbox"
                        checked={evidenceChecked}
                        onChange={(e) => setEvidenceChecked(e.target.checked)}
                      />
                      {t(
                        "已核对证据、实现映射、适用范围与例外；批准的是当前已保存内容",
                        "I reviewed evidence, implementation mapping, scope and exceptions. Approval applies to the saved content.",
                      )}
                    </label>
                    <div className="toolbar">
                      <button
                        disabled={
                          busy ||
                          !reviewer ||
                          !reviewReason ||
                          selectedReview.status === "PUBLISHED"
                        }
                        onClick={() => act(() => decision("EDIT"))}
                      >
                        {t("保存为新修订", "Save as new revision")}
                      </button>
                      <button
                        className="primary"
                        disabled={
                          busy ||
                          !reviewer ||
                          !reviewReason ||
                          !evidenceChecked ||
                          selectedReview.status === "PUBLISHED" ||
                          selectedReview.uncertainties.length > 0 ||
                          JSON.stringify(reviewDraft) !==
                            JSON.stringify({
                              name: selectedReview.name,
                              condition: selectedReview.condition,
                              outcome: selectedReview.outcome,
                              scope: selectedReview.scope,
                              exceptions: selectedReview.exceptions.join("\n"),
                              uncertainties:
                                selectedReview.uncertainties.join("\n"),
                            })
                        }
                        onClick={() => act(() => decision("APPROVE"))}
                      >
                        {t("批准当前修订", "Approve saved revision")}
                      </button>
                      <button
                        disabled={
                          busy ||
                          !reviewer ||
                          !reviewReason ||
                          selectedReview.status === "PUBLISHED"
                        }
                        onClick={() => act(() => decision("REJECT"))}
                      >
                        {t("拒绝", "Reject")}
                      </button>
                    </div>
                    <p className="muted">
                      {t(
                        "存在未解决问题时禁止批准；先保存修改，再重新核对批准。发布权限由管理员单独执行。",
                        "Unresolved uncertainties block approval. Save edits before reviewing again. Publication is a separate administrator action.",
                      )}
                    </p>
                  </>
                ) : (
                  <Empty
                    text={t(
                      "从左侧选择一条规则。",
                      "Select a rule from the left.",
                    )}
                  />
                )}
              </Panel>
            </div>
          )}

          {page === "ontology" && <OntologyEditor token={token} lang={lang} builder={!!builder} reviewer={!!reviewer} projections={projections} candidates={candidates} onGraph={id=>{setProjectionId(id);setGraphJobId("");setGraphRelease("");setView("mapping");setPage("graph");setGraph({nodes:[],edges:[]});}}/>}
          {page === "graph" && (
            <>
              <div className="note graph-intro">
                <strong>
                  {t(
                    "业务本体与代码事实，是相互关联的两层知识。",
                    "Business ontology and code facts are connected layers of knowledge.",
                  )}
                </strong>
                <p>
                  {t(
                    "业务规则由模型推荐、专家审核；类、方法与静态依赖来自真实 AST。IMPLEMENTED_BY 将规则关联到具体版本的方法。无法解析的动态调用会保留未知。布局坐标不表示业务语义。",
                    "Rules are proposed by models and reviewed by experts. Classes, methods and static dependencies come from real AST analysis. IMPLEMENTED_BY links rules to exact method versions. Dynamic targets remain unknown where unresolved. Layout positions have no business meaning.",
                  )}
                </p>
              </div>
              <Panel title={t("选择确切版本", "Select an exact version")}>
                <div className="toolbar">
                  <select
                    aria-label="Graph source"
                    value={graphRelease ? "release" : "projection"}
                    onChange={(e) => {
                      setGraphRelease(
                        e.target.value === "release"
                          ? pointer.release_id || releases[0]?.id || ""
                          : "",
                      );
                      setGraph({ nodes: [], edges: [] });
                    }}
                  >
                    <option value="projection">
                      {t("代码与候选本体预览", "Code & candidate ontology preview")}
                    </option>
                    <option value="release" disabled={!releases.length}>
                      {t("已发布知识", "Published knowledge")}
                    </option>
                  </select>
                  {graphRelease ? (
                    <select
                      value={graphRelease}
                      onChange={(e) => setGraphRelease(e.target.value)}
                    >
                      {releases.map((r) => (
                        <option value={r.id} key={r.id}>
                          {short(r.id)}
                        </option>
                      ))}
                    </select>
                  ) : (
                    <select
                      value={projectionId}
                      onChange={(e) => setProjectionId(e.target.value)}
                    >
                      <option value="">
                        {t("请选择投影", "Select projection")}
                      </option>
                      {projections.map((p) => (
                        <option value={p.id} key={p.id}>
                          {p.analyzer} · {short(p.id)} · {p.entity_count} entities
                        </option>
                      ))}
                    </select>
                  )}
                  <button
                    className="primary"
                    disabled={busy || (!graphRelease && !projectionId)}
                    onClick={() => act(loadGraph)}
                  >
                    {t("加载图谱", "Load graph")}
                  </button>
                </div>
              </Panel>
              {graph.preview && <div className="note"><strong>{t("候选业务本体预览 · 未审核、未发布", "Candidate ontology preview · Not reviewed or published")}</strong><p>{t("当前展示 ", "Showing ")}{graph.candidate_shown} / {graph.candidate_total}{t(" 条候选规则及其代码映射。规则节点可点击查看条件、结果、证据和不确定项；切换“业务—代码映射”查看关联。", " candidate rules with code mappings. Select a rule to inspect its conditions, outcomes, evidence and uncertainties. Use Business ↔ code to inspect links.")}</p><small>{graph.job_id}</small></div>}
              {!graphRelease && projections.filter(p=>p.id===graph.projection_id).map(p=><div className="note" key={p.id}><strong>{t("解析来源：", "Analysis source: ")}{p.analyzer}{p.codegraph?.version ? ' · Joern '+p.codegraph.version : ''}</strong><p>{p.entity_count} {t("个实体", "entities")} · {p.edge_count} {t("条关系", "edges")} · {p.unresolved_count} UNKNOWN{p.external_count!==undefined ? ' · '+p.external_count+' external calls' : ''}</p>{p.analyzer==='joern-codegraph'&&<small>{t("静态 CPG 声明目标不代表运行时动态派发；缺失源码范围单独诊断，完整 CPG 产物保留。旧规则仍绑定原投影。", "Static CPG declarations do not establish runtime dispatch. Missing source ranges are diagnosed separately; full CPG artifacts are retained. Existing rules remain pinned to their original projection.")}</small>}</div>)}
              {graph.backend && <p className="muted">{t("实际图查询后端：", "Graph query backend: ")}{graph.backend}</p>}
              {!graphRelease && node?.kind && graph.projection_id && <button disabled={busy} onClick={()=>act(expandGraph)}>{t("展开选中节点的一跳邻居", "Expand selected node neighbors")}</button>}
              {view === "mapping" && <p className="muted">{t("映射视图聚焦业务模块、概念、规则及其关联代码；完整代码节点请切换“代码实体拓扑”。", "Mapping view focuses on modules, concepts, rules and linked code. Switch to Code topology for other code nodes.")}</p>}
              <section className="panel graph-panel">
                <div className="graph-toolbar">
                  <div className="tabs">
                    {[
                      ["code", t("代码实体拓扑", "Code topology")],
                      ["business", t("业务本体", "Business ontology")],
                      ["mapping", t("业务—代码映射", "Business ↔ code")],
                    ].map(([v, n]) => (
                      <button
                        key={v}
                        className={view === v ? "active" : ""}
                        onClick={() => setView(v)}
                      >
                        {n}
                      </button>
                    ))}
                  </div>
                  <div className="row">
                    <input
                      aria-label="Search entities"
                      placeholder={t(
                        "搜索名称 / 签名 / ID",
                        "Search name / signature / ID",
                      )}
                      value={search}
                      onChange={(e) => setSearch(e.target.value)}
                    />
                    <select
                      aria-label="Entity type"
                      value={kind}
                      onChange={(e) => setKind(e.target.value)}
                    >
                      <option value="all">{t("全部类型", "All types")}</option>
                      {[
                        "Class",
                        "Interface",
                        "Method",
                        "File",
                        "BusinessRule",
                        "BusinessModule",
                        "BusinessConcept",
                        "ControlStructure",
                      ].map((k) => (
                        <option key={k}>{k}</option>
                      ))}
                    </select>
                  </div>
                </div>
                <div className="graph-layout">
                  <div className="graph-canvas">
                    {shownNodes.length ? (
                      <Suspense
                        fallback={
                          <Empty text={t("加载图谱组件…", "Loading graph…")} />
                        }
                      >
                        <Graph
                          nodes={shownNodes}
                          edges={shownEdges}
                          dark={dark}
                          onSelect={setNode}
                        />
                      </Suspense>
                    ) : (
                      <Empty
                        text={
                          graph.nodes.length
                            ? t(
                                "此视图没有对应实体；业务映射需先审核发布并加载 Release。",
                                "No entities in this view. Business mappings require a reviewed release.",
                              )
                            : t(
                                "请选择固定投影或 Release，再加载真实图谱。",
                                "Select a pinned projection or release, then load the graph.",
                              )
                        }
                      />
                    )}
                    <div className="graph-legend">
                      <span>■ Class / File</span>
                      <span>■ Method</span>
                      <span>■ BusinessRule</span>
                      <span>
                        {shownNodes.length} nodes · {shownEdges.length} edges
                      </span>
                    </div>
                  </div>
                  <aside className="inspector">
                    <h3>
                      {t("实体 / 关系检查器", "Entity / relation inspector")}
                    </h3>
                    {node ? (
                      <>
                        <div className="badge">{node.kind || node.type}</div>
                        <h3>{node.name || node.type}</h3>
                        <dl>
                          {Object.entries(node)
                            .filter(
                              ([k, v]) =>
                                !["name", "kind"].includes(k) &&
                                typeof v !== "object",
                            )
                            .map(([k, v]) => (
                              <ReactFragment key={k}>
                                <dt>{k}</dt>
                                <dd>{String(v)}</dd>
                              </ReactFragment>
                            ))}
                        </dl>
                        {node.path && node.snapshot_id && (
                          <button
                            onClick={() =>
                              act(async () => {
                                setSourceEvidence(
                                  await api(
                                    "/source-snapshots/" +
                                      node.snapshot_id +
                                      "/files?path=" +
                                      encodeURIComponent(node.path),
                                  ),
                                );
                              })
                            }
                          >
                            {t("定位源码", "Locate source")}
                          </button>
                        )}
                        <h3>{t("相邻关系", "Adjacent relations")}</h3>
                        {graph.edges
                          .filter(
                            (e: Row) =>
                              e.source === node.id || e.target === node.id,
                          )
                          .map((e: Row) => (
                            <button
                              className="neighbor"
                              key={e.id}
                              onClick={() => setNode(e)}
                            >
                              {e.source === node.id ? "→" : "←"} {e.type}
                            </button>
                          ))}
                      </>
                    ) : (
                      <p className="muted">
                        {t(
                          "选择节点或有向边查看实际属性。图支持拖拽、缩放和重新适配。",
                          "Select a node or directed edge to inspect stored attributes. Drag, zoom or fit the graph.",
                        )}
                      </p>
                    )}
                  </aside>
                </div>
                <details className="pad">
                  <summary>
                    {t(
                      "实体和关系记录（支持键盘操作）",
                      "Entity & relation records (keyboard accessible)",
                    )}
                  </summary>
                  <div className="scroll">
                    <table>
                      <thead>
                        <tr>
                          <th>{t("类型", "Type")}</th>
                          <th>{t("名称 / 关系", "Name / relationship")}</th>
                          <th>ID</th>
                        </tr>
                      </thead>
                      <tbody>
                        {shownNodes.map((n) => (
                          <tr key={n.id}>
                            <td>{n.kind}</td>
                            <td>
                              <button
                                className="link"
                                onClick={() => setNode(n)}
                              >
                                {n.name}
                              </button>
                            </td>
                            <td>{short(n.id)}</td>
                          </tr>
                        ))}
                        {shownEdges.map((e) => (
                          <tr key={e.id}>
                            <td>Edge</td>
                            <td>
                              <button
                                className="link"
                                onClick={() => setNode(e)}
                              >
                                {e.type} · {short(e.source)} → {short(e.target)}
                              </button>
                            </td>
                            <td>{short(e.id)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </details>
                {graph.truncated && (
                  <div className="notice warn">
                    {t(
                      "当前为有限节点的拓扑预览，不能当作完整图谱。",
                      "This bounded topology preview is not the complete graph.",
                    )}
                  </div>
                )}
                {sourceEvidence && (
                  <details className="pad">
                    <summary>
                      {sourceEvidence.path} ·{" "}
                      {short(sourceEvidence.snapshot_id)}
                    </summary>
                    <pre className="code-lines">{sourceEvidence.content}</pre>
                  </details>
                )}
                {graph.limitations && (
                  <details className="pad">
                    <summary>
                      {t("解析覆盖与限制", "Analysis coverage & limitations")}
                    </summary>
                    <ul>
                      {graph.limitations.map((s: string) => (
                        <li key={s}>{s}</li>
                      ))}
                    </ul>
                    <p>
                      {t("未解析调用", "Unresolved calls")}:{" "}
                      {graph.unresolved_calls?.length || 0}
                    </p>
                    <pre>{JSON.stringify(graph.diagnostics, null, 2)}</pre>
                  </details>
                )}
              </section>
            </>
          )}

          {page === "versions" && (
            <>
              <div className="tabs">
                <button
                  className={!auditTab ? "active" : ""}
                  onClick={() => setAuditTab(false)}
                >
                  {t("发布与默认指针", "Releases & pointer")}
                </button>
                <button
                  className={auditTab ? "active" : ""}
                  disabled={!reviewer}
                  onClick={() => setAuditTab(true)}
                >
                  {t("操作审计", "Audit trail")}
                </button>
              </div>
              {auditTab ? (
                <Panel title={t("实际操作记录", "Actual operation records")}>
                  {audits.length ? (
                    <div className="scroll">
                      <table>
                        <thead>
                          <tr>
                            <th>{t("时间", "Time")}</th>
                            <th>{t("身份", "Actor")}</th>
                            <th>{t("操作", "Operation")}</th>
                            <th>{t("对象", "Target")}</th>
                          </tr>
                        </thead>
                        <tbody>
                          {audits.map((a) => (
                            <tr key={a.id}>
                              <td>{new Date(a.at).toLocaleString()}</td>
                              <td>{a.actor}</td>
                              <td>{a.action}</td>
                              <td>{short(a.target)}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  ) : (
                    <Empty text={t("暂无审计。", "No audit records.")} />
                  )}
                </Panel>
              ) : (
                <>
                  <div className="note">
                    <strong>{t("当前默认版本", "Current release")}: </strong>
                    {pointer.release_id || t("尚未发布", "Not published")} ·
                    revision {pointer.revision}
                    <p>
                      {t(
                        "回退仅切换知识指针，不回退源码。历史内容不被覆盖。",
                        "Rollback switches the knowledge pointer only; source code is unaffected and history remains immutable.",
                      )}
                    </p>
                  </div>
                  <div className="two-columns">
                    <Panel
                      title={t("发布已审核知识", "Publish reviewed knowledge")}
                    >
                      {candidates.filter(
                        (c) =>
                          c.status === "APPROVED" || c.status === "PUBLISHED",
                      ).length === 0 ? (
                        <Empty
                          text={t(
                            "没有可发布的已批准规则。",
                            "No approved rules available for publication.",
                          )}
                        />
                      ) : (
                        <>
                          {candidates
                            .filter(
                              (c) =>
                                c.status === "APPROVED" ||
                                c.status === "PUBLISHED",
                            )
                            .map((c) => (
                              <label className="check" key={c.id}>
                                <input
                                  type="checkbox"
                                  checked={selectedCandidates.includes(c.id)}
                                  onChange={(e) =>
                                    setSelectedCandidates(
                                      e.target.checked
                                        ? [...selectedCandidates, c.id]
                                        : selectedCandidates.filter(
                                            (id) => id !== c.id,
                                          ),
                                    )
                                  }
                                />
                                <span>
                                  {c.name} · r{c.content_revision}
                                </span>
                                {status(c.status)}
                              </label>
                            ))}
                          <Field label={t("发布原因", "Publication reason")}>
                            <textarea
                              value={publishReason}
                              onChange={(e) => setPublishReason(e.target.value)}
                            />
                          </Field>
                          <button
                            className="primary"
                            disabled={
                              busy ||
                              !admin ||
                              !selectedCandidates.length ||
                              !publishReason
                            }
                            onClick={() =>
                              act(async () => {
                                const result = await api("/releases", "POST", {
                                  candidate_ids: selectedCandidates,
                                  expected_revision: pointer.revision,
                                  reason: publishReason,
                                });
                                setReleaseId(result.id);
                                setSelectedCandidates([]);
                                setMessage(
                                  t(
                                    "不可变清单已发布，默认指针已切换。",
                                    "Immutable manifest published; default pointer switched.",
                                  ),
                                );
                              })
                            }
                          >
                            {t("校验并发布", "Validate & publish")}
                          </button>
                        </>
                      )}
                    </Panel>
                    <Panel title={t("不可变发布版本", "Immutable releases")}>
                      {releases.length === 0 ? (
                        <Empty
                          text={t(
                            "首个发布版本将在审核后创建。",
                            "The first release is created after review.",
                          )}
                        />
                      ) : (
                        releases.map((r) => (
                          <div className="source-item" key={r.id}>
                            <div className="row between">
                              <strong title={r.id}>{short(r.id)}</strong>
                              {r.id === pointer.release_id && (
                                <span className="badge success">
                                  {t("当前", "Current")}
                                </span>
                              )}
                            </div>
                            <p>
                              {r.rule_count} {t("规则", "rules")} ·{" "}
                              {new Date(r.created_at).toLocaleString()}
                            </p>
                            <p className="muted">
                              {r.snapshot_ids.map(short).join(", ")}
                            </p>
                            <div className="row">
                              <button
                                onClick={() =>
                                  act(async () => {
                                    const v = await api("/releases/" + r.id + "/graph?limit=80");
                                    setReleaseId(v.id);
                                    setGraph(v);
                                    setGraphRelease(v.id);
                                    setView("mapping");
                                    setPage("graph");
                                  })
                                }
                              >
                                {t("查看图谱与映射", "View graph & mappings")}
                              </button>
                              {r.id !== pointer.release_id && (
                                <button
                                  disabled={busy || !admin}
                                  onClick={() => setReleaseId(r.id)}
                                >
                                  {t(
                                    "选择为回退目标",
                                    "Select rollback target",
                                  )}
                                </button>
                              )}
                            </div>
                          </div>
                        ))
                      )}
                      {releaseId && releaseId !== pointer.release_id && (
                        <div className="section-line">
                          <p>
                            {t("目标版本", "Target")}: {short(releaseId)}
                          </p>
                          <Field label={t("切换原因", "Switch reason")}>
                            <textarea
                              value={publishReason}
                              onChange={(e) => setPublishReason(e.target.value)}
                            />
                          </Field>
                          <button
                            disabled={busy || !admin || !publishReason}
                            onClick={() =>
                              act(async () => {
                                await api("/release-pointers/current", "PUT", {
                                  release_id: releaseId,
                                  expected_revision: pointer.revision,
                                  reason: publishReason,
                                });
                              })
                            }
                          >
                            {t("确认切换默认指针", "Confirm pointer switch")}
                          </button>
                        </div>
                      )}
                    </Panel>
                  </div>
                </>
              )}
            </>
          )}

          {page === "agents" && <><ContextPanel token={token} lang={lang} releases={releases}/><EvaluationPanel token={token} lang={lang} releases={releases} admin={!!admin}/></>}

        </main>
      </div>
      <footer>
        Bank Engineering Ontology Studio · M2 Increment 3{" "}
        <span>
          {t(
            "需求依据 V0.13 · 完整产品能力按阶段交付",
            "Requirements V0.13 · Full product capabilities delivered in stages",
          )}
        </span>
      </footer>
    </div>
  );
}

function ReactFragment({ children }: { children: React.ReactNode }) {
  return <>{children}</>;
}
