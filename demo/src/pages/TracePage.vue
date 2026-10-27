<template>
  <PageIntro
    kicker="05 / END-TO-END TRACEABILITY"
    title="从需求到交付，每一步都有依据"
    desc="关联同一运行、代码版本、业务规则、测试与审核记录。"
  >
    <button class="btn" @click="exportNote"><AppIcon name="download" :size="14" />导出原型说明</button>
    <button class="btn btn-primary" @click="openModal('events')"><AppIcon name="clock" :size="14" />查看运行事件</button>
  </PageIntro>

  <section class="panel">
    <div class="panel-heading">
      <div>
        <h2 class="panel-title">需求到交付的证据链</h2>
        <p class="panel-subtitle">任一节点可展开 · 当前内容为示例，不存在真实 Git 提交或执行报告</p>
      </div>
      <span class="pill">DEMO-RUN-001</span>
    </div>
    <div class="panel-body">
      <div class="trace-flow">
        <template v-for="(link, index) in links" :key="link.name">
          <button class="trace-node" @click="showEvidence(`trace-${index}`)">
            <AppIcon :name="link.icon" :size="20" />
            <b>{{ link.name }}</b>
            <span>{{ link.sub }}</span>
          </button>
          <AppIcon v-if="index < links.length - 1" name="arrow" :size="12" />
        </template>
      </div>
    </div>
  </section>

  <div class="split" style="margin-top:20px">
    <section class="panel">
      <div class="panel-heading"><h2 class="panel-title">关联证据清单 <small>原型示例</small></h2></div>
      <div class="table-wrap">
        <table>
          <thead><tr><th>产出物</th><th>来源平台</th><th>版本关联</th><th>查看</th></tr></thead>
          <tbody>
            <tr v-for="row in evidenceRows" :key="row.title">
              <td><strong>{{ row.title }}</strong></td>
              <td>{{ row.platform }}</td>
              <td>{{ row.version }}</td>
              <td><button class="table-link" @click="showEvidence('run')">证据详情 <AppIcon name="external" :size="11" /></button></td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section class="panel">
      <div class="panel-heading"><h2 class="panel-title">版本与来源，保持一致</h2></div>
      <div class="panel-body">
        <div class="detail-section">
          <dl>
            <dt>工程基线</dt><dd>待绑定</dd>
            <dt>知识索引</dt><dd>待绑定</dd>
            <dt>运行方式</dt><dd>MOCK · 本地原型</dd>
            <dt>计划确认</dt><dd>{{ demo.approved ? '已模拟确认' : '待模拟确认' }}</dd>
            <dt>检查结果</dt><dd>{{ demo.verified ? '示例检查完成' : '待体验验证' }}</dd>
          </dl>
        </div>
        <div class="callout blue">
          <AppIcon name="branch" :size="15" />
          <span>代码、知识、测试和审批关联对应版本。版本变化后，需要重新判断既有证据的有效性。</span>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import AppIcon from '../components/AppIcon.vue'
import PageIntro from '../components/PageIntro.vue'
import { demo, scenarioName, stages } from '../store/demo'
import { openModal, showToast } from '../store/ui'

const links = [
  { name: '需求', sub: 'REQ-DEMO-001', icon: 'document' },
  { name: '规则', sub: 'RULE-DEMO-01', icon: 'book' },
  { name: '代码', sub: '示例方法引用', icon: 'code' },
  { name: '提交', sub: '待绑定 Git 版本', icon: 'branch' },
  { name: '测试', sub: '示例报告', icon: 'target' },
  { name: '门禁', sub: '示例检查项', icon: 'shield' },
  { name: '审批', sub: '原型审核记录', icon: 'users' }
]

const evidenceRows = [
  { title: '结构化需求与验收', platform: 'Agent Teams', version: '方案草稿 · 示例' },
  { title: '代码上下文与影响分析', platform: 'CodeGraph', version: '工程快照待绑定' },
  { title: '代码变更摘要', platform: 'iCode', version: 'Git 版本待绑定' },
  { title: '质量与安全检查报告', platform: '测试平台', version: '执行结果待接入' },
  { title: '方案与交付审核记录', platform: 'Portal', version: '原型操作记录' }
]

function showEvidence(id) {
  openModal('evidence', { id })
}

function exportNote() {
  const report = {
    document: '银行 AI-Coding Demo 高保真原型说明',
    mode: 'MOCK',
    scenario: scenarioName(),
    backendConnected: false,
    measurements: '未导入真实实验',
    runtimeState: {
      stage: stages[demo.step],
      planConfirmed: demo.approved,
      exampleChecksCompleted: demo.verified,
      exampleReview: demo.reviewed
    },
    localPrototypeEvents: demo.events,
    notice: '本文件只记录本地交互原型，不构成平台执行、测试或审批证据。'
  }
  const url = URL.createObjectURL(new Blob([JSON.stringify(report, null, 2)], { type: 'application/json' }))
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = 'AI-Coding-Demo-原型交互说明.json'
  anchor.click()
  setTimeout(() => URL.revokeObjectURL(url), 1500)
  showToast('已导出原型交互说明，不含真实执行证据')
}
</script>
