<template>
  <section class="panel">
    <div class="panel-heading">
      <div>
        <h2 class="panel-title">Skill 协同执行 <AppIcon name="spark" :size="14" /></h2>
        <p class="panel-subtitle">让已有技能，成为连续的研发工作</p>
      </div>
      <RouterLink class="text-link" to="/skills">查看全部 <AppIcon name="arrow" :size="12" /></RouterLink>
    </div>
    <div class="execution-list">
      <div v-for="row in rows" :key="row.title" class="execution-row">
        <span class="agent-icon"><AppIcon :name="row.icon" :size="15" /></span>
        <div class="execution-copy">
          <h3>{{ row.title }}</h3>
          <p>{{ row.platform }}<br>{{ row.desc }}</p>
          <div class="asset-chips"><span class="asset-chip">{{ row.chip }}</span></div>
        </div>
        <span class="pill" :class="row.tag">{{ row.status }}</span>
      </div>
    </div>
    <div class="approval-box">
      <AppIcon name="lock" :size="15" />
      <div>
        <b>{{ demo.approved ? '演示方案已确认' : '执行前，先确认变更边界' }}</b>
        <p>复用与新增范围、关键规则及验证计划<br>审批动作仅作用于本地原型。</p>
        <button @click="$emit('approval')">
          {{ demo.approved ? '查看确认记录' : '查看方案并模拟确认' }} <AppIcon name="arrow" :size="11" />
        </button>
      </div>
    </div>
    <div class="execution-footer">
      <div>
        <small>统一关联 · DemoRun</small>
        <p style="margin-top:5px;font-size:13px">DEMO-RUN-001 <span style="color:#8193ae">/ MOCK</span></p>
      </div>
      <RouterLink class="link" to="/trace">每一步，都可追溯 <AppIcon name="arrow" :size="12" /></RouterLink>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import AppIcon from './AppIcon.vue'
import { demo } from '../store/demo'

defineEmits(['approval'])

const rows = computed(() => [
  { icon: 'graph', title: '工程知识检索', platform: 'CodeGraph · 知识工程', desc: '业务规则与代码的关联上下文', chip: '12 项资产', status: '示例完成', tag: 'success' },
  { icon: 'document', title: '变更影响评估', platform: 'Agent Teams', desc: '影响范围、复用建议与关键约束', chip: '6 个影响点', status: '示例完成', tag: 'success' },
  { icon: 'code', title: '增量开发与验证', platform: 'iCode · Talent', desc: '使用已确认方案和已有规范执行', chip: '方案确认后执行', status: demo.approved ? '示例待执行' : '等待确认', tag: 'pending' }
])
</script>
