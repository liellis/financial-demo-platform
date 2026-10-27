import { reactive } from 'vue'
import { en as autoEn } from './en'

const overrides = {
  '跳至主要内容': 'Skip to main content',
  '演示总览': 'Demo Overview',
  '代码工程管理': 'Code Projects',
  '存量系统开发需求': 'Existing System Development',
  '存量系统增量开发': 'Existing System Incremental Development',
  '存量系统增量需求开发': 'Existing System Incremental Development',
  '需求输入与 AI 开发': 'Requirement Input & AI Development',
  '平台 RSI 自演进': 'Platform RSI',
  '全链路追溯': 'End-to-End Traceability',
  '业务体验': 'Business Experience',
  '质量与安全保障': 'Quality & Security',
  'SDLC Skills 管理': 'SDLC Skills',
  '存量系统理解': 'Existing System Understanding',
  '存量资产复用': 'Existing Asset Reuse',
  '研发生产力': 'R&D Productivity',
  '研发效能提升': 'R&D Efficiency',
  'Token 资源监控': 'Token Monitoring',
  '进入查看': 'View details',
  '返回': 'Back',
  '返回查看': 'Back',
  '自由浏览': 'Explore freely',
  '保存修改': 'Save changes',
  '编辑': 'Edit',
  '示例': 'Example',
  '待接入': 'Not connected',
  '可用': 'Available',
  '已归档': 'Archived',
  '待评测': 'Pending evaluation',
  '待演练': 'Pending rehearsal',
  '待审核': 'Pending review',
  '待确认': 'Pending confirmation',
  '待执行': 'Pending execution',
  '待绑定': 'Not bound',
  '待采集': 'Not collected',
  '待识别': 'Pending identification',
  '待模拟审核': 'Pending simulated review',
  '待人工评审': 'Pending manual review',
  '待人工确认': 'Pending manual confirmation',
  '待真实扫描': 'Pending real scan',
  '已模拟确认': 'Confirmed (simulated)',
  '模拟已审核': 'Reviewed (simulated)',
  '模拟评审完成': 'Simulated review complete',
  '完成模拟评审': 'Complete simulated review',
  '确认导入本地示例': 'Import sample',
  '示例通过': 'Passed (sample)',
  '示例完成': 'Complete (sample)',
  '查看': 'View',
  '查看全部': 'View all',
  '查看示例': 'View example',
  '关闭对话框': 'Close dialog',
  '展开导航': 'Expand navigation',
  '演示指南': 'Demo guide',
  '演示帮助': 'Demo help',
  '切换主秀布局': 'Toggle presentation layout',
  '切换语言': 'Switch language',
  '质量门禁': 'Quality Gate',
  '六道质量门禁': 'Six Quality Gates',
  '需求确认门禁': 'Requirement Confirmation Gate',
  '设计评估门禁': 'Design Assessment Gate',
  '构建检查门禁': 'Build Check Gate',
  '测试验证门禁': 'Test Validation Gate',
  '安全检查门禁': 'Security Check Gate',
  '人工评审门禁': 'Manual Review Gate',
  '质量验证': 'Quality Validation',
  '质量与安全': 'Quality & Security',
  '质量与交付': 'Quality & Delivery',
  '需求分析': 'Requirements Analysis',
  '需求确认': 'Requirement Confirmation',
  '方案确认': 'Plan Confirmation',
  '方案批准': 'Plan Approval',
  '人工评审': 'Manual Review',
  '人工审核': 'Manual Review',
  '安全检查': 'Security Check',
  '安全扫描': 'Security Scan',
  '测试验证': 'Test Validation',
  '构建检查': 'Build Check',
  '设计评估': 'Design Assessment',
  '影响分析': 'Impact Analysis',
  '追溯分析': 'Traceability Analysis',
  '产出与证据': 'Outputs & Evidence',
  '执行与协作': 'Execution & Collaboration',
  '输入上下文': 'Input Context',
  '专业 Skill、工具与对应任务运行记录': 'Professional skills, tools and task run records',
  '代码差异、质量报告、来源和版本关联': 'Code diffs, quality reports, source and version links',
  '产品地址待配置 · 当前为演示详情': 'Product URL not configured · Demo detail',
  '进入 iCode-Teams 产品页面': 'Open the iCode-Teams product page',
  '进入 iCode-Talents 产品页面': 'Open the iCode-Talents product page',
  '需求、知识索引、源代码基线与权限范围': 'Requirements, knowledge index, source baseline and permission scope',
  '新建工程': 'New project',
  '上传代码工程': 'Upload code project',
  '工程名称': 'Project name',
  '工程标识': 'Project ID',
  '工程说明': 'Project description',
  '工程状态': 'Project status',
  '更新时间': 'Updated',
  '工程来源': 'Project source',
  '当前演示工程': 'Current demo project',
  '管理工程': 'Manage projects',
  '搜索工程': 'Search projects',
  '全部状态': 'All statuses',
  '全部': 'All',
  '操作': 'Actions',
  '取消': 'Cancel',
  '确认': 'Confirm',
  '确定': 'Confirm',
  '提交': 'Submit',
  '删除': 'Delete',
  '切换场景': 'Switch scenario',
  '查看运行事件': 'View run events',
  '查看完整追溯链': 'View full trace',
  '运行示例验证': 'Run sample validation',
  '本地示例已通过': 'Sample passed locally',
  '尚未演练': 'Not yet rehearsed',
  '演练中': 'Rehearsing',
  '演练运行中': 'Rehearsal running',
  '本地原型': 'Local prototype',
  'PLATINUM &nbsp; / &nbsp; 模拟账户': 'PLATINUM / Simulated Account',
  '· 已绑定本地压缩包，尚未解压分析': '· Local archive bound, not yet extracted or analyzed',
  '· 未解压分析': '· not extracted or analyzed',
  '上述路径与引用为原型示例。 接入后应返回 source / revision / artifact / checksum。': 'The paths and references above are prototype examples. After integration, source / revision / artifact / checksum should be returned.',
  '。复用已有业务服务与工程规范，只扩展与本次需求有关的实现。': '. Reuse existing business services and engineering standards, and only extend the implementation related to this requirement.',
  '输入 / 产出：': 'Input / Output: ',
  '（人工填写）': '(manual entry)',
  '删除后无法在本地工程库恢复，请确认已保留原始文件。': 'Once deleted, it cannot be restored in the local project library. Please make sure the original file is retained.',
  '适应窗口': 'Fit to window',
  '原始尺寸': 'Original size',
  '当前演示工程的绑定也将解除。': 'The current demo project will also be unbound.',
  '暂无说明': 'No description',
  '尚未选择': 'Not selected',
  '确认示例方案': 'Confirm sample plan',
  '等待模拟确认': 'Awaiting simulated confirmation',
  '等待确认': 'Pending confirmation',
  '代码': 'Code',
  '安全 / DFX': 'Security / DFX',
  '· 示例': '· Example',
  '按任务组织专业 Skills，为执行提供知识、规范和质量约束。': 'Organize professional skills by task, providing knowledge, standards and quality constraints for execution.',
  '把业务规则、代码与测试关联起来，形成可复用的工程知识。': 'Link business rules, code and tests into reusable engineering knowledge.',
  '从代码评审、功能回归到 DFX 与安全检查，保留证据与人工把关。': 'From code review and functional regression to DFX and security checks, preserving evidence and manual review.',
  '从能力地图进入说明、执行示例与关联记录。': 'Enter explanations, execution examples and linked records from the capability map.',
  '从业务需求与存量资产出发，理解平台如何支撑复用、研发和可信交付。': 'Starting from business requirements and existing assets, understand how the platform supports reuse, R&D and trusted delivery.',
  '关联同一运行、代码版本、业务规则、测试与审核记录。': 'Link the same run, code version, business rules, tests and review records.',
  '将 Token、调用、时延与质量关联，理解一次研发任务的完整投入。': 'Connect tokens, calls, latency and quality to understand the full investment of an R&D task.',
  '上传已有工程压缩包，维护代码资产，并选择当前演示所使用的工程。': 'Upload an existing project archive, maintain code assets and select the project used in the current demo.',
  '同任务、同基线、同验收，比较研发投入与业务结果。': 'Compare R&D investment and business outcomes with the same task, baseline and acceptance criteria.',
  '问题 → 候选改进 → 固定任务集评测 → 人工审核 → 发布与观察。': 'Issue → Candidate improvement → Fixed task set evaluation → Manual review → Release and observe.',
  '先查看研发流程，再进入下方或左侧的二级菜单开展演示。': 'Review the development flow first, then use the submenu below or on the left to start the demo.',
  '信用卡返现是可替换的场景模板；本页为本地前端试算。': 'Credit card cashback is a replaceable scenario template; this page is a local front-end trial calculation.',
  '需求规格、实施计划、人工确认与 Agent 执行共享同一次运行。': 'Requirement specs, implementation plan, manual confirmation and Agent execution share the same run.',
  '组织级管理、多引擎核心部署、自演进与全链路监控。': 'Organization-level management, multi-engine core deployment, self-evolution and end-to-end monitoring.'
}

