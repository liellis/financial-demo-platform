<template>
  <PageIntro
    kicker="04 / QUALITY & SECURITY"
    title="质量可验证，交付有边界"
    desc="从代码评审、功能回归到 DFX 与安全检查，保留证据与人工把关。"
  >
    <button class="btn" @click="openModal('approval')"><AppIcon name="lock" :size="14" />变更边界</button>
    <button class="btn btn-primary" @click="runVerification">
      <AppIcon name="shield" :size="14" />{{ demo.verified ? '示例检查已完成' : '运行示例验证' }}
    </button>
  </PageIntro>

  <div class="v3-quality-tags">
    <span>代码 Review</span><span>功能与回归</span><span>DFX 检查</span><span>安全扫描</span><span>质量门禁</span>
  </div>

  <div class="split">
    <div class="stack">
      <section class="panel">
        <div class="panel-heading">
          <div>
            <h2 class="panel-title">六道质量门禁</h2>
            <p class="panel-subtitle">以下为本地原型状态，必选门禁未满足时，不标记交付完成</p>
          </div>
          <span class="pill" :class="demo.reviewed ? 'success' : 'pending'">
            {{ demo.reviewed ? '示例门禁满足' : '等待验证与审核' }}
          </span>
        </div>
        <div class="panel-body">
          <div class="gate-grid">
            <button v-for="gate in gates" :key="gate.name" class="gate-card" @click="showEvidence(gate.evidence)">
              <span class="gate-icon"><AppIcon :name="gate.icon" :size="20" /></span>
              <h3>{{ gate.name }}</h3>
              <p>{{ gate.desc }}</p>
              <span class="pill" :class="gate.color">{{ gate.status }}</span>
            </button>
          </div>
          <div class="callout blue">
            <AppIcon name="shield" :size="16" />
            <span>Skill 退出成功 ≠ 验证通过 ≠ 允许交付。质量状态与人工审批分开呈现。</span>
          </div>
          <div style="display:flex;gap:9px">
            <button class="btn btn-small" @click="openModal('test-report')"><AppIcon name="document" :size="13" />查看示例测试报告</button>
            <button class="btn btn-small" @click="requestReview"><AppIcon name="users" :size="13" />模拟最终评审</button>
          </div>
        </div>
      </section>
    </div>

    <div class="stack">
      <section class="panel">
        <div class="panel-heading"><h2 class="panel-title">开发过程保护范围</h2></div>
        <div class="panel-body">
          <div v-for="item in protections" :key="item.text" class="risk-row">
            <AppIcon :name="item.icon" :size="15" />
            <span>{{ item.text }}</span>
            <span style="margin-left:auto;font-size:12px;color:#a1acba">策略示意</span>
          </div>
        </div>
      </section>
      <section class="panel">
        <div class="panel-heading"><h2 class="panel-title">业务规则如何被验证？</h2></div>
        <div class="panel-body">
          <p class="subheading">从验收条件到测试用例，再到当前版本的执行结果，形成关联。</p>
          <button class="related-link" @click="go('trace')">需求 → 规则 → 代码 → 测试 <AppIcon name="arrow" :size="13" /></button>
          <button class="related-link" @click="go('business')">查看增量业务体验 <AppIcon name="arrow" :size="13" /></button>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import PageIntro from '../components/PageIntro.vue'
import { demo } from '../store/demo'
import { runVerification, requestReview } from '../store/flow'
import { openModal } from '../store/ui'

const router = useRouter()

const gates = computed(() => [
  { name: '需求确认', desc: '需求、关键规则与验收条件', color: 'success', status: '示例已确认', icon: 'document', evidence: 'gate-0' },
  { name: '设计评估', desc: '影响范围、复用方案与风险', color: demo.approved ? 'success' : 'pending', status: demo.approved ? '模拟确认' : '待方案确认', icon: 'graph', evidence: 'gate-1' },
  { name: '构建检查', desc: '构建结果与开发规范', color: demo.verified ? 'success' : 'neutral', status: demo.verified ? '示例通过' : '待演练', icon: 'code', evidence: 'gate-2' },
  { name: '测试验证', desc: '新增、边界和必要历史回归', color: demo.verified ? 'success' : 'neutral', status: demo.verified ? '示例通过' : '待演练', icon: 'target', evidence: 'gate-3' },
  { name: '安全检查', desc: '权限、敏感信息与依赖风险', color: demo.verified ? 'success' : 'neutral', status: demo.verified ? '示例通过' : '待演练', icon: 'shield', evidence: 'gate-4' },
  { name: '人工评审', desc: '审核明确版本的交付结果', color: demo.reviewed ? 'success' : 'pending', status: demo.reviewed ? '模拟已审核' : '待人工评审', icon: 'users', evidence: 'gate-5' }
])

const protections = [
  { text: '隔离工程工作区', icon: 'folder' },
  { text: '明确可修改目录与分支', icon: 'branch' },
  { text: '保护关键业务约束', icon: 'lock' },
  { text: '敏感信息与日志脱敏', icon: 'shield' },
  { text: '保留审批与执行记录', icon: 'document' }
]

function showEvidence(id) {
  openModal('evidence', { id })
}

function go(name) {
  router.push({ name })
}
</script>
