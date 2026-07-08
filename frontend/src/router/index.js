import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/LoginView.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/',
    component: () => import('../views/Layout.vue'),
    meta: { requiresAuth: true },
    redirect: '/chat',
    children: [
      {
        path: 'chat',
        name: 'Chat',
        component: () => import('../views/chat/ChatView.vue'),
        meta: { title: '智能问答' }
      },
      {
        path: 'knowledge',
        name: 'Knowledge',
        component: () => import('../views/knowledge/KnowledgeView.vue'),
        meta: { title: '知识库管理' }
      },
      {
        path: 'users',
        name: 'Users',
        component: () => import('../views/user/UserView.vue'),
        meta: { title: '用户管理', requiresAdmin: true }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// Navigation guard: check authentication before each route
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')

  // If route requires auth and no token, redirect to login
  if (to.meta.requiresAuth !== false && !token) {
    next('/login')
    return
  }

  // If already logged in and trying to access login, redirect to home
  if (to.path === '/login' && token) {
    next('/')
    return
  }

  // Check admin permission for admin-only routes
  if (to.meta.requiresAdmin) {
    const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}')
    const role = (userInfo.role || '').toUpperCase()
    if (role !== 'ADMIN') {
      next('/chat')
      return
    }
  }

  next()
})

export default router
