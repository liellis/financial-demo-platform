<template>
  <PageIntro
    kicker="01 / UNDERSTAND THE EXISTING SYSTEM"
    title="理解存量，让增量有据可依"
    desc="把业务规则、代码与测试关联起来，形成可复用的工程知识。"
  >
    <button class="btn" @click="go('projects')"><AppIcon name="folder" :size="14" />工程来源</button>
  </PageIntro>

  <div class="v3-stat-row">
    <div v-for="stat in stats" :key="stat.title">
      <AppIcon :name="stat.icon" :size="18" />
      <span><b>{{ stat.title }}</b><small>{{ stat.desc }}</small></span>
      <i>示例</i>
    </div>
  </div>

  <section class="panel v3-knowledge-panel">
    <div class="v3-panel-top">
      <div class="v3-tabset">
        <button
          v-for="tab in tabs"
          :key="tab.id"
          :class="{ active: demo.knowledgeTab === tab.id }"
          @click="demo.knowledgeTab = tab.id"
        >{{ tab.name }}</button>
      </div>
      <span class="pill neutral">示例知识快照</span>
    </div>

    <div v-if="demo.knowledgeTab === 'rules'" class="v3-rules">
      <button v-for="rule in rules" :key="rule.title" @click="showEvidence(rule.evidence)">
        <AppIcon name="book" :size="20" />
        <div><b>{{ rule.title }}</b><p>{{ rule.desc }}</p></div>
        <AppIcon name="external" :size="14" />
      </button>
    </div>

    <template v-else>
      <div class="v3-graph-heading">
        <div>
          <h2>{{ demo.knowledgeTab === 'impact' ? '变更边界与影响路径' : '业务知识 × 代码关系图谱' }}</h2>
          <p>{{ demo.knowledgeTab === 'impact' ? '识别候选修改、受影响依赖与必须保护的已有能力' : '从业务规则出发，找到已有实现及其验证依据' }}</p>
        </div>
        <button class="icon-button" aria-label="查看图谱依据" @click="showEvidence('service')">
          <AppIcon name="expand" :size="15" />
        </button>
      </div>
      <KnowledgeGraph @evidence="showEvidence" />
    </template>

    <div class="v3-graph-summary">
      <span><AppIcon name="target" :size="15" /> 本次分析产出</span>
      <button @click="showEvidence('service')">候选变更模块 <AppIcon name="external" :size="11" /></button>
      <button @click="showEvidence('ledger')">复用与保护清单 <AppIcon name="external" :size="11" /></button>
      <button @click="showEvidence('tests')">关联回归范围 <AppIcon name="external" :size="11" /></button>
    </div>
  </section>

  <div class="v3-insights">
    <section class="panel">
      <div class="v3-panel-label"><AppIcon name="layers" :size="17" /> 优先复用已有资产</div>
      <p>业务处理入口、账务组件与历史回归用例，可作为本次增量的候选资产。</p>
      <button class="text-link" @click="showEvidence('ledger')">查看复用依据 <AppIcon name="arrow" :size="13" /></button>
    </section>
    <section class="panel">
      <div class="v3-panel-label"><AppIcon name="lock" :size="17" /> 先明确变更边界</div>
      <p>新增返现规则应遵守现有准入、幂等与一致性约束；具体范围需结合真实工程确认。</p>
      <button class="text-link" @click="demo.knowledgeTab = 'impact'">查看影响分析 <AppIcon name="arrow" :size="13" /></button>
    </section>
  </div>

  <div class="v3-next">
    <span><AppIcon name="help" :size="15" /> 点击图谱节点，查看规则、代码和测试的关联证据</span>
    <button class="btn btn-primary" @click="next">
      {{ demo.knowledgeReady ? '进入 Skills 管理' : '确认知识准备 · 下一步' }} <AppIcon name="arrow" :size="14" />
    </button>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import KnowledgeGraph from '../components/KnowledgeGraph.vue'
import PageIntro from '../components/PageIntro.vue'
import { demo, record } from '../store/demo'
import { openModal } from '../store/ui'

const router = useRouter()

const stats = [
  { icon: 'graph', title: '代码资产', desc: '服务 · 依赖 · 调用链' },
  { icon: 'book', title: '业务知识', desc: '规则 · 约束 · 业务语义' },
  { icon: 'shield', title: '验证资产', desc: '测试 · 回归 · 质量规范' }
]

const tabs = [
  { id: 'graph', name: '代码图谱' },
  { id: 'rules', name: '业务规则' },
  { id: 'impact', name: '影响分析' }
]

const rules = [
  { title: '卡片状态校验', desc: '冻结、注销及异常状态应沿用已有处理规则', evidence: 'rule' },
  { title: '消费与限额约束', desc: '沿用现有消费校验，避免新增路径绕过规则', evidence: 'service' },
  { title: '账本一致性与返现幂等', desc: '识别重复交易与账务保护范围', evidence: 'ledger' },
  { title: '历史回归保护', desc: '将已有测试纳入必要回归清单', evidence: 'tests' }
]

function go(name) {
  router.push({ name })
}

function showEvidence(id) {
  openModal('evidence', { id })
}

function next() {
  demo.knowledgeReady = true
  record('MOCK_KNOWLEDGE_READY', '本地确认示例知识准备')
  router.push({ name: 'skills' })
}
</script>
