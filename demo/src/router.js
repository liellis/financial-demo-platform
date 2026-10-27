import { createRouter, createWebHashHistory } from 'vue-router'
import OverviewPage from './pages/OverviewPage.vue'
import DevelopmentPage from './pages/DevelopmentPage.vue'
import KnowledgePage from './pages/KnowledgePage.vue'
import SkillsPage from './pages/SkillsPage.vue'
import DevelopPage from './pages/DevelopPage.vue'
import QualityPage from './pages/QualityPage.vue'
import TracePage from './pages/TracePage.vue'
import EfficiencyPage from './pages/EfficiencyPage.vue'
import BusinessPage from './pages/BusinessPage.vue'
import EvolutionPage from './pages/EvolutionPage.vue'
import PlatformPage from './pages/PlatformPage.vue'
import ResourcesPage from './pages/ResourcesPage.vue'
import ProjectsPage from './pages/ProjectsPage.vue'

const routes = [
  { path: '/', redirect: '/overview' },
  { path: '/overview', name: 'overview', component: OverviewPage },
  { path: '/development', name: 'development', component: DevelopmentPage },
  { path: '/knowledge', name: 'knowledge', component: KnowledgePage },
  { path: '/skills', name: 'skills', component: SkillsPage },
  { path: '/develop', name: 'develop', component: DevelopPage },
  { path: '/quality', name: 'quality', component: QualityPage },
  { path: '/trace', name: 'trace', component: TracePage },
  { path: '/efficiency', name: 'efficiency', component: EfficiencyPage },
  { path: '/business', name: 'business', component: BusinessPage },
  { path: '/evolution', name: 'evolution', component: EvolutionPage },
  { path: '/platform', name: 'platform', component: PlatformPage },
  { path: '/resources', name: 'resources', component: ResourcesPage },
  { path: '/projects', name: 'projects', component: ProjectsPage },
  { path: '/:pathMatch(.*)*', redirect: '/overview' }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
  scrollBehavior() {
    return { top: 0 }
  }
})

export default router
