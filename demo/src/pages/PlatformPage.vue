<template>
  <template v-if="demo.platformTab === 'overview'">
    <PageIntro
      kicker="01 / ICODE PLATFORM"
      title="iCode 整体架构总览"
      desc="组织级管理、多引擎核心部署、自演进与全链路监控。"
    >
      <button class="btn" @click="openModal('integrations')"><AppIcon name="plug" :size="14" />查看集成状态</button>
    </PageIntro>

    <div class="portal-secondary-heading">
      <h2>iCode 模块入口</h2>
      <span>模块与架构图对应；真实产品地址待配置</span>
    </div>
    <div class="portal-module-links">
      <a v-for="module in modules" :key="module.id" :href="module.url || '#'" @click="onModuleClick($event, module)">
        <span class="module-icon"><AppIcon :name="module.icon" :size="19" /></span>
        <div>
          <h3>{{ module.name }}</h3>
          <p>{{ module.desc }}</p>
          <small>{{ module.note }}</small>
        </div>
        <AppIcon name="arrow" :size="15" />
      </a>
    </div>

    <section class="panel portal-platform-image">
      <div class="portal-platform-caption">
        <h2>iCode 整体架构总览</h2>
        <button class="text-link" @click="showArchitecture"><AppIcon name="expand" :size="14" />放大查看原图</button>
      </div>
      <button class="architecture-image-button" aria-label="放大 iCode 总体架构图" @click="showArchitecture">
        <img :src="architectureImage" alt="用户提供的 iCode 整体架构总览：组织级 Harness 管理 iCode-Teams，单机部署 iCode-Core 与 OpenCode，统一监控中心，以及系统自演进 iCode-Talents。">
      </button>
      <div class="provided-image-caption">原始架构图 · iCode整体架构总览 infographics.png</div>
    </section>
  </template>

  <template v-else>
    <PageIntro
      kicker="01 / ICODE CAPABILITY MAP"
      title="平台能力如何协同，完成一次交付"
      desc="从能力地图进入说明、执行示例与关联记录。"
    >
      <button class="btn" @click="openModal('integrations')"><AppIcon name="plug" :size="14" />查看集成状态</button>
    </PageIntro>

    <div class="v3-platform-tabs">
      <button
        v-for="tab in tabs"
        :key="tab.id"
        :class="{ active: demo.platformTab === tab.id }"
        @click="demo.platformTab = tab.id"
      >{{ tab.name }}</button>
    </div>

    <section class="panel v3-cap-detail">
      <span class="module-icon"><AppIcon :name="detail.icon" :size="28" /></span>
      <h2>{{ detail.title }}</h2>
      <p>{{ detail.desc }}</p>
      <div class="v3-three">
        <div v-for="(item, index) in detail.items" :key="item.title">
          <small>0{{ index + 1 }}</small>
          <h3>{{ item.title }}</h3>
          <p>{{ item.desc }}</p>
        </div>
      </div>
      <button class="btn btn-primary" @click="go('knowledge')">进入主流程查看示例 <AppIcon name="arrow" :size="14" /></button>
      <button class="btn" @click="openModal('events')">关联运行记录</button>
    </section>

    <div class="v3-three v3-values">
      <div v-for="value in values" :key="value.title">
        <h3>{{ value.title }}</h3>
        <p>{{ value.desc }}</p>
      </div>
    </div>
  </template>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import PageIntro from '../components/PageIntro.vue'
import { demo } from '../store/demo'
import { openModal } from '../store/ui'

const router = useRouter()
const architectureImage = `${import.meta.env.BASE_URL}assets/icode-architecture.png`

const modules = [
  { id: 'agent', icon: 'code', name: 'iCode-Core', desc: '单机部署与编码执行', note: '产品地址待配置 · 当前为演示详情' },
  { id: 'teams', icon: 'users', name: 'iCode-Teams', desc: '组织级 Harness 管理', note: '进入 iCode-Teams 产品页面', url: 'http://127.0.0.1:45970/teams?demo-role=owner#/projects?project=teams&tab=content' },
  { id: 'talent', icon: 'spark', name: 'iCode-Talents', desc: '系统自演进与能力沉淀', note: '进入 iCode-Talents 产品页面', url: 'http://127.0.0.1:45980/talent?demo-role=owner' },
  { id: 'environment', icon: 'database', name: 'OpenCode', desc: '单机执行引擎', note: '产品地址待配置 · 当前为演示详情' },
  { id: 'mcp', icon: 'plug', name: 'MCP / Tools', desc: '工具治理与集成', note: '产品地址待配置 · 当前为演示详情' },
  { id: 'resources', icon: 'chart', name: '统一监控中心', desc: 'Token、资源与模型调用监控', note: '进入当前 Portal 的资源监控示例' }
]

