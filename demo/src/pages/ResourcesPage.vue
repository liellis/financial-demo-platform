<template>
  <PageIntro
    kicker="04 / RESOURCE OBSERVABILITY"
    title="看清资源投入，也看清任务效果"
    desc="将 Token、调用、时延与质量关联，理解一次研发任务的完整投入。"
  >
    <button class="btn" :class="{ 'btn-primary': !demo.resourceSample }" @click="demo.resourceSample = !demo.resourceSample">
      {{ demo.resourceSample ? '关闭示例数据' : '查看示例数据布局' }}
    </button>
  </PageIntro>

  <div class="tabs">
    <button
      v-for="tab in tabs"
      :key="tab.id"
      :class="{ active: demo.resourceTab === tab.id }"
      @click="demo.resourceTab = tab.id"
    >{{ tab.name }}</button>
  </div>

  <div class="v3-data-notice">
    <AppIcon name="help" :size="15" />
    {{ demo.resourceSample ? '当前显示虚构示例，用于评审布局；不代表平台实测数据。' : '采集接口尚未连接，暂无真实用量；可切换示例数据预览图表布局。' }}
  </div>

  <div class="metric-four">
    <div v-for="metric in metrics" :key="metric.label" class="metric-box">
      <label>{{ metric.label }}</label>
      <strong>{{ demo.resourceSample ? metric.value : '—' }}</strong>
      <small>{{ demo.resourceSample ? metric.note : '等待真实采集数据' }}</small>
    </div>
  </div>

  <div class="v3-resource-grid">
    <section class="panel">
      <div class="panel-heading">
        <div>
          <h2 class="panel-title">{{ chartTitle }}</h2>
          <p class="panel-subtitle">{{ demo.resourceSample ? 'MOCK · 示意图表' : '按 DemoRun、阶段与调用来源统一统计' }}</p>
        </div>
        <span class="mini-tag">{{ demo.resourceSample ? '示例' : '待接入' }}</span>
      </div>
      <div class="v3-resource-chart">
        <div v-for="bar in chartData" :key="bar.name">
          <span>{{ demo.resourceSample ? bar.value : '—' }}</span>
          <i :style="{ height: demo.resourceSample ? `${bar.height * 1.6}px` : '8px' }"></i>
          <small>{{ bar.name }}</small>
        </div>
      </div>
      <div class="graph-caption">
        {{ demo.resourceSample ? '柱形与数值为示例，仅用于高保真评审' : '等待导入调用与阶段执行记录' }}
        <button class="text-link" @click="openModal('resource-method')">统计口径 <AppIcon name="external" :size="11" /></button>
      </div>
    </section>

    <section class="panel">
      <div class="panel-heading"><h2 class="panel-title">资源效率，结合交付结果看</h2></div>
      <div class="panel-body">
        <div v-for="line in qualityLines" :key="line.title" class="v3-quality-line">
          <span><AppIcon name="target" :size="16" /></span>
          <div><b>{{ line.title }}</b><p>{{ line.desc }}</p></div>
          <small>{{ demo.resourceSample ? '示例观察' : '待采集' }}</small>
        </div>
      </div>
    </section>
  </div>

  <section class="panel" style="margin-top:20px">
    <div class="panel-heading">
      <h2 class="panel-title">{{ tableTitle }}</h2>
      <button class="text-link" @click="openModal('events')">关联运行 <AppIcon name="arrow" :size="12" /></button>
    </div>
    <div class="table-wrap">
      <table>
        <thead>
          <tr><th>关联对象</th><th>模型调用</th><th>输入 / 输出 Token</th><th>响应时延 P50</th><th>失败 / 重试</th><th>质量状态</th></tr>
        </thead>
        <tbody>
          <tr v-for="(row, index) in rows" :key="row.name">
            <td>{{ row.name }}<small>MOCK · 示例关联</small></td>
            <td>{{ demo.resourceSample ? [14, 22][index] : '—' }}</td>
            <td>{{ demo.resourceSample ? ['32.8K / 15.5K', '51.6K / 28.5K'][index] : '—' }}</td>
            <td>{{ demo.resourceSample ? ['1.8s', '2.4s'][index] : '—' }}</td>
            <td>{{ demo.resourceSample ? ['0 / 0', '1 / 1'][index] : '—' }}</td>
            <td>{{ demo.resourceSample ? '示例状态 · 待验真' : '待接入' }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import AppIcon from '../components/AppIcon.vue'
import PageIntro from '../components/PageIntro.vue'
import { demo } from '../store/demo'
import { openModal } from '../store/ui'

const tabs = [
  { id: 'overview', name: '全局概览' },
  { id: 'task', name: '任务分析' },
  { id: 'efficiency', name: '资源效率' }
]

const metrics = [
  { label: '总 Token', value: '128.4K', note: '输入 + 输出，不含虚构缓存收益' },
  { label: '模型调用', value: '36', note: '按运行关联调用记录' },
  { label: '累计耗时', value: '12m 48s', note: '示例墙钟时间' },
  { label: '估算成本', value: '¥ 8.62', note: '虚构计价 · 非账单' }
]

const stageBars = [
  { name: '知识检索', height: 24, tokens: '18.2K', minutes: [1.8], cost: [1.22] },
  { name: '影响分析', height: 43, tokens: '30.1K', minutes: [3.0], cost: [2.02] },
  { name: '方案设计', height: 35, tokens: '24.6K', minutes: [2.4], cost: [1.65] },
  { name: '编码实施', height: 85, tokens: '42.3K', minutes: [4.2], cost: [2.84] },
  { name: '质量验证', height: 30, tokens: '13.2K', minutes: [1.4], cost: [0.89] }
]

const chartData = computed(() => stageBars.map((bar, index) => ({
  name: bar.name,
  height: bar.height,
  value: demo.resourceTab === 'task'
    ? `${bar.minutes[0]} min`
    : demo.resourceTab === 'efficiency'
      ? `¥ ${bar.cost[0].toFixed(2)}`
      : bar.tokens
})))

const chartTitle = computed(() => demo.resourceTab === 'overview'
  ? '阶段资源分布'
  : demo.resourceTab === 'task' ? '当前任务阶段耗时' : '成本与任务完成情况')

const tableTitle = computed(() => demo.resourceTab === 'task'
  ? 'Agent / Tool 调用明细'
  : demo.resourceTab === 'efficiency' ? '模型配置与任务效果对比' : '任务级用量分析')

const qualityLines = [
  { title: '任务完成', desc: '完成 / 失败 / 人工接管' },
  { title: '质量结果', desc: '门禁通过、缺陷与返工' },
  { title: '资源消耗', desc: 'Token、成本、调用与耗时' },
  { title: '异常与重试', desc: '工具失败、超时与重复执行' }
]

const rows = [
  { name: 'DEMO-RUN-001 / 知识与分析' },
  { name: 'DEMO-RUN-001 / 编码与验证' }
]
</script>