export const en = { ...autoEn, ...overrides }

const STORAGE_KEY = 'icode-demo-lang'
const CJK = /[\u4e00-\u9fff]/
const ATTRS = ['placeholder', 'aria-label', 'title', 'alt', 'label']
const dynamicRules = [
  [/^(\d[\d,.]*)\s*个$/, '$1'],
  [/^(\d[\d,.]*)\s*项$/, '$1'],
  [/^(\d[\d,.]*)\s*条$/, '$1'],
  [/^(\d[\d,.]*)\s*次$/, '$1 runs'],
  [/^(\d[\d,.]*)\s*天$/, '$1 days'],
  [/^(\d[\d,.]*)\s*小时$/, '$1 hours'],
  [/^(\d[\d,.]*)\s*分钟$/, '$1 min'],
  [/^(\d[\d,.]*)\s*秒$/, '$1 s']
]

function readLang() {
  try {
    const requested = new URLSearchParams(window.location.search).get('lang')
    if (requested === 'en' || requested === 'zh') return requested
    return localStorage.getItem(STORAGE_KEY) === 'en' ? 'en' : 'zh'
  } catch (e) {
    return 'zh'
  }
}

export const i18n = reactive({ lang: readLang() })

const textState = new WeakMap()
const attrState = new WeakMap()
let observer = null
let docMeta = null

