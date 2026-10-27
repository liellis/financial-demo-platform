import { demo, record, resetDemoState, stages } from './demo'
import { openModal, showToast } from './ui'

let timer = null

export function clearFlowTimer() {
  clearInterval(timer)
  timer = null
}

export function startFlowTimer() {
  clearFlowTimer()
  timer = setInterval(() => {
    if (!demo.running) return
    if (demo.step < 2) {
      demo.step += 1
      record('MOCK_STEP_CHANGED', stages[demo.step])
      if (demo.step === 2) {
        demo.running = false
        demo.waiting = 'approval'
        clearFlowTimer()
        showToast('示例方案已就绪，等待模拟确认')
      }
    } else if (demo.step === 3) {
      demo.step = 4
      demo.running = false
      demo.waiting = 'verification'
      record('MOCK_VALIDATION_PENDING', '等待示例质量验证')
      clearFlowTimer()
      showToast('示例开发阶段结束，请进入质量验证')
    }
  }, 3200)
}

export function startDemo() {
  resetDemoState()
  demo.running = true
  demo.waiting = false
  demo.step = 0
  record('MOCK_RUN_STARTED', '启动本地交互流程，未创建实际任务')
  startFlowTimer()
}

export function toggleDemo() {
  if (demo.waiting === 'approval') {
    openModal('approval')
    return
  }
  if (demo.waiting === 'verification') {
    showToast('请运行示例验证，再进行模拟评审')
    return
  }
  if (demo.paused) {
    demo.paused = false
    demo.running = true
    startFlowTimer()
    showToast('已继续原型演练')
    return
  }
  if (demo.running) {
    demo.running = false
    demo.paused = true
    clearFlowTimer()
    record('VIEW_PAUSED', '暂停本地演练画面，未暂停任何真实任务')
    showToast('已暂停原型演练')
    return
  }
  openModal('guide')
}

export function confirmApproval() {
  demo.approved = true
  demo.step = 3
  demo.waiting = false
  record('MOCK_PLAN_CONFIRMED', '浏览器本地确认，不创建真实审批')
  showToast('已模拟确认方案；未向任何平台提交审批')
  if (demo.running || window.location.hash.includes('overview')) {
    demo.running = true
    startFlowTimer()
  }
}

export function runVerification() {
  if (!demo.approved) {
    openModal('approval')
    showToast('请先模拟确认实施方案，再体验验证')
    return
  }
  demo.verified = true
  demo.step = 4
  demo.waiting = 'review'
  record('MOCK_CHECKS_FINISHED', '本地示例检查通过，等待模拟人工评审')
  showToast('示例检查已完成；真实测试与安全扫描尚未接入')
}

export function requestReview() {
  if (!demo.verified) {
    showToast('请先完成示例方案确认和示例验证')
    return
  }
  openModal('review')
}

export function confirmReview() {
  demo.reviewed = true
  demo.running = false
  demo.waiting = false
  demo.step = 5
  record('MOCK_REVIEW_CONFIRMED', '演练完成；未产生真实合并或发布')
  showToast('示例交付链已完成。真实平台、代码与报告仍待接入')
}

export function resetBusiness() {
  demo.businessResult = null
  showToast('已重置前端试算，未修改任何业务数据')
}
