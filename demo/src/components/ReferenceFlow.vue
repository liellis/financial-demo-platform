<template>
  <section class="panel reference-flow-panel">
    <div class="portal-flow-head">
      <div>
        <span class="v3-kicker">{{ scope === 'overview' ? 'UNIFIED DEMO PORTAL' : 'INCREMENTAL DEVELOPMENT WORKFLOW' }}</span>
        <h2>{{ scope === 'overview' ? 'Demo 演示的统一 Portal' : '存量系统增量需求开发 · 演示流程' }}</h2>
        <p>按提供的演示流程图对齐，点击模块或环节进入相应页面。</p>
      </div>
      <button v-if="scope !== 'overview'" class="btn btn-small" @click="showImage"><AppIcon name="expand" :size="13" />查看原始流程图</button>
    </div>

    <div class="reference-flow-scroll">
      <div class="reference-flow-grid">
        <section class="reference-pillar icode-pillar">
          <span class="reference-number">1</span>
          <a href="#/platform" @click.prevent="go('platform')">
            <span class="reference-pillar-icon"><AppIcon name="code" :size="25" /></span>
            <h3>iCode<br>功能展示</h3>
            <p>整体架构与模块入口</p>
            <span class="reference-enter">进入查看 <AppIcon name="arrow" :size="12" /></span>
          </a>
          <div class="reference-support"><span>Core</span><span>Teams</span><span>Talent</span></div>
        </section>

        <section class="reference-main">
          <div class="reference-main-title">
            <span class="reference-number">2</span>
            <a href="#/development" @click.prevent="go('development')">存量系统增量需求开发 <small>信用卡 Demo · 场景可替换</small></a>
          </div>
          <div class="reference-stages">
            <div v-for="(stage, index) in flowItems" :key="stage.id" class="reference-stage">
              <a :href="`#/${stage.id}`" @click.prevent="go(stage.id)">
                <span class="reference-stage-top"><small>0{{ index + 1 }}</small><AppIcon :name="stage.icon" :size="18" /></span>
                <h3>{{ stage.title }}</h3>
                <p>{{ stage.sub }}</p>
                <span class="reference-enter">进入查看 <AppIcon name="arrow" :size="11" /></span>
              </a>
              <div class="reference-support">
                <span v-for="role in stage.roles" :key="role">{{ role }}</span>
              </div>
            </div>
          </div>
        </section>

        <section class="reference-pillar rsi-pillar">
          <span class="reference-number">3</span>
          <a href="#/evolution" @click.prevent="go('evolution')">
            <span class="reference-pillar-icon"><AppIcon name="spark" :size="25" /></span>
            <h3>平台 RSI<br>自演进</h3>
            <p>反馈 · 评测 · 发布</p>
            <span class="reference-enter">进入查看 <AppIcon name="arrow" :size="12" /></span>
          </a>
          <div class="reference-support"><span>Talent</span></div>
        </section>

        <section class="reference-pillar token-pillar">
          <span class="reference-number">4</span>
          <a href="#/resources" @click.prevent="go('resources')">
            <span class="reference-pillar-icon"><AppIcon name="chart" :size="25" /></span>
            <h3>Token<br>资源监控</h3>
            <p>用量 · 时延 · 成本</p>
            <span class="reference-enter">进入查看 <AppIcon name="arrow" :size="12" /></span>
          </a>
          <div class="reference-support"><span>监控中心</span></div>
        </section>
      </div>
    </div>

    <div class="reference-flow-note">
      <span><AppIcon name="lock" :size="12" /> 实施计划人工确认 → 受控开发 → 质量门禁与交付评审</span>
      <span>点击模块或环节进入对应演示页面 · 状态仅为本地 MOCK</span>
    </div>
  </section>
</template>

<script setup>
import { useRouter } from 'vue-router'
import AppIcon from './AppIcon.vue'
import { flowItems } from '../store/demo'
import { openModal } from '../store/ui'

defineProps({
  scope: { type: String, default: 'overview' }
})

const router = useRouter()

function go(name) {
  router.push({ name })
}

function showImage() {
  openModal('original-image', {
    image: {
      path: 'assets/demo-flow-reference.png',
      title: '整体演示流程图',
      file: 'demo 界面流程图',
      width: 1911
    }
  })
}
</script>
