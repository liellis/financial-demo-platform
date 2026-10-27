<template>
  <Teleport to="body">
    <div v-if="ui.modal" class="overlay" :class="{ 'modal-overlay': !isDrawer }" @click.self="closeModal">
      <section
        v-if="type === 'project-form'"
        class="modal project-form-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="dialog-title"
      >
        <ProjectFormModal :id="payload.id || ''" @close="closeModal" />
      </section>

      <section
        v-else
        :class="[isDrawer ? 'drawer' : 'modal', { 'provided-image-modal': type === 'original-image' }]"
        role="dialog"
        aria-modal="true"
        aria-labelledby="dialog-title"
      >
        <div class="overlay-head">
          <div>
            <div class="eyebrow">{{ head.kicker }}</div>
            <h2 id="dialog-title">{{ head.title }}</h2>
            <p v-if="head.desc">{{ head.desc }}</p>
          </div>
          <button class="icon-button" aria-label="关闭对话框" @click="closeModal"><AppIcon name="close" :size="20" /></button>
        </div>

        <template v-if="type === 'evidence'">
          <span class="prototype-pill">MOCK · 示例证据，非真实工程数据</span>
          <div class="detail-section">
            <h3>平台为什么引用这项资产？</h3>
            <p>{{ evidence.desc }}<template v-if="evidence.asset">。本次任务把相关已有能力作为上下文，用于判断复用范围和验证安排。</template></p>
          </div>
          <div class="detail-section">
            <h3>来源与版本</h3>
            <dl>
              <dt>示例运行</dt><dd>DEMO-RUN-001</dd>
              <dt>来源能力</dt><dd>{{ evidence.asset ? 'CodeGraph / 知识工程' : '平台任务与证据服务' }}</dd>
              <dt>资产类型</dt><dd>{{ evidence.asset?.type || '研发产出物' }}</dd>
              <dt>源码位置</dt><dd>{{ evidence.asset?.source || '待绑定真实平台产物' }}</dd>
              <dt>代码 / 索引版本</dt><dd>待接入，不提供虚构提交号</dd>
              <dt>确认状态</dt><dd>{{ evidence.asset?.status || '示例展示，待核验' }}</dd>
            </dl>
          </div>
          <div class="detail-section">
            <h3>业务、实现与验证关联</h3>
            <div class="evidence-code">{{ evidence.asset?.method || 'Requirement → Rule → Code → Test → Approval' }}