function translateText(raw) {
  if (!CJK.test(raw)) return raw
  if (en[raw]) return en[raw]
  const compact = raw.replace(/\s+/g, ' ').trim()
  if (compact !== raw && en[compact]) {
    const lead = raw.match(/^\s*/)[0]
    const trail = raw.match(/\s*$/)[0]
    return lead + en[compact] + trail
  }
  const m = raw.match(/^([^\u4e00-\u9fffA-Za-z0-9]*)([\s\S]*?)([^\u4e00-\u9fffA-Za-z0-9]*)$/)
  if (m && m[2] && m[2] !== raw && en[m[2]]) return m[1] + en[m[2]] + m[3]
  const evidence = raw.match(/^查看(.+)的示例证据$/)
  if (evidence) return `View sample evidence for ${evidence[1]}`
  const example = raw.match(/^(.+?) · 示例$/)
  if (example) return `${example[1]} · Example`
  if (/^PLATINUM\s*\/\s*模拟账户$/.test(raw)) return 'PLATINUM / Simulated Account'
  for (const [pattern, replacement] of dynamicRules) {
    if (pattern.test(raw)) return raw.replace(pattern, replacement)
  }
  if (raw.endsWith('：')) {
    const head = raw.slice(0, -1)
    if (en[head]) return en[head] + ': '
  }
  return raw
}

