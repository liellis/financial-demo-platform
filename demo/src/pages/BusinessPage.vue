<template>
  <PageIntro
    kicker="06 / BUSINESS VALIDATION"
    title="把增量交付，带回业务验证"
    desc="信用卡返现是可替换的场景模板；本页为本地前端试算。"
  >
    <button class="btn" @click="go('overview')">查看研发过程</button>
    <button class="btn btn-primary" @click="reset"><AppIcon name="refresh" :size="14" />重置示例</button>
  </PageIntro>

  <div class="business-grid">
    <section class="panel">
      <div class="panel-heading"><h2 class="panel-title">信用卡消费返现 <small>文档业务样例</small></h2></div>
      <div class="panel-body">
        <span class="pill pending">MOCK · 无真实交易</span>
        <div class="bank-card">
          <small>DEMO BANK · SYNTHETIC DATA</small>
          <h3>•••• &nbsp;•••• &nbsp;•••• &nbsp;1026</h3>
          <p>PLATINUM &nbsp; / &nbsp; 模拟账户</p>
        </div>
        <label class="input-label" for="business-customer">客户与卡状态</label>
        <select id="business-customer" v-model="tier" class="form-input">
          <option value="platinum">白金客户 · 正常卡片</option>
          <option value="standard">普通客户 · 正常卡片</option>
          <option value="frozen">白金客户 · 冻结卡片</option>
        </select>
        <label class="input-label" for="business-amount">模拟消费金额（元）</label>
        <input id="business-amount" v-model="amount" class="form-input" type="number" min="0.01" max="1000000" step="0.01" @keyup.enter="calculate">
        <button class="btn btn-primary" style="margin-top:20px;width:100%" @click="calculate">
          <AppIcon name="play" :size="13" />体验示例试算
        </button>
        <div v-if="demo.businessResult" class="business-result">
          <small>前端规则示例 · 额外 1%</small>
          <strong>¥{{ demo.businessResult.reward }}</strong>
          <p>{{ demo.businessResult.message }}</p>
        </div>
      </div>
    </section>

    <div class="stack">
      <section class="panel">
        <div class="panel-heading"><h2 class="panel-title">本次增量，改变了什么？</h2></div>
        <div class="panel-body">
          <div class="before-after">
            <div>
              <h3>已有系统</h3>
              <p>客户、卡片与消费<br>账户与账务处理<br>卡状态及消费约束</p>
              <strong>复用存量能力</strong>
            </div>
            <div>
              <h3>增量需求</h3>
              <p>白金客户额外返现 1%<br>活动规则与权益记录<br>幂等及边界验证</p>
              <strong class="text-red">只增加必要能力</strong>
            </div>
          </div>
          <div class="callout">
            <AppIcon name="alert" :size="15" />
            <span>此页简化为白金客户 1% 示例试算，不覆盖活动期、商户、封顶或退款。完整规则待需求确认与真实业务接入。</span>
          </div>
          <button class="related-link" @click="go('knowledge')">平台怎样定位已有能力？ <AppIcon name="arrow" :size="13" /></button>
          <button class="related-link" @click="go('quality')">怎样保护原有业务约束？ <AppIcon name="arrow" :size="13" /></button>
        </div>
      </section>

      <section class="panel">
        <div class="panel-heading"><h2 class="panel-title">一项业务结果，背后的平台工作</h2></div>
        <div class="panel-body">
          <p class="subheading">理解工程 → 评估影响 → 复用资产 → Skills 执行 → 验证与审核。业务页面不替代这些研发证据。</p>
          <button class="text-link" style="margin-top:15px" @click="go('trace')">查看完整追溯链 <AppIcon name="arrow" :size="12" /></button>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import PageIntro from '../components/PageIntro.vue'
import { demo, record } from '../store/demo'
import { resetBusiness } from '../store/flow'
import { showToast } from '../store/ui'

const router = useRouter()
const tier = ref('platinum')
const amount = ref('1500')

function calculate() {
  const value = String(amount.value)
  const numeric = Number(value)
  if (!value || !Number.isFinite(numeric) || numeric <= 0 || numeric > 1000000 || !/^(\d+)(\.\d{1,2})?$/.test(value)) {
    showToast('请输入 0.01—1,000,000 之间、最多两位小数的金额')
    return
  }
  const cents = Math.round(numeric * 100)
  const reward = tier.value === 'platinum' ? Math.floor((cents + 50) / 100) : 0
  demo.businessResult = {
    reward: (reward / 100).toFixed(2),
    message: tier.value === 'frozen'
      ? '冻结卡示例：交易拒绝，不产生返现。'
      : tier.value === 'standard'
        ? '普通客户示例：不符合白金活动资格。'
        : '白金客户示例：按 1% 计算。仅为前端试算，不产生交易或账务。'
  }
  record('MOCK_BUSINESS_CALCULATED', '浏览器本地试算，不创建交易')
}

function reset() {
  resetBusiness()
}

function go(name) {
  router.push({ name })
}
</script>