上述路径与引用为原型示例。
接入后应返回 source / revision / artifact / checksum。</div>
            <button class="related-link" @click="goTrace">展开完整追溯链 <AppIcon name="arrow" :size="13" /></button>
          </div>
          <div class="detail-section">
            <h3>原始执行记录</h3>
            <div class="tech-panel" style="margin-top:0">
              <b>原始日志 · 未接入</b><br>没有真实 Skill 输出可展示。<br>
              <span class="line">接入后在此查看命令、退出码、日志和产物。</span>
            </div>
          </div>
          <div class="detail-footer">原型用于评审信息层级与交互，不代表已生成代码、执行测试或完成审批。所有数据必须关联实际运行后才能作为交付证据。</div>
        </template>

        <template v-else-if="type === 'approval'">
          <span class="pill pending">{{ demo.approved ? '本地原型已确认' : '等待模拟确认' }} · DEMO-PLAN-001</span>
          <div class="detail-section">
            <h3>变更目标</h3>
            <p>{{ scenarioName() }}。复用已有业务服务与工程规范，只扩展与本次需求有关的实现。</p>
          </div>
          <div class="detail-section">
            <h3>复用、扩展与保护</h3>
            <div class="rule-list">
              <div class="rule-item">
                <div><p>复用已有处理入口与基础组件</p><small>知识引用与源码关联随方案传入执行</small></div>
                <span class="pill success">复用</span>
              </div>
              <div class="rule-item">
                <div><p>新增规则与增量业务实现</p><small>限定到已确认的任务与工作区</small></div>
                <span class="pill progress">扩展</span>
              </div>
              <div class="rule-item">
                <div><p>关键业务约束与原有行为</p><small>新增验证与必要回归保持关联</small></div>
                <span class="pill pending">保护</span>
              </div>
            </div>
          </div>
          <div class="callout"><AppIcon name="alert" :size="16" /><span>确认项：业务规则版本与当前工程一致。点击下方按钮只会更新浏览器中的示例状态。</span></div>
          <div class="modal-actions">
            <button class="btn" @click="closeModal">返回查看</button>
            <button class="btn btn-primary" @click="approve">
              <AppIcon name="check" :size="14" />{{ demo.approved ? '已模拟确认' : '确认示例方案' }}
            </button>
          </div>
        </template>

        <template v-else-if="type === 'scenario'">
          <button
            v-for="option in scenarioOptions"
            :key="option.id"
            class="scenario-option"
            :class="{ active: demo.scenario === option.id }"
            @click="selectScenario(option.id)"
          >
            <span class="square-icon"><AppIcon :name="option.icon" /></span>
            <span><h3>{{ option.name }}</h3><p>{{ option.desc }}</p></span>
            <span v-if="demo.scenario === option.id" class="check"><AppIcon name="check" :size="16" /></span>
          </button>
          <div class="callout blue"><AppIcon name="folder" :size="15" /><span>切换会重置本地原型演练状态，不创建工程、任务或业务交易。</span></div>
        </template>

        <template v-else-if="type === 'integrations'">
          <span class="prototype-pill">连接状态来自原型配置 · 不代表可用性检测</span>
          <div style="margin-top:15px">
            <div v-for="row in integrations" :key="row.name" class="integration-row">
              <div><h3>{{ row.name }}</h3><p>{{ row.desc }}</p></div>
              <span class="pill neutral">待接入</span>
            </div>
          </div>
          <div class="detail-footer">后续对接任务启动、状态、事件、产出物与权限能力。界面不直接执行终端命令，也不读取真实平台凭据。</div>
        </template>

        <template v-else-if="type === 'skills'">
          <div class="rule-list">
            <div v-for="row in skillGroups" :key="row.num" class="asset-row">
              <span class="square-icon">{{ row.num }}</span>
              <div>
                <h3>{{ row.name }}</h3>
                <p>{{ row.platform }}</p>
                <div class="asset-chips"><span class="asset-chip">输入 / 产出：{{ row.desc }}</span></div>
              </div>
            </div>
          </div>
          <div class="callout blue"><AppIcon name="layers" :size="16" /><span>平台选择 Skill、提供相关上下文、传递产出、收集反馈。主屏展示工程工作，详情保留命令与来源。</span></div>
          <div class="detail-footer">本原型不启动任何外部 Skill 或 Agent。接入后，每次调用应关联平台任务 ID、Skill 版本和产出物。</div>
        </template>

        <template v-else-if="type === 'guide'">
          <div class="detail-section">
            <h3>01 / 理解已有工程</h3>
            <p>点击关系图，追溯业务知识、代码与测试。展现平台怎样形成有来源的任务上下文。</p>
          </div>
          <div class="detail-section">
            <h3>02 / 评估影响并组织研发</h3>
            <p>展示影响范围、复用建议和受保护约束。确认示例方案，体验 Skills 协同执行的呈现。</p>
          </div>
          <div class="detail-section">
            <h3>03 / 验证与交付</h3>
            <p>查看质量门禁、追溯链和业务验收。效能指标等待真实对照实验，不预设提升百分比。</p>
          </div>
          <div class="callout"><AppIcon name="play" :size="16" /><span>“体验流程”只演练页面状态，运行与审批始终标记 MOCK，不代表底层执行。</span></div>
          <div class="modal-actions">
            <button class="btn" @click="closeModal">自由浏览</button>
            <button class="btn btn-primary" @click="beginDemo"><AppIcon name="play" :size="14" />体验交互流程</button>
          </div>
        </template>

        <template v-else-if="type === 'events'">
          <span class="prototype-pill">MOCK / DEMO-RUN-001</span>
          <div v-for="event in eventList" :key="event.sequence" class="detail-section">
            <span class="mini-tag">#{{ event.sequence }} · {{ event.time }}</span>
            <h3 style="margin-top:12px">{{ event.type }}</h3>
            <p>{{ event.summary }}</p>
            <small style="color:#a4afbe">来源：本地原型，不是平台执行事件</small>
          </div>
          <div class="detail-footer">真实事件需包含 task / run / step / source / revision / artifact 等关联信息。当前没有真实执行日志。</div>
        </template>

        <template v-else-if="type === 'test-report'">
          <span class="prototype-pill">MOCK · 非真实报告</span>
          <div class="detail-section">
            <dl>
              <dt>运行来源</dt><dd>本地原型</dd>
              <dt>验证范围</dt><dd>新增需求 / 关键约束 / 关联回归 / 安全检查</dd>
              <dt>当前状态</dt><dd>{{ demo.verified ? '本地示例已通过' : '尚未演练' }}</dd>
              <dt>真实测试数量</dt><dd>未接入</dd>
              <dt>原始报告</dt><dd>待绑定真实产物 URI 与校验信息</dd>
            </dl>
          </div>
          <div class="detail-section">
            <h3>应呈现的验证链</h3>
            <button v-for="text in reportChain" :key="text" class="related-link" @click="goTrace">
              {{ text }}<AppIcon name="arrow" :size="12" />
            </button>
          </div>
        </template>

        <template v-else-if="type === 'review'">
          <div class="detail-section">
            <h3>模拟交付检查</h3>
            <p>示例需求、设计、构建、测试与安全检查已具备展示结果。真实评审需绑定代码版本、报告与审核权限。</p>
          </div>
          <div class="modal-actions">
            <button class="btn" @click="closeModal">返回</button>
            <button class="btn btn-primary" @click="finishReview"><AppIcon name="check" :size="14" />完成模拟评审</button>
          </div>
        </template>

        <template v-else-if="type === 'import-skills'">
          <div class="detail-section">
            <p>包含需求澄清、存量理解、影响分析、受控修改、测试执行及交付评审六类能力。真实文件来源与版本待平台接入时确认。</p>
          </div>
          <div class="modal-actions">
            <button class="btn" @click="closeModal">返回</button>
            <button class="btn btn-primary" @click="confirmImportSkills">确认导入本地示例</button>
          </div>
        </template>

        <template v-else-if="type === 'skill-detail'">
          <span class="prototype-pill">示例能力 · 版本待映射</span>
          <div class="detail-section">
            <h3>输入上下文</h3>
            <p>需求、源代码基线、知识索引、已确认变更范围与工程规范。</p>
            <h3>执行与产出</h3>
            <p>实际平台将记录 Skill 版本、输入引用、任务事件、执行结果与相关产物。</p>
          </div>
          <button class="btn" @click="openModal('events')">查看本地操作记录</button>
        </template>

        <template v-else-if="type === 'project-detail'">
          <div v-if="projectDetail">
            <span class="pill neutral">{{ projects.mode === 'persistent' ? '本地已保存' : '临时内存' }} · 未解压分析</span>
            <div class="detail-section">
              <dl>
                <dt>工程标识</dt><dd>{{ projectDetail.code }}</dd>
                <dt>技术栈</dt><dd>{{ projectDetail.language }}（人工填写）</dd>
                <dt>工程状态</dt><dd>{{ projectDetail.status === 'archived' ? '已归档' : '可用' }}</dd>
                <dt>创建时间</dt><dd>{{ projectFullDate(projectDetail.createdAt) }}</dd>
                <dt>更新时间</dt><dd>{{ projectFullDate(projectDetail.updatedAt) }}</dd>
                <dt>压缩包</dt><dd>{{ projectDetail.archiveName }}</dd>
                <dt>文件大小</dt><dd>{{ projectSize(projectDetail.archiveSize) }}</dd>
                <dt>SHA-256</dt><dd class="project-hash">{{ projectDetail.sha256 || '当前浏览器未提供摘要计算' }}</dd>
                <dt>知识与版本</dt><dd>待接入工程解析与知识索引</dd>
              </dl>
            </div>
            <div class="detail-section">
              <h3>工程说明</h3>
              <p class="project-description">{{ projectDetail.description || '暂无说明' }}</p>
            </div>
            <div class="project-detail-actions">
              <button class="btn" @click="downloadProject(projectDetail.id)"><AppIcon name="download" :size="14" />下载原始压缩包</button>
              <button class="btn" @click="editProject(projectDetail.id)">编辑工程</button>
              <button v-if="projectDetail.status !== 'archived'" class="btn btn-primary" @click="toggleBind(projectDetail.id)">
                {{ projectDetail.id === projects.activeId ? '取消当前绑定' : '设为当前演示工程' }}
              </button>
            </div>
            <div class="detail-footer">保存和绑定不代表完成代码扫描、知识构建或 Skill 执行。后续真实任务需关联压缩包解压结果、工程基线及知识版本。</div>
          </div>
        </template>

        <template v-else-if="type === 'project-delete'">
          <div v-if="projectDetail" class="detail-section">
            <h3>{{ projectDetail.name }}</h3>
            <p>{{ projectDetail.archiveName }} · {{ projectSize(projectDetail.archiveSize) }}</p>
            <p>删除后无法在本地工程库恢复，请确认已保留原始文件。{{ projectDetail.id === projects.activeId ? '当前演示工程的绑定也将解除。' : '' }}</p>
          </div>
          <div class="project-form-error">{{ deleteError }}</div>
          <div class="modal-actions">
            <button class="btn" @click="closeModal">取消</button>
            <button class="btn project-danger" :disabled="projects.busy" @click="confirmDelete">确认删除本地工程</button>
          </div>
        </template>

        <template v-else-if="type === 'original-image'">
          <div v-if="payload.image">
            <div class="provided-image-toolbar">
              <span>默认适应窗口，可切换到原始尺寸查看细节。</span>
              <button class="btn btn-small" @click="imageFull = !imageFull">{{ imageFull ? '适应窗口' : '原始尺寸' }}</button>
            </div>
            <div class="provided-image-scroll">
              <img
                :src="payload.image.path"
                :alt="payload.image.title"
                :style="{ width: imageFull ? `${payload.image.width}px` : '100%' }"
              >
            </div>
          </div>
        </template>

        <template v-else-if="type === 'resource-method'">
          <div class="detail-section">
            <p>Token 按供应方返回的输入、输出与缓存口径计数；成本按模型、价格版本与币种计算，避免跨模型直接累加单价。</p>
            <p>任务历时使用墙钟时间；阶段累计耗时与并行 Agent 工时分开统计。重试成本计入同一任务，同时展示质量、成功率和返工。</p>
            <p>当前数字均为虚构示例，没有真实采集数据或账单。</p>
          </div>
        </template>

        <template v-else-if="type === 'rollback'">
          <div class="detail-section">
            <h3>回滚条件</h3>
            <p>固定任务集出现退化，或运行观察发现失败率、质量和成本异常时提出回滚申请。</p>
            <h3>操作边界</h3>
            <p>选择已审核的历史版本，保留原因、审批人和关联任务。原型不调用真实发布或回滚接口。</p>
          </div>
        </template>

        <template v-else-if="type === 'evolution-source'">
          <div class="detail-section">
            <h3>候选来源</h3>
            <p>候选改进由运行反馈、失败用例与人工评审建议汇总形成。原型不展示真实反馈数据，也不自动修改能力版本。</p>
          </div>
        </template>

        <template v-else-if="type === 'evolution-evaluation'">
          <div class="detail-section">
            <h3>固定任务集评测</h3>
            <p>评测需在相同任务集、相同验收口径下比较新旧版本，记录收益、退化、时延与成本。当前未导入真实评测结果。</p>
          </div>
        </template>
      </section>
    </div>
  </Teleport>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from './AppIcon.vue'
