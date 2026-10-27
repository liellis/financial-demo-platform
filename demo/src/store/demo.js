import { reactive } from 'vue'

export const stages = ['理解存量', '影响分析', '方案确认', 'Skill 研发', '质量验证', '交付沉淀']

export const pages = [
  { id: 'business', icon: 'credit', name: '业务体验', caption: '信用卡界面', description: '在模拟业务中，验收增量交付结果' },
  { id: 'knowledge', icon: 'graph', name: '存量系统理解', description: '关联业务规则、代码与可复用资产' },
  { id: 'efficiency', icon: 'chart', name: '研发效能提升', description: '以任务过程与对照实验，量化研发价值' },
  { id: 'quality', icon: 'shield', name: '质量与安全保障', description: '关键约束、质量门禁与人工审核' },
  { id: 'evolution', icon: 'spark', name: '平台 RSI 自演进', description: '从运行反馈到经过评测的能力改进' },
  { id: 'trace', icon: 'branch', name: '全链路追溯', description: '串联需求、规则、代码与交付证据' }
]

export const workflowItems = [
  { id: 'knowledge', icon: 'graph', name: '存量系统理解', description: '代码图谱 · 业务规则 · 影响' },
  { id: 'skills', icon: 'layers', name: 'SDLC Skills 管理', description: '导入能力 · 规范复用' },
  { id: 'develop', icon: 'document', name: '需求输入与 AI 开发', description: '需求 · SDD · 计划 · 执行' },
  { id: 'quality', icon: 'shield', name: '代码评审与质量保障', description: 'Review · 测试 · 安全门禁' },
  { id: 'trace', icon: 'branch', name: '追溯分析', description: '需求 · 代码 · 证据关联' },
  { id: 'efficiency', icon: 'chart', name: '研发效能与业务结果', description: '过程对照 · 业务验收' }
]

export const skillRows = [
  { name: '需求分析', skill: 'bank-requirement-clarification', desc: '澄清业务边界与验收条件', icon: 'document' },
  { name: '存量理解', skill: 'bank-repo-understanding', desc: '构建代码与业务知识上下文', icon: 'graph' },
  { name: '影响评估', skill: 'bank-change-impact', desc: '定位影响范围与保护清单', icon: 'branch' },
  { name: '受控开发', skill: 'bank-scoped-modification', desc: '按批准范围完成增量修改', icon: 'code' },
  { name: '测试验证', skill: 'bank-test-execution', desc: '执行新增与关联回归验证', icon: 'shield' },
  { name: '交付追溯', skill: 'bank-change-review-package', desc: '汇总变更与审核依据', icon: 'folder' }
]

export const flowItems = [
  { id: 'knowledge', icon: 'graph', title: '构建存量系统的代码图谱与业务知识', sub: '理解存量系统', roles: ['Talent', 'Teams'] },
  { id: 'skills', icon: 'layers', title: '导入 SDLC 各环节 Skills', sub: '复用专业能力', roles: ['Teams', 'Core'] },
  { id: 'develop', icon: 'code', title: '输入需求，启动开发', sub: '需求到受控执行', roles: ['Core', 'Teams'] },
  { id: 'quality', icon: 'shield', title: '代码 Review、测试与安全扫描', sub: '功能测试 / DFX / 安全', roles: ['Core', 'Teams'] },
  { id: 'trace', icon: 'branch', title: '追溯分析', sub: '关联需求、代码与证据', roles: ['Teams', 'Core'] }
]

export const assets = [
  { id: 'rule', name: '业务准入与规则校验', type: '业务知识', icon: 'book', desc: '建立业务约束与执行入口之间的关联', status: '候选规则', tag: 'pending', source: 'business-rules / admission-policy', method: '规则条目 → 校验方法 → 关联测试' },
  { id: 'service', name: '已有业务处理服务', type: '代码资产', icon: 'code', desc: '复用现有处理入口，限定增量修改范围', status: '复用建议', tag: 'progress', source: 'modules/business/BusinessService.java', method: 'BusinessService.execute → RuleService.validate' },
  { id: 'ledger', name: '已有账务处理组件', type: '可复用服务', icon: 'database', desc: '通过既有服务接口处理账户与账务一致性', status: '保护范围', tag: 'success', source: 'modules/accounting/PostingService.java', method: 'PostingService.post → consistency checks' },
  { id: 'tests', name: '历史业务回归用例', type: '测试资产', icon: 'shield', desc: '把已有测试纳入变更后的必要回归范围', status: '复用建议', tag: 'progress', source: 'tests/business/ExistingBehaviorTest', method: '关键约束 → 历史用例 → 新增边界验证' }
]

export const demo = reactive({
  scenario: 'card',
  step: 1,
  running: false,
  paused: false,
  waiting: false,
  approved: false,
  verified: false,
  reviewed: false,
  view: 'executive',
  showcase: false,
  knowledgeTab: 'graph',
  query: '',
  comparison: false,
  evolution: 1,
  businessResult: null,
  events: [],
  knowledgeReady: false,
  skillsImported: false,
  requirementSaved: false,
  developed: false,
  devTab: 'requirement',
  platformTab: 'overview',
  resourceTab: 'overview',
  resourceSample: false,
  requirementText: '为符合活动资格的信用卡消费增加额外返现能力。复用已有客户、卡片与账务服务，保持卡状态校验、消费限额和账本一致性，补充幂等与回归验证。',
  mobileNav: false
})

export function scenarioName() {
  return demo.scenario === 'card' ? '信用卡消费返现增量开发' : '存量业务规则增量扩展'
}

export function record(type, summary) {
  demo.events.push({
    sequence: demo.events.length + 1,
    type,
    summary,
    source: 'MOCK / browser-local',
    time: new Date().toLocaleTimeString('zh-CN', { hour12: false })
  })
}

export function resetDemoState() {
  Object.assign(demo, {
    step: 1,
    running: false,
    paused: false,
    waiting: false,
    approved: false,
    verified: false,
    reviewed: false,
    events: [],
    businessResult: null,
    evolution: 1,
    knowledgeReady: false,
    skillsImported: false,
    requirementSaved: false,
    developed: false,
    devTab: 'requirement',
    resourceSample: false
  })
}

export function isCompleted(id) {
  if (id === 'knowledge') return demo.knowledgeReady
  if (id === 'skills') return demo.skillsImported
  if (id === 'develop') return demo.developed
  if (id === 'quality') return demo.reviewed
  return false
}

export const pageLabels = {
  overview: '演示总览',
  development: '存量系统开发需求',
  platform: 'iCode 平台能力展示',
  skills: 'SDLC Skills 管理',
  develop: '需求输入与 AI 开发',
  resources: 'Token 与资源使用监控',
  projects: '代码工程管理',
  evolution: '平台 RSI 自演进',
  business: '业务体验',
  knowledge: '存量系统理解',
  efficiency: '研发效能提升',
  quality: '质量与安全保障',
  trace: '全链路追溯'
}
