import { createRouter, createWebHistory } from 'vue-router'
import { getAuthState, getAdminAuthState } from '../utils/auth'

const routes = [
  {
    path: '/auth',
    name: 'auth',
    component: () => import('../pages/Auth.vue'),
    meta: { public: true }
  },
  {
    path: '/onboarding',
    name: 'onboarding',
    component: () => import('../pages/Onboarding.vue')
  },
  { path: '/', name: 'home', component: () => import('../pages/Home.vue') },
  { path: '/checkin', name: 'checkin', component: () => import('../pages/Checkin.vue') },
  { path: '/track', name: 'track', component: () => import('../pages/Track.vue') },
  {
    path: '/track/:id',
    name: 'track-detail',
    component: () => import('../pages/TrackDetail.vue')
  },
  { path: '/stats', name: 'stats', component: () => import('../pages/Stats.vue') },
  { path: '/rank', name: 'rank', component: () => import('../pages/Rank.vue') },
  { path: '/summary', name: 'summary', component: () => import('../pages/Summary.vue') },
  { path: '/mine', name: 'mine', component: () => import('../pages/Mine.vue') },
  { path: '/settings', name: 'settings', component: () => import('../pages/Settings.vue') },
  {
    path: '/admin/login',
    name: 'admin-login',
    component: () => import('../pages/admin/AdminLogin.vue'),
    meta: { public: true }
  },
  {
    path: '/admin',
    component: () => import('../pages/admin/AdminLayout.vue'),
    children: [
      {
        path: '',
        name: 'admin-overview',
        component: () => import('../pages/admin/AdminOverview.vue')
      },
      {
        path: 'users',
        name: 'admin-users',
        component: () => import('../pages/admin/AdminUsers.vue')
      },
      {
        path: 'records',
        name: 'admin-records',
        component: () => import('../pages/admin/AdminRecords.vue')
      },
      {
        path: 'routes',
        name: 'admin-routes',
        component: () => import('../pages/admin/AdminRoutes.vue')
      },
      {
        path: 'rank',
        name: 'admin-rank',
        component: () => import('../pages/admin/AdminRank.vue')
      }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  if (to.path.startsWith('/admin')) {
    const { user: adminUser } = getAdminAuthState()
    const isAdmin = adminUser?.isAdmin === true

    if (to.path === '/admin/login') {
      return isAdmin ? '/admin' : true
    }
    if (!adminUser) return { path: '/admin/login' }
    if (!isAdmin) return { path: '/admin/login', query: { error: 'forbidden' } }
    return true
  }

  const { user } = getAuthState()
  const onboarded = user?.onboarded === true

  if (to.meta.public) {
    if (!user) return true
    return onboarded ? '/' : '/onboarding'
  }

  if (!user) {
    return { path: '/auth', query: { redirect: to.fullPath } }
  }
  if (!onboarded && to.path !== '/onboarding') {
    return { path: '/onboarding', query: { redirect: to.fullPath } }
  }
  if (onboarded && to.path === '/onboarding') {
    return '/'
  }
  return true
})

export default router