import ProjectFormModal from './ProjectFormModal.vue'
import { assets, demo, record, resetDemoState } from '../store/demo'
import { confirmApproval, confirmReview, startDemo } from '../store/flow'
import { closeModal, openModal, showToast, ui } from '../store/ui'
import {
  bindProject,
  downloadProject as downloadProjectFile,
  getProject,
  projectFullDate,
  projectSize,
  projects,
  removeProject
} from '../services/projects'

const router = useRouter()
const type = computed(() => ui.modal?.type || '')
const payload = computed(() => ui.modal?.payload || {})
const deleteError = ref('')
const imageFull = ref(false)

const drawerTypes = ['evidence', 'integrations', 'skills', 'events', 'test-report', 'project-detail']
const isDrawer = computed(() => drawerTypes.includes(type.value))

const scenarioOptions = [
  { id: 'generic', icon: 'folder', name: '存量业务规则增量扩展', desc: '业务无关框架 · 待绑定示例工程与增量需求' },
  { id: 'card', icon: 'credit', name: '信用卡消费返现增量开发', desc: '设计文档样例 · 白金客户额外 1% 返现 · 前端示例' }
]

const integrations = [
  { name: 'iCode', desc: '代码修改、工具调用与测试产物' },
  { name: 'Agent Teams', desc: '需求、设计与工程任务协作' },
  { name: 'Talent', desc: 'Skill 版本、评测与能力资产' },
  { name: 'CodeGraph / 知识工程', desc: '代码关系、业务规则与影响分析' },
  { name: '质量与监控平台', desc: '测试、安全、模型与执行记录' },
  { name: '银行 Demo 业务系统', desc: '真实构建后的业务验收入口' }
]

