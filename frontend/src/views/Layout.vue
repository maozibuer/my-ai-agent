<template>
  <el-container class="layout-container">
    <!-- Header -->
    <el-header class="layout-header">
      <div class="header-left">
        <div class="header-logo">
          <el-icon :size="28" color="#fff"><ChatDotRound /></el-icon>
          <span class="logo-text">智能问答Agent</span>
        </div>
      </div>

      <div class="header-right">
        <el-dropdown @command="handleCommand">
          <div class="user-info">
            <el-avatar :size="32" class="user-avatar">
              {{ displayName.charAt(0).toUpperCase() }}
            </el-avatar>
            <span class="user-name">{{ displayName }}</span>
            <el-icon><ArrowDown /></el-icon>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">
                <el-icon><UserFilled /></el-icon>
                个人信息
              </el-dropdown-item>
              <el-dropdown-item divided command="logout">
                <el-icon><SwitchButton /></el-icon>
                退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>

    <el-container class="layout-body">
      <!-- Sidebar Navigation -->
      <el-aside width="220px" class="layout-aside">
        <nav class="sidebar-nav">
          <router-link to="/chat" class="nav-item" :class="{ active: route.path === '/chat' }">
            <el-icon class="nav-icon"><ChatLineRound /></el-icon>
            <span class="nav-label">智能问答</span>
          </router-link>
          <router-link to="/knowledge" class="nav-item" :class="{ active: route.path === '/knowledge' }">
            <el-icon class="nav-icon"><Document /></el-icon>
            <span class="nav-label">知识库管理</span>
          </router-link>
          <router-link v-if="authStore.isAdmin" to="/users" class="nav-item" :class="{ active: route.path === '/users' }">
            <el-icon class="nav-icon"><UserFilled /></el-icon>
            <span class="nav-label">用户管理</span>
          </router-link>
        </nav>
      </el-aside>

      <!-- Main Content -->
      <el-main class="layout-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ChatDotRound,
  ArrowDown,
  UserFilled,
  SwitchButton,
  ChatLineRound,
  Document
} from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

// Display name from user info
const displayName = computed(() => {
  return authStore.userInfo?.username
    || authStore.userInfo?.userName
    || authStore.userInfo?.nickname
    || '用户'
})

// Handle dropdown menu commands
function handleCommand(command) {
  if (command === 'logout') {
    ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }).then(() => {
      authStore.logout()
      ElMessage.success('已退出登录')
      router.push('/login')
    }).catch(() => {})
  } else if (command === 'profile') {
    ElMessage.info('个人信息功能开发中')
  }
}
</script>

<style scoped>
.layout-container {
  height: 100vh;
}

.layout-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: linear-gradient(90deg, #409eff, #337ecc);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  padding: 0 24px;
  z-index: 10;
}

.header-left {
  display: flex;
  align-items: center;
}

.header-logo {
  display: flex;
  align-items: center;
  gap: 10px;
}

.logo-text {
  font-size: 18px;
  font-weight: 700;
  color: #fff;
  letter-spacing: 1px;
}

.header-right {
  display: flex;
  align-items: center;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: #fff;
  padding: 6px 12px;
  border-radius: 6px;
  transition: background 0.3s;
}

.user-info:hover {
  background: rgba(255, 255, 255, 0.15);
}

.user-avatar {
  background: rgba(255, 255, 255, 0.25);
  color: #fff;
  font-weight: 600;
}

.user-name {
  font-size: 14px;
}

.layout-body {
  height: calc(100vh - 60px);
}

.layout-aside {
  background: #fff;
  border-right: 1px solid #ebeef5;
  overflow: hidden;
}

.sidebar-nav {
  padding-top: 12px;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 4px 12px;
  padding: 0 16px;
  height: 48px;
  border-radius: 8px;
  color: #606266;
  text-decoration: none;
  font-size: 14px;
  transition: all 0.3s ease;
  cursor: pointer;
  user-select: none;
}

.nav-item:hover {
  background-color: #f0f2f5;
  color: #409eff;
}

.nav-item.active {
  background: linear-gradient(135deg, #409eff, #337ecc);
  color: #fff;
}

.nav-icon {
  font-size: 18px;
  flex-shrink: 0;
}

.nav-label {
  white-space: nowrap;
}

.layout-main {
  background: #f0f2f5;
  padding: 0;
  overflow: hidden;
}
</style>
