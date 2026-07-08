<template>
  <div class="knowledge-page">
    <div class="page-header">
      <h2 class="page-title">知识库管理</h2>
      <p class="page-desc">管理知识库、上传文档并进行语义检索</p>
    </div>

    <!-- ===== 知识库列表 ===== -->
    <el-card class="section-card" shadow="never">
      <template #header>
        <div class="card-header">
          <el-icon><Folder /></el-icon>
          <span>知识库列表</span>
          <div class="header-actions">
            <el-button type="primary" size="small" :icon="Plus" @click="openKbDialog()">新建知识库</el-button>
            <el-button size="small" :icon="Refresh" @click="loadKnowledgeBases">刷新</el-button>
          </div>
        </div>
      </template>

      <el-table :data="knowledgeBases" v-loading="kbLoading" style="width:100%" row-key="id">
        <el-table-column prop="id" label="ID" width="80" align="center" />
        <el-table-column prop="name" label="知识库名称" min-width="160" />
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.description || '-' }}</template>
        </el-table-column>
        <el-table-column prop="department" label="部门" width="120">
          <template #default="{ row }">{{ row.department || '-' }}</template>
        </el-table-column>
        <el-table-column prop="documentCount" label="文档数" width="90" align="center">
          <template #default="{ row }">{{ row.documentCount ?? 0 }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" align="center">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="openKbDialog(row)">编辑</el-button>
            <el-button size="small" type="danger" link @click="handleDeleteKb(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 知识库新增/编辑对话框 -->
    <el-dialog v-model="kbDialogVisible" :title="kbForm.id ? '编辑知识库' : '新建知识库'" width="480px" @close="resetKbForm">
      <el-form ref="kbFormRef" :model="kbForm" :rules="kbRules" label-width="80px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="kbForm.name" placeholder="请输入知识库名称" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="kbForm.description" type="textarea" :rows="2" placeholder="可选描述" />
        </el-form-item>
        <el-form-item label="部门" prop="department">
          <el-input v-model="kbForm.department" placeholder="可选所属部门" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="kbDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="kbSaving" @click="saveKb">保存</el-button>
      </template>
    </el-dialog>

    <!-- ===== 文档上传 ===== -->
    <el-card class="section-card" shadow="never">
      <template #header>
        <div class="card-header">
          <el-icon><Upload /></el-icon>
          <span>文档上传</span>
        </div>
      </template>

      <!-- 必须先选择目标知识库 -->
      <div class="upload-kb-select">
        <span class="upload-kb-label">目标知识库：</span>
        <el-select v-model="uploadKbId" placeholder="请选择知识库（必选）" style="width:260px" clearable>
          <el-option v-for="kb in knowledgeBases" :key="kb.id" :label="kb.name" :value="kb.id" />
        </el-select>
        <span class="upload-kb-tip">上传前请先选择要添加到的知识库</span>
      </div>

      <el-upload
        ref="uploadRef"
        class="upload-area"
        drag
        multiple
        :auto-upload="true"
        :http-request="handleUpload"
        :before-upload="beforeUpload"
        :file-list="fileList"
        accept=".txt,.md,.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.csv"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">拖拽文件到此处，或 <em>点击上传</em></div>
        <template #tip>
          <div class="upload-tip">支持 TXT、MD、PDF、Word、Excel、PPT、CSV，单文件不超过 50MB</div>
        </template>
      </el-upload>
    </el-card>

    <!-- ===== 文档列表 ===== -->
    <el-card class="section-card" shadow="never">
      <template #header>
        <div class="card-header">
          <el-icon><Document /></el-icon>
          <span>文档列表</span>
          <div class="header-actions">
            <el-select v-model="filterKbId" placeholder="筛选知识库" clearable size="small"
              style="width:180px" @change="() => { currentPage = 1; loadDocuments() }">
              <el-option label="全部" value="" />
              <el-option label="通用知识库" :value="0" />
              <el-option v-for="kb in knowledgeBases" :key="kb.id" :label="kb.name" :value="kb.id" />
            </el-select>
            <el-button size="small" :icon="Refresh" @click="loadDocuments">刷新</el-button>
          </div>
        </div>
      </template>

      <el-table :data="documents" v-loading="tableLoading" style="width:100%" row-key="id">
        <el-table-column prop="fileName" label="文件名" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <el-icon class="file-icon"><Document /></el-icon>
            {{ row.fileName }}
          </template>
        </el-table-column>

        <el-table-column label="所属知识库" width="150" align="center">
          <template #default="{ row }">
            {{ getKbName(row.knowledgeBaseId) }}
          </template>
        </el-table-column>

        <el-table-column prop="fileType" label="类型" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ row.fileType?.toUpperCase() || '-' }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="getStatusType(row.status)">{{ getStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="fileSize" label="大小" width="100" align="center">
          <template #default="{ row }">{{ formatFileSize(row.fileSize) }}</template>
        </el-table-column>

        <el-table-column prop="createTime" label="上传时间" width="170" align="center">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>

        <el-table-column label="操作" width="90" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="danger" size="small" :icon="Delete" link @click="handleDeleteDoc(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="loadDocuments"
          @current-change="loadDocuments"
        />
      </div>
    </el-card>

    <!-- ===== 语义检索 ===== -->
    <el-card class="section-card" shadow="never">
      <template #header>
        <div class="card-header">
          <el-icon><Search /></el-icon>
          <span>语义检索</span>
        </div>
      </template>

      <div class="search-box">
        <el-input v-model="searchQuery" placeholder="输入检索关键词..." size="large" clearable @keyup.enter="handleSearch">
          <template #append>
            <el-button :icon="Search" :loading="searchLoading" @click="handleSearch">检索</el-button>
          </template>
        </el-input>
        <div class="search-options">
          <span class="option-label">知识库：</span>
          <el-select v-model="searchKbId" placeholder="全部知识库" clearable size="small" style="width:180px">
            <el-option label="全部" value="" />
            <el-option v-for="kb in knowledgeBases" :key="kb.id" :label="kb.name" :value="String(kb.id)" />
          </el-select>
          <span class="option-label" style="margin-left:12px">返回数量：</span>
          <el-input-number v-model="topK" :min="1" :max="20" size="small" style="width:100px" />
        </div>
      </div>

      <div class="search-results">
        <div v-if="searchLoading" class="results-loading"><el-skeleton :rows="5" animated /></div>
        <div v-else-if="searchResults.length === 0 && hasSearched" class="results-empty">
          <el-icon :size="48" color="#dcdfe6"><Search /></el-icon><p>未找到相关结果</p>
        </div>
        <div v-else-if="!hasSearched" class="results-placeholder">
          <el-icon :size="48" color="#dcdfe6"><Search /></el-icon><p>输入关键词后点击检索</p>
        </div>
        <div v-else>
          <div class="results-count">共找到 {{ searchResults.length }} 条结果</div>
          <div v-for="(result, index) in searchResults" :key="index" class="result-item">
            <div class="result-header">
              <el-tag size="small" type="success">TOP {{ index + 1 }}</el-tag>
              <span class="result-score">相似度: {{ formatScore(result.score) }}</span>
            </div>
            <div v-if="result.fileName" class="result-source">
              <el-icon><Document /></el-icon><span>{{ result.fileName }}</span>
            </div>
            <div class="result-content">{{ result.content }}</div>
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload, UploadFilled, Document, Delete, Refresh, Search, Plus, Folder } from '@element-plus/icons-vue'
import {
  listKnowledgeBasesAPI,
  createKnowledgeBaseAPI,
  updateKnowledgeBaseAPI,
  deleteKnowledgeBaseAPI,
  uploadDocumentAPI,
  listDocumentsAPI,
  deleteDocumentAPI,
  searchAPI
} from '../../api/knowledge'

// ─── Knowledge Bases ──────────────────────────────────────────────────────────
const knowledgeBases = ref([])
const kbLoading = ref(false)
const kbDialogVisible = ref(false)
const kbSaving = ref(false)
const kbFormRef = ref(null)
const kbForm = reactive({ id: null, name: '', description: '', department: '' })
const kbRules = {
  name: [{ required: true, message: '请输入知识库名称', trigger: 'blur' }]
}

async function loadKnowledgeBases() {
  kbLoading.value = true
  try {
    const res = await listKnowledgeBasesAPI()
    // res = Result wrapper; interceptor returns Result object; .data is the array
    knowledgeBases.value = res?.data ?? (Array.isArray(res) ? res : [])
  } catch (e) {
    console.error('Failed to load knowledge bases:', e)
  } finally {
    kbLoading.value = false
  }
}

function openKbDialog(row) {
  resetKbForm()
  if (row) {
    kbForm.id = row.id
    kbForm.name = row.name
    kbForm.description = row.description || ''
    kbForm.department = row.department || ''
  }
  kbDialogVisible.value = true
}

function resetKbForm() {
  kbForm.id = null
  kbForm.name = ''
  kbForm.description = ''
  kbForm.department = ''
  kbFormRef.value?.clearValidate()
}

async function saveKb() {
  const valid = await kbFormRef.value?.validate().catch(() => false)
  if (!valid) return
  kbSaving.value = true
  try {
    if (kbForm.id) {
      await updateKnowledgeBaseAPI(kbForm.id, {
        name: kbForm.name,
        description: kbForm.description,
        department: kbForm.department
      })
      ElMessage.success('知识库已更新')
    } else {
      await createKnowledgeBaseAPI({
        name: kbForm.name,
        description: kbForm.description,
        department: kbForm.department
      })
      ElMessage.success('知识库已创建')
    }
    kbDialogVisible.value = false
    loadKnowledgeBases()
  } catch (e) {
    // interceptor handles error message
  } finally {
    kbSaving.value = false
  }
}

async function handleDeleteKb(row) {
  await ElMessageBox.confirm(`确定删除知识库「${row.name}」吗？`, '删除确认', {
    confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning'
  }).catch(() => { throw new Error('cancelled') })
  try {
    await deleteKnowledgeBaseAPI(row.id)
    ElMessage.success('知识库已删除')
    loadKnowledgeBases()
  } catch (e) {
    if (e.message !== 'cancelled') { /* interceptor shows error */ }
  }
}

// ─── Helper: get KB name by id ────────────────────────────────────────────────
function getKbName(kbId) {
  if (kbId == null || kbId === 0 || kbId === '0') return '通用知识库'
  // id may arrive as string (JSON serialised Long) or number
  const found = knowledgeBases.value.find(k => String(k.id) === String(kbId))
  return found ? found.name : `KB-${kbId}`
}

// ─── Upload ───────────────────────────────────────────────────────────────────
const uploadRef = ref(null)
const fileList = ref([])
const uploadKbId = ref(null)   // the KB chosen before uploading

function beforeUpload(file) {
  if (file.size > 50 * 1024 * 1024) {
    ElMessage.error('文件大小不能超过 50MB')
    return false
  }
  // uploadKbId must be a valid knowledge base ID (cannot be null, undefined, or 0)
  if (!uploadKbId.value) {
    ElMessage.warning('请先选择要上传到的知识库')
    return false
  }
  return true
}

async function handleUpload(options) {
  const file = options.file
  if (!file) return
  // Pass the selected kbId directly
  const kbId = uploadKbId.value
  try {
    await uploadDocumentAPI(file, kbId)
    options.onSuccess?.()
    ElMessage.success(`"${file.name}" 上传成功，正在处理…`)
    // Auto-filter document list to show the KB the user just uploaded to
    if (kbId) {
      filterKbId.value = String(kbId)
    }
    loadDocuments()
    loadKnowledgeBases() // refresh document counts
  } catch (e) {
    options.onError?.(e)
    ElMessage.error(`"${file.name}" 上传失败`)
  }
}

// ─── Document list ────────────────────────────────────────────────────────────
const documents = ref([])
const tableLoading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const filterKbId = ref('')

loadDocuments._lastCallId = 0

async function loadDocuments() {
  tableLoading.value = true
  const callId = Date.now()
  loadDocuments._lastCallId = callId
  try {
    const params = { page: currentPage.value, size: pageSize.value }
    // filterKbId '' = all;  0 = general KB;  positive = specific KB
    if (filterKbId.value !== '') params.kbId = filterKbId.value
    const res = await listDocumentsAPI(params)
    if (loadDocuments._lastCallId !== callId) return
    const pr = res?.data ?? res
    documents.value = Array.isArray(pr?.records) ? pr.records : Array.isArray(pr) ? pr : []
    total.value = pr?.total ?? documents.value.length
  } catch (e) {
    if (loadDocuments._lastCallId !== callId) return
    ElMessage.error('加载文档列表失败')
  } finally {
    if (loadDocuments._lastCallId === callId) tableLoading.value = false
  }
}

async function handleDeleteDoc(row) {
  // row.id is a String (serialised Long) — send as-is; the backend path var is Long
  const docId = row.id
  const docName = row.fileName || String(docId)
  await ElMessageBox.confirm(`确定删除文档「${docName}」吗？`, '删除确认', {
    confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning'
  }).catch(() => { throw new Error('cancelled') })
  try {
    await deleteDocumentAPI(docId)
    ElMessage.success('文档已删除')
    loadDocuments()
  } catch (e) {
    if (e.message !== 'cancelled') { /* interceptor shows error */ }
  }
}

// ─── Search ───────────────────────────────────────────────────────────────────
const searchQuery = ref('')
const searchKbId = ref('')
const topK = ref(5)
const searchResults = ref([])
const searchLoading = ref(false)
const hasSearched = ref(false)

async function handleSearch() {
  const query = searchQuery.value.trim()
  if (!query) { ElMessage.warning('请输入检索关键词'); return }
  searchLoading.value = true
  hasSearched.value = true
  try {
    const res = await searchAPI({ query, topK: topK.value, knowledgeBaseId: searchKbId.value || undefined })
    const list = res?.data ?? (Array.isArray(res) ? res : [])
    searchResults.value = Array.isArray(list) ? list : (list?.results || list?.list || [])
  } catch (e) {
    searchResults.value = []
  } finally {
    searchLoading.value = false
  }
}

// ─── Utils ────────────────────────────────────────────────────────────────────
function getStatusType(s) {
  const v = (s || '').toLowerCase()
  if (v === 'processed' || v === 'completed') return 'success'
  if (v === 'failed' || v === 'error') return 'danger'
  if (v === 'pending' || v === 'processing') return 'warning'
  return 'info'
}

function getStatusLabel(s) {
  const v = (s || '').toLowerCase()
  if (v === 'processed' || v === 'completed') return '已完成'
  if (v === 'failed' || v === 'error') return '失败'
  if (v === 'pending') return '待处理'
  if (v === 'processing') return '处理中'
  return s || '未知'
}

function formatFileSize(bytes) {
  if (!bytes && bytes !== 0) return '-'
  const units = ['B', 'KB', 'MB', 'GB']
  let size = bytes, i = 0
  while (size >= 1024 && i < units.length - 1) { size /= 1024; i++ }
  return `${size.toFixed(1)} ${units[i]}`
}

function formatTime(t) {
  if (!t) return '-'
  try {
    const d = new Date(t)
    return isNaN(d) ? t : d.toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
  } catch { return t }
}

function formatScore(score) {
  if (score == null) return '-'
  return score <= 1 ? `${(score * 100).toFixed(1)}%` : score.toFixed(3)
}

onMounted(() => {
  loadKnowledgeBases()
  loadDocuments()
})
</script>

<style scoped>
.knowledge-page { height: 100%; overflow-y: auto; padding: 20px; }
.page-header { margin-bottom: 20px; }
.page-title { font-size: 22px; font-weight: 700; color: #303133; margin-bottom: 6px; }
.page-desc  { font-size: 14px; color: #909399; }
.section-card { margin-bottom: 20px; border-radius: 8px; }
.section-card :deep(.el-card__header) { padding: 14px 20px; border-bottom: 1px solid #ebeef5; }
.card-header {
  display: flex; align-items: center; gap: 8px;
  font-size: 16px; font-weight: 600; color: #303133;
}
.header-actions { margin-left: auto; display: flex; gap: 8px; align-items: center; }

/* Upload KB selector */
.upload-kb-select {
  display: flex; align-items: center; gap: 10px;
  margin-bottom: 16px; padding: 12px 16px;
  background: #f0f9ff; border-radius: 8px; border: 1px solid #bae6fd;
}
.upload-kb-label { font-size: 14px; color: #303133; font-weight: 500; white-space: nowrap; }
.upload-kb-tip   { font-size: 12px; color: #909399; }

.upload-area { width: 100%; }
.upload-area :deep(.el-upload-dragger) { width: 100%; padding: 30px 20px; }
.upload-tip  { font-size: 12px; color: #909399; text-align: center; margin-top: 8px; }
.file-icon   { margin-right: 6px; color: #409eff; vertical-align: middle; }
.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 16px; }

/* Search */
.search-box { display: flex; flex-direction: column; gap: 12px; margin-bottom: 20px; }
.search-options { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.option-label { font-size: 13px; color: #606266; }
.search-results { min-height: 200px; }
.results-loading, .results-empty, .results-placeholder {
  display: flex; flex-direction: column; align-items: center;
  justify-content: center; padding: 40px 0; color: #c0c4cc; gap: 8px;
}
.results-count { font-size: 14px; color: #909399; margin-bottom: 12px; }
.result-item {
  border: 1px solid #ebeef5; border-radius: 8px;
  padding: 14px 16px; margin-bottom: 12px; transition: all 0.3s;
}
.result-item:hover { border-color: #409eff; box-shadow: 0 2px 12px rgba(64,158,255,.1); }
.result-header { display: flex; align-items: center; gap: 12px; margin-bottom: 8px; }
.result-score  { font-size: 12px; color: #909399; }
.result-source { display: flex; align-items: center; gap: 4px; font-size: 13px; color: #606266; margin-bottom: 6px; }
.result-content {
  font-size: 14px; color: #303133; line-height: 1.6;
  background: #f5f7fa; border-radius: 6px; padding: 10px 12px;
}
</style>