const skillGroups = [
  { num: '01', name: '知识检索与工程理解', platform: 'CodeGraph / 知识工程', desc: '业务规则、相关代码、已有测试' },
  { num: '02', name: '需求分析与实施设计', platform: 'Agent Teams', desc: '验收条件、变更边界、SDD 计划' },
  { num: '03', name: '编码、规范与增量修改', platform: 'iCode / Talent', desc: '已批准范围、资产引用、开发规范' },
  { num: '04', name: '质量验证与评审准备', platform: '测试 / 安全工具', desc: '新增及回归用例、问题处理、报告' }
]

const reportChain = [
  '需求验收条件 → 对应用例',
  '保护规则 → 历史回归',
  '发现问题 → 修复与复验',
  '检查结果 → 门禁与人工审核'
]

const eventList = computed(() => {
  if (demo.events.length) return [...demo.events].reverse()
  return [
    { sequence: 1, time: '示例', type: 'KNOWLEDGE_REFERENCED', summary: '形成业务、代码与测试资产关联（预设展示内容）' },
    { sequence: 2, time: '示例', type: 'IMPACT_PROPOSED', summary: '形成影响分析与待确认事项（预设展示内容）' }
  ]
})

const evidence = computed(() => {
  const id = payload.value.id
  const asset = assets.find((item) => item.id === id)
  let title = asset?.name || '研发任务关联证据'
  let desc = asset?.desc || '展示产出物、工程版本、执行来源与关联关系的详情样式。'
  if (id?.startsWith('gate-')) {
    title = ['需求确认门禁', '设计评估门禁', '构建检查门禁', '测试验证门禁', '安全检查门禁', '人工评审门禁'][Number(id.split('-')[1])]
    desc = '门禁的范围、验证结果与审核版本，保持独立可追溯。'
  }
  if (id?.startsWith('trace-')) {
    title = ['结构化需求', '业务规则引用', '代码位置', '提交与变更', '测试结果', '质量门禁', '审核记录'][Number(id.split('-')[1])]
    desc = '该节点关联到同一个 DemoRun；真实文件、版本和报告等待后续平台接入。'
  }
  return { title, desc, asset }
})

