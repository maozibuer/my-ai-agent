<template>
  <div class="chat-page">
    <!-- Main chat area -->
    <div class="chat-main">
      <!-- Top toolbar -->
      <div class="chat-toolbar">
        <div class="toolbar-left">
          <el-select
            v-model="selectedKbId"
            placeholder="选择知识库"
            clearable
            size="default"
            style="width: 200px"
          >
            <el-option label="通用知识库" value="" />
            <el-option
              v-for="kb in knowledgeBases"
              :key="kb.id"
              :label="kb.name"
              :value="kb.id"
            />
          </el-select>

          <el-tooltip content="开启后将使用流式输出（打字机效果）" placement="bottom">
            <div class="stream-toggle">
              <span class="toggle-label">流式输出</span>
              <el-switch v-model="streamMode" size="small" />
            </div>
          </el-tooltip>
        </div>

        <div class="toolbar-right">
          <el-button
            type="danger"
            plain
            size="small"
            :icon="Delete"
            @click="handleClearHistory"
          >
            清空对话
          </el-button>
        </div>
      </div>

      <!-- Message list -->
      <div class="message-list-wrapper">
        <el-scrollbar ref="scrollbarRef" class="message-scrollbar">
          <div class="message-list-inner">
            <!-- Empty state -->
            <div v-if="chatStore.messages.length === 0" class="empty-state">
              <el-icon :size="64" color="#c0c4cc"><ChatDotRound /></el-icon>
              <p class="empty-title">开始与智能助手对话</p>
              <p class="empty-desc">在下方输入框中输入您的问题</p>
            </div>

            <!-- Message items -->
            <div
              v-for="(msg, index) in chatStore.messages"
              :key="index"
              :class="['message-item', msg.role === 'user' ? 'message-user' : 'message-ai']"
            >
              <div class="message-avatar">
                <el-avatar :size="38" :style="avatarStyle(msg.role)">
                  {{ msg.role === 'user' ? '我' : 'AI' }}
                </el-avatar>
              </div>

              <div class="message-content">
                <!-- User message: plain text -->
                <div v-if="msg.role === 'user'" class="message-text">{{ msg.content }}</div>

                <!-- AI message: rendered markdown -->
                <div
                  v-else
                  class="message-text markdown-body"
                  :class="{ 'typewriter-cursor': chatStore.streaming && index === chatStore.messages.length - 1 }"
                  v-html="renderMarkdown(msg.content)"
                ></div>

                <!-- Tools used -->
                <div v-if="msg.toolsUsed && msg.toolsUsed.length" class="tools-container">
                  <div class="tools-title">使用工具:</div>
                  <el-tag
                    v-for="tool in msg.toolsUsed"
                    :key="tool"
                    size="small"
                    type="warning"
                    effect="plain"
                    style="margin-right: 6px; margin-bottom: 4px"
                  >
                    <el-icon style="margin-right: 2px"><Tools /></el-icon>
                    {{ tool }}
                  </el-tag>
                </div>

                <!-- Sources -->
                <div v-if="msg.sources && msg.sources.length" class="sources-container">
                  <div class="sources-title">参考来源:</div>
                  <el-tag
                    v-for="(source, idx) in msg.sources"
                    :key="idx"
                    size="small"
                    type="success"
                    effect="plain"
                    style="margin-right: 6px; margin-bottom: 4px"
                  >
                    <el-icon style="margin-right: 2px"><Document /></el-icon>
                    {{ typeof source === 'string' ? source : source.title || source.name || source.fileName || JSON.stringify(source) }}
                  </el-tag>
                </div>
              </div>
            </div>

            <!-- Loading indicator -->
            <div v-if="chatStore.loading && !chatStore.streaming && hasLastUserMessage" class="message-item message-ai">
              <div class="message-avatar">
                <el-avatar :size="38" style="background: #409eff">AI</el-avatar>
              </div>
              <div class="message-content">
                <div class="typing-indicator">
                  <span></span>
                  <span></span>
                  <span></span>
                </div>
              </div>
            </div>
          </div>
        </el-scrollbar>
      </div>

      <!-- Input area -->
      <div class="chat-input-area">
        <div class="input-wrapper">
          <el-input
            v-model="inputMessage"
            type="textarea"
            :rows="2"
            resize="none"
            placeholder="输入您的问题，按 Enter 发送，Shift+Enter 换行..."
            @keydown.enter.exact.prevent="handleSend"
          />
          <el-button
            type="primary"
            :icon="Promotion"
            :loading="chatStore.loading"
            :disabled="!inputMessage.trim()"
            @click="handleSend"
            class="send-btn"
          >
            发送
          </el-button>
        </div>
      </div>
    </div>

    <!-- Right sidebar: tools -->
    <div class="chat-sidebar">
      <div class="sidebar-header">
        <el-icon><Tools /></el-icon>
        <span>可用工具</span>
      </div>

      <el-scrollbar class="sidebar-scrollbar">
        <div class="sidebar-content">
          <div v-if="toolsLoading" class="sidebar-loading">
            <el-skeleton :rows="3" animated />
          </div>

          <div v-else-if="tools.length === 0" class="sidebar-empty">
            <el-icon :size="40" color="#dcdfe6"><Box /></el-icon>
            <p>暂无可用工具</p>
          </div>

          <div v-else class="tool-list">
            <div
              v-for="tool in tools"
              :key="tool.name || tool.id"
              class="tool-card"
            >
              <div class="tool-card-header">
                <el-icon color="#409eff"><Tools /></el-icon>
                <span class="tool-name">{{ tool.name || tool.toolName }}</span>
              </div>
              <p class="tool-desc">{{ tool.description || tool.desc || '无描述' }}</p>
            </div>
          </div>
        </div>
      </el-scrollbar>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ChatDotRound,
  Delete,
  Tools,
  Document,
  Promotion,
  Box
} from '@element-plus/icons-vue'
import { useChatStore } from '../../stores/chat'
import { getToolsAPI } from '../../api/tools'
import { listKnowledgeBasesAPI } from '../../api/knowledge'
import { renderMarkdown } from '../../utils/markdown'

