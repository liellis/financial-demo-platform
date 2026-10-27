<template>
  <PageIntro
    kicker="03 / REQUIREMENT TO DELIVERY"
    title="从一项需求，组织一次可信开发"
    desc="需求规格、实施计划、人工确认与 Agent 执行共享同一次运行。"
  >
    <button class="btn" @click="openModal('approval')"><AppIcon name="lock" :size="14" />实施计划与审批</button>
  </PageIntro>

  <div class="tabs">
    <button
      v-for="tab in tabs"
      :key="tab.id"
      :class="{ active: demo.devTab === tab.id }"
      @click="demo.devTab = tab.id"
    >{{ tab.name }}</button>
  </div>

  <section v-if="demo.devTab === 'requirement'" class="panel">
    <div class="panel-heading">
      <h2 class="panel-title">增量需求输入</h2>
      <span class="mini-tag">CR-001</span>
    </div>
    <div class="panel-body">
      <label class="input-label" for="requirement-text">业务诉求与约束</label>
      <textarea id="requirement-text" v-model="demo.requirementText" class="form-input" rows="7"></textarea>
      <div class="v3-accept">
        <b>验收维度</b>
        <span>业务资格与边界</span>
        <span>账务与幂等</span>
        <span>历史业务回归</span>
      </div>
      <div class="callout blue">
        <AppIcon name="book" :size="16" />
        <span>需求将引用知识准备阶段的候选资产。示例规格用于评审呈现，尚未调用模型分析。</span>
      </div>
      <button class="btn btn-primary" @click="saveRequirement">保存需求并查看示例 SDD <AppIcon name="arrow" :size="14" /></button>
    </div>
  </section>

  <section v-else-if="demo.devTab === 'spec'" class="panel">
    <div class="panel-heading">
      <h2 class="panel-title">需求规格与实施计划</h2>
      <span class="pill pending">{{ demo.approved ? '示例方案 · 已模拟确认' : '示例草稿 · 待确认' }}</span>
    </div>
    <div class="panel-body">
      <div class="v3-spec">
        <h3>01 · 业务目标</h3>
        <p>{{ demo.requirementText }}</p>
        <h3>02 · 资产复用与变更边界</h3>
        <p>复用已有卡片校验与账务接口；候选新增返现规则及记录，避免重复实现存量服务。</p>
        <h3>03 · 验证与交付条件</h3>
        <p>覆盖资格、卡状态、边界与重复交易；执行历史回归、安全检查和人工评审。真实标准需与业务方确认。</p>
      </div>
      <button class="btn btn-primary" @click="openModal('approval')"><AppIcon name="lock" :size="14" />查看方案并模拟确认</button>
      <button class="btn" @click="demo.devTab = 'execution'">查看 Agent 任务</button>
    </div>
  </section>

  <template v-else-if="demo.devTab === 'execution'">
    <ExecutionPanel @approval="openModal('approval')" />
    <div class="v3-next">
      <span>执行状态、日志和代码产物将来自底层任务事件</span>
      <button class="btn btn-primary" @click="simulateDevelopment">
        <AppIcon name="play" :size="13" />{{ demo.approved ? '演练执行并进入验证' : '先确认方案' }}
      </button>
    </div>
  </template>

  <section v-else class="panel">
    <div class="panel-heading">
      <h2 class="panel-title">代码变更与结果</h2>
      <span class="pill neutral">待真实产物</span>
    </div>
    <div class="panel-body">
      <div class="v3-empty">
        <AppIcon name="branch" :size="38" />
        <h3>等待绑定工程与代码差异</h3>
        <p>接入后展示修改文件、复用引用、代码差异与对应提交版本。</p>
        <button class="btn" @click="go('projects')">查看工程来源</button>
      </div>
    </div>
  </section>
</template>

<script setup>
import { useRouter } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import ExecutionPanel from '../components/ExecutionPanel.vue'
import PageIntro from '../components/PageIntro.vue'
import { demo, record } from '../store/demo'
import { openModal, showToast } from '../store/ui'

const router = useRouter()

const tabs = [
  { id: 'requirement', name: '业务需求' },
  { id: 'spec', name: '需求规格 / SDD' },
  { id: 'execution', name: 'Agent 任务执行' },
  { id: 'changes', name: '代码变更' }
]

function saveRequirement() {
  const text = demo.requirementText.trim()
  if (!text) {
    showToast('请填写业务诉求与约束')
    return
  }
  if (text !== demo.requirementText || !demo.requirementSaved) {
    demo.approved = false
    demo.verified = false
    demo.reviewed = false
    demo.developed = false
  }
  demo.requirementText = text
  demo.requirementSaved = true
  demo.devTab = 'spec'
  record('MOCK_REQUIREMENT_SAVED', '保存本地需求草稿，未调用模型')
}

function simulateDevelopment() {
  if (!demo.approved) {
    openModal('approval')
    return
  }
  demo.developed = true
  demo.step = 4
  record('MOCK_DEVELOPMENT_PREVIEW', '演练开发状态，未修改实际工程')
  router.push({ name: 'quality' })
  showToast('当前为示例状态，请继续体验质量验证')
}

function go(name) {
  router.push({ name })
}
</script>
