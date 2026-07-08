<template>
  <div class="user-page">
    <!-- Page header -->
    <div class="page-header">
      <h2 class="page-title">用户管理</h2>
      <p class="page-desc">查看和管理系统用户账号</p>
    </div>

    <!-- User table card -->
    <el-card class="section-card" shadow="never">
      <template #header>
        <div class="card-header">
          <el-icon><UserFilled /></el-icon>
          <span>用户列表</span>
          <div class="header-actions">
            <el-input
              v-model="searchKeyword"
              placeholder="搜索用户名..."
              size="small"
              clearable
              style="width: 200px"
              :prefix-icon="Search"
            />
            <el-button
              type="primary"
              size="small"
              :icon="Plus"
              @click="handleAdd"
            >
              添加用户
            </el-button>
            <el-button
              size="small"
              :icon="Refresh"
              @click="loadUsers"
            >
              刷新
            </el-button>
          </div>
        </div>
      </template>

      <el-table
        :data="filteredUsers"
        v-loading="tableLoading"
        style="width: 100%"
        row-key="id"
      >
        <el-table-column prop="id" label="ID" width="80" align="center" />

        <el-table-column prop="username" label="用户名" min-width="150">
          <template #default="{ row }">
            <div class="user-cell">
              <el-avatar :size="28" class="user-cell-avatar">
                {{ (row.username || row.userName || '?').charAt(0).toUpperCase() }}
              </el-avatar>
              <span>{{ row.username || row.userName }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="email" label="邮箱" min-width="200" show-overflow-tooltip />

        <el-table-column prop="role" label="角色" width="120" align="center">
          <template #default="{ row }">
            <el-tag
              size="small"
              :type="getRoleType(row.role)"
              effect="dark"
            >
              {{ getRoleLabel(row.role) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.enabled === false || row.status === 0 ? 'danger' : 'success'">
              {{ row.enabled === false || row.status === 0 ? '禁用' : '正常' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="createTime" label="注册时间" width="180" align="center">
          <template #default="{ row }">
            {{ formatTime(row.createTime || row.createdAt || row.registerTime) }}
          </template>
        </el-table-column>

        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              size="small"
              type="primary"
              link
              @click="handleEdit(row)"
            >
              编辑
            </el-button>
            <el-button
              size="small"
              type="danger"
              link
              @click="handleDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- Pagination -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          background
        />
      </div>
    </el-card>

    <!-- Edit dialog -->
    <el-dialog
      v-model="editDialogVisible"
      title="编辑用户"
      width="480px"
    >
      <el-form
        ref="editFormRef"
        :model="editForm"
        label-width="80px"
      >
        <el-form-item label="用户名">
          <el-input v-model="editForm.username" disabled />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="editForm.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="editForm.role" placeholder="请选择角色" style="width: 100%">
            <el-option label="管理员" value="ADMIN" />
            <el-option label="普通用户" value="USER" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch
            v-model="editForm.enabled"
            active-text="正常"
            inactive-text="禁用"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- Add user dialog -->
    <el-dialog
      v-model="addDialogVisible"
      title="添加用户"
      width="480px"
      @close="resetAddForm"
    >
      <el-form
        ref="addFormRef"
        :model="addForm"
        :rules="addRules"
        label-width="80px"
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="addForm.username" placeholder="3-50 个字符" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="addForm.password" type="password" placeholder="至少 6 个字符" show-password />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="addForm.email" placeholder="请输入邮箱" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="addLoading" @click="saveAdd">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UserFilled, Search, Refresh, Plus } from '@element-plus/icons-vue'
import request from '../../api/request'

// Table state
const users = ref([])
const tableLoading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const searchKeyword = ref('')

// Edit dialog state
const editDialogVisible = ref(false)
const editFormRef = ref(null)
const editForm = reactive({
  id: '',
  username: '',
  email: '',
  role: '',
  enabled: true
})

// Add user dialog state
const addDialogVisible = ref(false)
const addFormRef = ref(null)
const addLoading = ref(false)
const addForm = reactive({ username: '', password: '', email: '' })
const addRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 50, message: '用户名长度为 3-50 个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 个字符', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入有效的邮箱地址', trigger: 'blur' }
  ]
}

// Filtered users based on search keyword
const filteredUsers = computed(() => {
  if (!searchKeyword.value) return users.value
  const keyword = searchKeyword.value.toLowerCase()
  return users.value.filter(user => {
    const name = (user.username || user.userName || '').toLowerCase()
    return name.includes(keyword)
  })
})

// Load users from backend
async function loadUsers() {
  tableLoading.value = true
  try {
    const res = await request.get('/admin/users', {
      params: {
        page: currentPage.value,
        size: pageSize.value
      }
    })
    // Handle various response formats
    users.value = res?.content || res?.list || res?.records || res?.data || (Array.isArray(res) ? res : [])
    total.value = res?.totalElements || res?.total || users.value.length
  } catch (error) {
    // If admin users endpoint is not available, show current user as placeholder
    console.error('Failed to load users:', error)
    users.value = []
  } finally {
    tableLoading.value = false
  }
}

// Open add user dialog
function handleAdd() {
  resetAddForm()
  addDialogVisible.value = true
}

// Reset add form fields
function resetAddForm() {
  addForm.username = ''
  addForm.password = ''
  addForm.email    = ''
  addFormRef.value?.clearValidate()
}

// Submit new user
async function saveAdd() {
  const valid = await addFormRef.value?.validate().catch(() => false)
  if (!valid) return

  addLoading.value = true
  try {
    await request.post('/admin/users', {
      username: addForm.username,
      password: addForm.password,
      email:    addForm.email
    })
    ElMessage.success(`用户 "${addForm.username}" 创建成功`)
    addDialogVisible.value = false
    loadUsers()
  } catch (error) {
    // Error message shown by axios interceptor
  } finally {
    addLoading.value = false
  }
}

// Handle edit action
function handleEdit(row) {
  editForm.id = row.id
  editForm.username = row.username || row.userName || ''
  editForm.email = row.email || ''
  editForm.role = (row.role || 'USER').toUpperCase()
  editForm.enabled = row.enabled !== false && row.status !== 0
  editDialogVisible.value = true
}

// Save edit changes
async function saveEdit() {
  try {
    await request.put(`/admin/users/${editForm.id}`, {
      email: editForm.email,
      role: editForm.role,
      enabled: editForm.enabled
    })
    ElMessage.success('用户信息已更新')
    editDialogVisible.value = false
    loadUsers()
  } catch (error) {
    // Error handled by interceptor
  }
}

// Handle delete action
function handleDelete(row) {
  ElMessageBox.confirm(
    `确定要删除用户 "${row.username || row.userName}" 吗？此操作不可恢复。`,
    '删除确认',
    {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(async () => {
    try {
      await request.delete(`/admin/users/${row.id}`)
      ElMessage.success('用户已删除')
      loadUsers()
    } catch (error) {
      // Error handled by interceptor
    }
  }).catch(() => {})
}

// ===== Utility Functions =====

function getRoleType(role) {
  const r = (role || '').toUpperCase()
  if (r === 'ADMIN') return 'danger'
  if (r === 'USER') return 'primary'
  return 'info'
}

function getRoleLabel(role) {
  const r = (role || '').toUpperCase()
  if (r === 'ADMIN') return '管理员'
  if (r === 'USER') return '普通用户'
  return role || '未知'
}

function formatTime(time) {
  if (!time) return '-'
  try {
    const date = new Date(time)
    if (isNaN(date.getTime())) return time
    return date.toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    })
  } catch {
    return time
  }
}

// Load users on mount
onMounted(() => {
  loadUsers()
})
</script>

<style scoped>
.user-page {
  height: 100%;
  overflow-y: auto;
  padding: 20px;
}

.page-header {
  margin-bottom: 20px;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  color: #303133;
  margin-bottom: 6px;
}

.page-desc {
  font-size: 14px;
  color: #909399;
}

.section-card {
  border-radius: 8px;
}

.section-card :deep(.el-card__header) {
  padding: 14px 20px;
  border-bottom: 1px solid #ebeef5;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.header-actions {
  margin-left: auto;
  display: flex;
  gap: 8px;
  align-items: center;
}

.user-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-cell-avatar {
  background: #409eff;
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