const chatStore = useChatStore()

// Local state
const inputMessage = ref('')
const selectedKbId = ref('')
const streamMode = ref(true)
const scrollbarRef = ref(null)
const tools = ref([])
const toolsLoading = ref(false)
const knowledgeBases = ref([])

// Computed: check if last message is from user (for loading indicator)
const hasLastUserMessage = computed(() => {
  const last = chatStore.messages[chatStore.messages.length - 1]
  return last && last.role === 'user'
})

// Avatar style based on role
function avatarStyle(role) {
  if (role === 'user') {
    return { background: '#409eff', color: '#fff' }
  }
  return { background: 'linear-gradient(135deg, #52c41a, #389e0d)', color: '#fff' }
}

// Scroll to bottom of message list
function scrollToBottom() {
  nextTick(() => {
    const scrollbar = scrollbarRef.value
    if (scrollbar) {
      scrollbar.setScrollTop(scrollbar.wrapRef.scrollHeight)
    }
  })
}

// Watch messages change to auto-scroll
watch(
  () => chatStore.messages.length,
  () => scrollToBottom()
)

// Watch streaming content changes for live scroll
watch(
  () => chatStore.messages.map(m => m.content).join(''),
  () => {
    if (chatStore.streaming) {
      scrollToBottom()
    }
  }
)

// Handle send message
async function handleSend() {
  const message = inputMessage.value.trim()
  if (!message || chatStore.loading) return

  inputMessage.value = ''
  scrollToBottom()

  try {
    if (streamMode.value) {
      await chatStore.streamMessage(message, selectedKbId.value || undefined)
    } else {
      await chatStore.sendMessage(message, selectedKbId.value || undefined)
    }
    scrollToBottom()
  } catch (error) {
    // Error already handled in store
  }
}

// Handle clear history
function handleClearHistory() {
  if (chatStore.messages.length === 0) {
    ElMessage.info('当前没有对话记录')
    return
  }

  ElMessageBox.confirm('确定要清空所有对话记录吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await chatStore.clearHistory()
    ElMessage.success('对话已清空')
  }).catch(() => {})
}

// Load available tools on mount
async function loadTools() {
  toolsLoading.value = true
  try {
    const res = await getToolsAPI()
    tools.value = Array.isArray(res) ? res : (res?.data || res?.list || [])
  } catch (error) {
    console.error('Failed to load tools:', error)
  } finally {
    toolsLoading.value = false
  }
}