const projectDetail = computed(() => getProject(payload.value.id))

const head = computed(() => {
  switch (type.value) {
    case 'evidence':
      return { kicker: 'EVIDENCE EXPLORER', title: evidence.value.title, desc: evidence.value.desc }
    case 'approval':
      return { kicker: 'PLAN REVIEW', title: '先确认方案，再执行变更', desc: '此窗口演示方案审核体验，不创建真实平台审批。' }
    case 'scenario':
      return { kicker: 'SCENARIO REGISTRY', title: '选择演示场景', desc: '演示框架可复用，工程和业务规则通过配置接入。' }
    case 'integrations':
      return { kicker: 'PLATFORM ADAPTERS', title: '已有能力，统一接入', desc: '当前是本地高保真原型，以下平台均未连接。' }
    case 'skills':
      return { kicker: 'SKILL ORCHESTRATION', title: '平台如何组织已有 Skills', desc: '能力名称是展示分类；实际 Skill 名称、版本及命令待映射。' }
    case 'guide':
      return { kicker: 'PRESENTATION GUIDE', title: '先看懂平台，再深入工程', desc: '建议用一项增量任务，串起三个关键价值。' }
    case 'events':
      return { kicker: 'RUN EVENT STREAM', title: '任务事件与来源', desc: '刷新后本地操作记录不保留；接入后由平台持久化并支持断线恢复。' }
    case 'test-report':
      return { kicker: 'QUALITY EVIDENCE', title: '示例验证报告', desc: '此报告用于评审布局与追溯交互，不代表真实测试运行。' }
    case 'review':
      return { kicker: 'HUMAN REVIEW', title: '确认示例交付评审', desc: '这里只更新本地演练，不会批准真实代码合并或发布。' }
    case 'import-skills':
      return { kicker: 'MOCK / PRESENTATION PROTOTYPE', title: '导入场景 Skills 清单', desc: '仅配置本地原型，不安装或执行真实 Skill。' }
    case 'skill-detail':
      return { kicker: 'MOCK / PRESENTATION PROTOTYPE', title: payload.value.skill || 'Skill 详情', desc: '能力名称为展示示例，真实版本与来源待映射。' }
    case 'project-detail':
      return { kicker: 'CODE PROJECT / LOCAL ASSET', title: projectDetail.value?.name || '代码工程', desc: '工程详情与原始代码压缩包' }
    case 'project-delete':
      return { kicker: 'DELETE LOCAL PROJECT', title: '删除代码工程', desc: '该操作只移除此浏览器中的工程记录和压缩包。' }
    case 'original-image':
      return { kicker: 'PROVIDED ORIGINAL IMAGE', title: payload.value.image?.title || '原始图片', desc: `来源：${payload.value.image?.file || ''}` }
    case 'resource-method':
      return { kicker: 'MOCK / PRESENTATION PROTOTYPE', title: '资源指标统计口径', desc: '正式数据应关联调用 ID、DemoRun 与采集来源。' }
    case 'rollback':
      return { kicker: 'MOCK / PRESENTATION PROTOTYPE', title: '版本回滚与运行观察', desc: '当前无真实版本发布，以下为控制流程。' }
    case 'evolution-source':
      return { kicker: 'MOCK / PRESENTATION PROTOTYPE', title: '候选来源', desc: '示例流程，不引用真实运行反馈。' }
    case 'evolution-evaluation':
      return { kicker: 'MOCK / PRESENTATION PROTOTYPE', title: '评测示例', desc: '真实评测结果尚未导入。' }
    default:
      return { kicker: 'MOCK', title: '原型详情', desc: '' }
  }
})