const tabs = [
  { id: 'overview', name: '平台总览' },
  { id: 'agent', name: 'Code Agent' },
  { id: 'teams', name: 'Agent Teams' },
  { id: 'talent', name: 'Talent / Skills' },
  { id: 'mcp', name: 'MCP / Tools' },
  { id: 'environment', name: '模型与环境' }
]

const detailMap = {
  agent: {
    icon: 'code',
    title: 'iCode-Core · 编码与执行能力',
    desc: '检索工程上下文，依据已批准的计划修改代码，并将结果送往验证。',
    items: [
      { title: '输入上下文', desc: '需求、知识索引、源代码基线与权限范围' },
      { title: '执行与协作', desc: '专业 Skill、工具与对应任务运行记录' },
      { title: '产出与证据', desc: '代码差异、质量报告、来源和版本关联' }
    ]
  },
  teams: {
    icon: 'users',
    title: 'iCode-Teams · 组织级 Harness 管理',
    desc: '需求分析、架构、开发与验证分工协作，保留每项任务的输入、产出和审核边界。',
    items: [
      { title: '输入上下文', desc: '需求、知识索引、源代码基线与权限范围' },
      { title: '执行与协作', desc: '专业 Skill、工具与对应任务运行记录' },
      { title: '产出与证据', desc: '代码差异、质量报告、来源和版本关联' }
    ]
  },
  talent: {
    icon: 'layers',
    title: 'iCode-Talents · 系统自演进',
    desc: '复用既有工程规范与专业 Skills，记录每次调用所使用的能力版本。',
    items: [
      { title: '输入上下文', desc: '需求、知识索引、源代码基线与权限范围' },
      { title: '执行与协作', desc: '专业 Skill、工具与对应任务运行记录' },
      { title: '产出与证据', desc: '代码差异、质量报告、来源和版本关联' }
    ]
  },
  mcp: {
    icon: 'plug',
    title: 'MCP / Tools · 工具连接',
    desc: '连接代码仓、知识、测试与安全工具，将调用结果关联到当前运行。',
    items: [
      { title: '输入上下文', desc: '需求、知识索引、源代码基线与权限范围' },
      { title: '执行与协作', desc: '专业 Skill、工具与对应任务运行记录' },
      { title: '产出与证据', desc: '代码差异、质量报告、来源和版本关联' }
    ]
  },
  environment: {
    icon: 'database',
    title: 'OpenCode · 单机执行引擎',
    desc: '管理模型配置、隔离执行环境与资源观测；当前没有连接模型或任务运行时。',
    items: [
      { title: '输入上下文', desc: '需求、知识索引、源代码基线与权限范围' },
      { title: '执行与协作', desc: '专业 Skill、工具与对应任务运行记录' },
      { title: '产出与证据', desc: '代码差异、质量报告、来源和版本关联' }
    ]
  }
}

const detail = computed(() => detailMap[demo.platformTab] || detailMap.agent)

const values = [
  { title: '研发生产力', desc: '以 Skills 衔接需求、实施和验证' },
  { title: '存量资产复用', desc: '让业务知识、代码与测试成为任务上下文' },
  { title: '质量与安全', desc: '以变更边界、门禁和证据支撑交付' }
]

function onModuleClick(event, module) {
  if (module.url) return
  event.preventDefault()
  openModule(module.id)
}

function openModule(id) {
  if (id === 'resources') {
    router.push({ name: 'resources' })
    return
  }
  demo.platformTab = id
}

function showArchitecture() {
  openModal('original-image', {
    image: {
      path: 'assets/icode-architecture.png',
      title: 'iCode 整体架构总览',
      file: 'iCode整体架构总览 infographics.png',
      width: 1672
    }
  })
}

function go(name) {
  router.push({ name })
}
</script>