// Load knowledge bases for the selector
async function loadKnowledgeBases() {
  try {
    const res = await listKnowledgeBasesAPI()
    const list = res?.data || (Array.isArray(res) ? res : [])
    knowledgeBases.value = list
  } catch (error) {
    console.error('Failed to load knowledge bases:', error)
  }
}

onMounted(async () => {
  loadTools()
  loadKnowledgeBases()
  // Restore chat history if session exists
  if (chatStore.sessionId) {
    try {
      await chatStore.getHistory()
    } catch (error) {
      console.warn('Failed to load chat history:', error)
    }
  }
  scrollToBottom()
})
</script>

<style scoped>
.chat-page {
  display: flex;
  height: 100%;
  overflow: hidden;
}

/* ===== Main Chat Area ===== */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: #f5f7fa;
}

.chat-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 20px;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
  flex-shrink: 0;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 20px;
}

.stream-toggle {
  display: flex;
  align-items: center;
  gap: 8px;
}

.toggle-label {
  font-size: 13px;
  color: #606266;
}

/* ===== Message List ===== */
.message-list-wrapper {
  flex: 1;
  overflow: hidden;
  padding: 0;
}

.message-scrollbar {
  height: 100%;
}

.message-list-inner {
  padding: 20px 24px;
  min-height: 100%;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding-top: 120px;
}

.empty-title {
  font-size: 18px;
  color: #909399;
  margin-top: 16px;
}

.empty-desc {
  font-size: 14px;
  color: #c0c4cc;
  margin-top: 8px;
}

/* ===== Message Items (uses global styles from styles.css) ===== */
.message-item {
  display: flex;
  margin-bottom: 20px;
}

.message-user {
  flex-direction: row-reverse;
}

.message-user .message-content {
  background: linear-gradient(135deg, #409eff, #337ecc);
  color: #fff;
  border-radius: 12px 12px 4px 12px;
  padding: 12px 16px;
  max-width: 70%;
  margin-right: 12px;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.15);
}

.message-ai .message-content {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 12px 12px 12px 4px;
  padding: 12px 16px;
  max-width: 75%;
  margin-left: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.message-avatar {
  flex-shrink: 0;
}

.message-text {
  line-height: 1.7;
  word-wrap: break-word;
  white-space: pre-wrap;
}

/* ===== Typing Indicator ===== */
.typing-indicator {
  display: flex;
  gap: 4px;
  padding: 6px 0;
}

.typing-indicator span {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #c0c4cc;
  animation: typing 1.4s infinite;
}

.typing-indicator span:nth-child(2) {
  animation-delay: 0.2s;
}

.typing-indicator span:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes typing {
  0%, 60%, 100% { transform: translateY(0); opacity: 0.4; }
  30% { transform: translateY(-8px); opacity: 1; }
}

/* ===== Input Area ===== */
.chat-input-area {
  padding: 12px 20px 16px;
  background: #fff;
  border-top: 1px solid #ebeef5;
  flex-shrink: 0;
}

.input-wrapper {
  display: flex;
  gap: 12px;
  align-items: flex-end;
}

.input-wrapper .el-input {
  flex: 1;
}

.send-btn {
  height: 56px;
  font-size: 15px;
  padding: 0 24px;
}

/* ===== Right Sidebar ===== */
.chat-sidebar {
  width: 280px;
  background: #fff;
  border-left: 1px solid #ebeef5;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.sidebar-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px 20px;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  border-bottom: 1px solid #ebeef5;
}

.sidebar-scrollbar {
  flex: 1;
}

.sidebar-content {
  padding: 16px;
}

.sidebar-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 40px 0;
  color: #c0c4cc;
  font-size: 14px;
}

.tool-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.tool-card {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 12px;
  transition: all 0.3s;
}

.tool-card:hover {
  border-color: #409eff;
  box-shadow: 0 2px 12px rgba(64, 158, 255, 0.12);
}

.tool-card-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}

.tool-name {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.tool-desc {
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
}

/* ===== Sources & Tools in Messages ===== */
.tools-container,
.sources-container {
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px dashed #ebeef5;
}

.tools-title,
.sources-title {
  font-size: 12px;
  color: #909399;
  margin-bottom: 6px;
}
</style>
