<template>
  <div class="graph-wrap">
    <svg class="graph-svg" viewBox="0 0 674 330" role="group" aria-label="示例业务知识与代码资产关系图">
      <defs>
        <marker id="end-arrow" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="4" markerHeight="4" orient="auto">
          <path d="M0 0 10 5 0 10" fill="#c3cedf" />
        </marker>
      </defs>
      <path class="wire" d="M180 104H246" marker-end="url(#end-arrow)" />
      <path class="wire-hot" d="M326 135V199" />
      <path class="wire" d="M406 104h27V47h44" marker-end="url(#end-arrow)" />
      <path class="wire" d="M406 104h27v57h44" marker-end="url(#end-arrow)" />
      <path class="wire wire-dash" d="M406 231h27v44h44" marker-end="url(#end-arrow)" />
      <path class="wire wire-dash" d="M180 104h22v171h275" />
      <text class="link-label" x="194" y="93">关联</text>
      <text class="link-label" x="337" y="171">增量扩展</text>
      <text class="link-label" x="437" y="269">验证</text>
      <g
        v-for="(node, index) in nodes"
        :key="`${node.id}-${index}`"
        class="graph-node"
        :class="{ hot: node.hot }"
        tabindex="0"
        role="button"
        :aria-label="`查看${node.label}的示例证据`"
        @click="$emit('evidence', node.id)"
        @keydown.enter.prevent="$emit('evidence', node.id)"
      >
        <rect class="node-bg" :x="node.x" :y="node.y" :width="node.w" height="63" rx="8" />
        <rect class="icon-bg" :x="node.x + 11" :y="node.y + 16" width="25" height="25" rx="6" />
        <g class="node-icon" :transform="`translate(${node.x + 16} ${node.y + 21}) scale(.62)`" v-html="icons[node.icon]" />
        <text class="node-label" :x="node.x + 45" :y="node.y + 26">{{ node.label }}</text>
        <text class="node-small" :x="node.x + 45" :y="node.y + 43">{{ node.sub }}</text>
      </g>
    </svg>
    <div class="graph-legend">
      <span><i class="legend-square red"></i>候选变更</span>
      <span><i class="legend-square green"></i>复用 / 保护</span>
      <span><i class="legend-square"></i>关联依赖</span>
      <span style="margin-left:auto">示例关系图 · 点击节点查看依据</span>
    </div>
  </div>
</template>

<script setup>
import { icons } from '../icons'

defineEmits(['evidence'])

const nodes = [
  { id: 'rule', x: 30, y: 72, w: 150, label: '客户与业务规则', sub: 'BUSINESS KNOWLEDGE', icon: 'book' },
  { id: 'service', x: 246, y: 72, w: 160, label: '已有业务处理服务', sub: 'CHANGE CANDIDATE', icon: 'code', hot: true },
  { id: 'rule', x: 246, y: 199, w: 160, label: '新增规则与约束', sub: 'INCREMENTAL CHANGE', icon: 'spark', hot: true },
  { id: 'ledger', x: 477, y: 15, w: 154, label: '账户与账务组件', sub: 'PROTECTED ASSET', icon: 'database' },
  { id: 'service', x: 477, y: 129, w: 154, label: '事件与通知服务', sub: 'REUSABLE SERVICE', icon: 'layers' },
  { id: 'tests', x: 477, y: 243, w: 154, label: '已有回归测试', sub: 'TEST ASSET', icon: 'shield' }
]
</script>