watch(type, () => {
  deleteError.value = ''
  imageFull.value = false
})

function scenarioName() {
  return demo.scenario === 'card' ? '信用卡消费返现增量开发' : '存量业务规则增量扩展'
}

function approve() {
  confirmApproval()
  closeModal()
}

function beginDemo() {
  closeModal()
  startDemo()
  router.push({ name: 'overview' })
}

function finishReview() {
  confirmReview()
  closeModal()
}

function confirmImportSkills() {
  demo.skillsImported = true
  record('MOCK_SKILLS_SELECTED', '选择六类示例能力，未执行安装')
  closeModal()
  showToast('已加入本地演示清单')
}

function selectScenario(kind) {
  const page = router.currentRoute.value.name
  resetDemoState()
  demo.scenario = kind
  closeModal()
  showToast('已切换示例场景，并重置本地演练状态')
  if (page) router.push({ name: page })
}

function goTrace() {
  closeModal()
  router.push({ name: 'trace' })
}

function editProject(id) {
  closeModal()
  setTimeout(() => openModal('project-form', { id }), 0)
}

async function toggleBind(id) {
  try {
    await bindProject(projects.activeId === id ? null : id)
    closeModal()
    showToast('已更新当前演示工程；尚未启动代码分析')
  } catch (error) {
    showToast('保存工程绑定失败，请重试')
  }
}

async function confirmDelete() {
  if (projects.busy) return
  projects.busy = true
  deleteError.value = ''
  try {
    await removeProject(payload.value.id)
    closeModal()
    showToast('已删除本地工程')
  } catch (error) {
    deleteError.value = '删除失败，请重试'
  } finally {
    projects.busy = false
  }
}

function downloadProject(id) {
  downloadProjectFile(id)
}
</script>