export function t(text) {
  return i18n.lang === 'en' ? translateText(String(text)) : String(text)
}

function translateTextNode(node) {
  const parent = node.parentElement
  if (parent && /^(SCRIPT|STYLE|NOSCRIPT)$/.test(parent.tagName)) return
  const current = node.nodeValue
  if (!current) return
  const state = textState.get(node)
  let source
  if (state && current === state.output) source = state.source
  else if (CJK.test(current)) source = current
  else return
  const output = i18n.lang === 'en' ? translateText(source) : source
  textState.set(node, { source, output })
  if (current !== output) node.nodeValue = output
}

function translateAttrs(el) {
  if (!el.getAttribute) return
  const state = attrState.get(el) || {}
  for (const name of ATTRS) {
    const current = el.getAttribute(name)
    if (current == null) continue
    const prev = state[name]
    let source
    if (!prev) source = current
    else if (current === prev.output) source = prev.source
    else source = current
    const output = i18n.lang === 'en' ? translateText(source) : source
    state[name] = { source, output }
    if (current !== output) el.setAttribute(name, output)
  }
  attrState.set(el, state)
}

function scan(root) {
  if (!root) return
  if (root.nodeType === Node.TEXT_NODE) {
    translateTextNode(root)
    return
  }
  if (root.nodeType !== Node.ELEMENT_NODE && root.nodeType !== Node.DOCUMENT_NODE) return
  if (root.nodeType === Node.ELEMENT_NODE) translateAttrs(root)
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT | NodeFilter.SHOW_ELEMENT)
  let node = walker.nextNode()
  while (node) {
    if (node.nodeType === Node.TEXT_NODE) translateTextNode(node)
    else translateAttrs(node)
    node = walker.nextNode()
  }
}

function applyAll() {
  if (!document.body) return
  scan(document.body)
  applyDocumentMeta()
}

function applyDocumentMeta() {
  if (!docMeta) {
    const meta = document.querySelector('meta[name="description"]')
    docMeta = { title: document.title, description: meta ? meta.getAttribute('content') : '' }
  }
  document.title = i18n.lang === 'en' ? 'iCode · Bank AI-Coding Demo Platform' : docMeta.title
  const meta = document.querySelector('meta[name="description"]')
  if (meta) {
    meta.setAttribute(
      'content',
      i18n.lang === 'en'
        ? 'High-fidelity interactive prototype of a unified bank AI-Coding demo platform. Understand existing knowledge, organize R&D tasks and verify trusted delivery.'
        : docMeta.description
    )
  }
}

function setDocumentLang(lang) {
  document.documentElement.setAttribute('lang', lang === 'en' ? 'en' : 'zh-CN')
}

export function setLang(lang) {
  const next = lang === 'en' ? 'en' : 'zh'
  i18n.lang = next
  try {
    localStorage.setItem(STORAGE_KEY, next)
  } catch (e) {}
  setDocumentLang(next)
  applyAll()
}

export function toggleLang() {
  setLang(i18n.lang === 'en' ? 'zh' : 'en')
}

export function installI18n() {
  setDocumentLang(i18n.lang)
  applyAll()
  if (observer) observer.disconnect()
  observer = new MutationObserver((mutations) => {
    for (const mutation of mutations) {
      if (mutation.type === 'characterData') {
        translateTextNode(mutation.target)
      } else if (mutation.type === 'childList') {
        mutation.addedNodes.forEach((node) => scan(node))
      } else if (mutation.type === 'attributes') {
        translateAttrs(mutation.target)
      }
    }
  })
  observer.observe(document.body, {
    subtree: true,
    childList: true,
    characterData: true,
    attributes: true,
    attributeFilter: ATTRS
  })
}
