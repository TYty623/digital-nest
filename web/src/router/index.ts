import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: '/', name: 'home', component: HomeView },
    { path: '/habitat', name: 'pocket-habitat', component: () => import('../views/HabitatStudioView.vue') },
    { path: '/habitat/play', name: 'habitat-play', component: () => import('../views/PocketHabitatView.vue') },
    { path: '/examples', name: 'examples', component: () => import('../views/ExamplesView.vue') },
    { path: '/community', name: 'community', component: () => import('../views/CommunityView.vue') },
    { path: '/examples/:slug', name: 'example-memorial', component: () => import('../views/ExampleMemorialView.vue') },
    { path: '/pricing', name: 'pricing', component: () => import('../views/PricingView.vue') },
    { path: '/help', name: 'help', component: () => import('../views/HelpView.vue') },
    { path: '/health', name: 'health', component: () => import('../views/HealthView.vue') },
    { path: '/privacy', name: 'privacy', component: () => import('../views/LegalView.vue'), meta: { legalPage: 'privacy' } },
    { path: '/terms', name: 'terms', component: () => import('../views/LegalView.vue'), meta: { legalPage: 'terms' } },
    { path: '/content', name: 'content-rules', component: () => import('../views/LegalView.vue'), meta: { legalPage: 'content' } },
    { path: '/refund', name: 'refund-rules', component: () => import('../views/LegalView.vue'), meta: { legalPage: 'refund' } },
    { path: '/legal/privacy', redirect: '/privacy' },
    { path: '/legal/terms', redirect: '/terms' },
    { path: '/legal/content', redirect: '/content' },
    { path: '/create', name: 'create', component: () => import('../views/CreateView.vue') },
    { path: '/login', name: 'login', component: () => import('../views/LoginView.vue') },
    { path: '/account', name: 'account', component: () => import('../views/AccountView.vue') },
    { path: '/account/orders', name: 'orders', component: () => import('../views/OrdersView.vue') },
    { path: '/custom-service', name: 'custom-service', component: () => import('../views/CustomServiceView.vue') },
    { path: '/admin', name: 'admin', component: () => import('../views/AdminView.vue') },
    { path: '/editor/:id', name: 'editor', component: () => import('../views/EditorView.vue') },
    { path: '/editor/:id/themes', name: 'theme-studio', component: () => import('../views/ThemeStudioView.vue') },
    { path: '/editor/:id/room', name: 'memorial-room', component: () => import('../views/MemorialRoomView.vue') },
    { path: '/editor/:id/ritual', name: 'ritual-center', component: () => import('../views/RitualCenterView.vue') },
    { path: '/editor/:id/archive', name: 'life-archive', component: () => import('../views/LifeArchiveView.vue') },
    { path: '/editor/:id/calendar', name: 'memory-calendar', component: () => import('../views/MemoryCalendarView.vue') },
    { path: '/editor/:id/yearbook', name: 'yearbook', component: () => import('../views/YearbookView.vue') },
    { path: '/editor/:id/digital-life', name: 'digital-life', component: () => import('../views/DigitalLifeView.vue') },
    { path: '/m/:slug', name: 'public-memorial', component: () => import('../views/PublicMemorialView.vue') },
    { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('../views/NotFoundView.vue') },
  ],
})

export default router
