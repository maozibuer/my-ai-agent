import { defineStore } from 'pinia'
import { chatAPI, getHistoryAPI, clearMemoryAPI } from '../api/chat'

export const useChatStore = defineStore('chat', {
  state: () => ({
    messages: [],
    sessionId: localStorage.getItem('chatSessionId') || '',
    loading: false,
    streaming: false
  }),

  actions: {
    initSession() {
      if (!this.sessionId) {
        this.sessionId = crypto.randomUUID
          ? crypto.randomUUID()
          : 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
              const r = Math.random() * 16 | 0
              return (c === 'x' ? r : (r & 0x3 | 0x8)).toString(16)
            })
        localStorage.setItem('chatSessionId', this.sessionId)
      }
    },

    // Non-streaming: full response at once
    async sendMessage(message, kbId) {
      this.initSession()
      this.loading = true

      this.$patch(state => {
        state.messages.push({ role: 'user', content: message, sources: [], toolsUsed: [] })
      })

      try {
        const res  = await chatAPI({ message, sessionId: this.sessionId, knowledgeBaseId: kbId })
        const data = res.data || res

        if (data.sessionId) {
          this.sessionId = data.sessionId
          localStorage.setItem('chatSessionId', this.sessionId)
        }

        this.$patch(state => {
          state.messages.push({
            role: 'assistant',
            content: data.answer || '',
            sources: data.sources || [],
            toolsUsed: data.toolsUsed || []
          })
        })
      } catch (err) {
        this.$patch(state => {
          state.messages.push({ role: 'assistant', content: '抱歉，请求出错，请稍后重试。', sources: [], toolsUsed: [] })
        })
        throw err
      } finally {
        this.loading = false
      }
    },

    // Streaming: typewriter effect via EventSource
    streamMessage(message, kbId) {
      this.initSession()
      this.loading   = true
      this.streaming = true

      // Add user bubble
      this.$patch(state => {
        state.messages.push({ role: 'user', content: message, sources: [], toolsUsed: [] })
      })

      // Add empty AI bubble — we will append to it
      this.$patch(state => {
        state.messages.push({ role: 'assistant', content: '', sources: [], toolsUsed: [] })
      })
      const aiIndex = this.messages.length - 1

      // Build SSE URL (EventSource is GET + cannot set custom headers,
      // so token goes in the query string)
      const token = localStorage.getItem('token')
      const qs    = new URLSearchParams({ message, sessionId: this.sessionId })
      if (kbId)  qs.append('kbId', kbId)
      if (token) qs.append('token', token)
      const url = `/api/chat/stream?${qs.toString()}`

      return new Promise((resolve, reject) => {
        const es = new EventSource(url)

        es.onmessage = (event) => {
          try {
            const data = JSON.parse(event.data)

            if (data.content) {
              // $patch triggers Vue reactivity — direct index mutation does NOT
              this.$patch(state => {
                state.messages[aiIndex].content += data.content
              })
            }

            if (data.sessionId) {
              this.sessionId = data.sessionId
              localStorage.setItem('chatSessionId', this.sessionId)
            }

            if (data.done === true) {
              es.close()
              this.$patch(state => {
                state.loading   = false
                state.streaming = false
              })
              resolve()
            }
          } catch (e) {
            // Ignore unparseable frames (keep-alive comments, etc.)
          }
        }

        es.onerror = (err) => {
          es.close()
          this.$patch(state => {
            state.loading   = false
            state.streaming = false
            if (!state.messages[aiIndex].content) {
              state.messages[aiIndex].content = '抱歉，连接中断，请稍后重试。'
            }
          })
          reject(err)
        }
      })
    },

    async getHistory() {
      if (!this.sessionId) { this.initSession(); return }
      try {
        const res     = await getHistoryAPI(this.sessionId)
        const history = res.data || res
        if (Array.isArray(history)) {
          this.$patch(state => { state.messages = history })
        }
      } catch (e) {
        console.error('Failed to load chat history:', e)
      }
    },

    async clearHistory() {
      if (this.sessionId) {
        try { await clearMemoryAPI(this.sessionId) } catch (e) { /* ignore */ }
      }
      this.$patch(state => {
        state.messages  = []
        state.sessionId = ''
      })
      localStorage.removeItem('chatSessionId')
    }
  }
})
