import { defineStore } from 'pinia'
import { loginAPI, getUserInfoAPI } from '../api/auth'
import { useChatStore } from './chat'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    userInfo: JSON.parse(localStorage.getItem('userInfo') || '{}')
  }),

  getters: {
    isLoggedIn: (state) => !!state.token,
    isAdmin: (state) => {
      const role = (state.userInfo.role || '').toUpperCase()
      return role === 'ADMIN'
    },
    username: (state) => state.userInfo.username || state.userInfo.userName || ''
  },

  actions: {
    // Login: wipe previous session first, then authenticate
    async login(credentials) {
      // Always reset chat state before logging in a new user so the
      // incoming user never sees the previous user's conversation.
      this._clearChatSession()

      const res = await loginAPI(credentials)
      if (res.code !== 200 || !res.data) {
        throw new Error(res.message || '登录失败，请检查账号密码')
      }
      const loginData = res.data
      this.token    = loginData.token
      this.userInfo = loginData.userInfo || {}
      localStorage.setItem('token',    loginData.token)
      localStorage.setItem('userInfo', JSON.stringify(loginData.userInfo || {}))
      return loginData
    },

    // Fetch current user info from backend
    async getUserInfo() {
      const res = await getUserInfoAPI()
      if (res.code !== 200 || !res.data) {
        throw new Error(res.message || '获取用户信息失败')
      }
      const userInfo = res.data
      this.userInfo = userInfo
      localStorage.setItem('userInfo', JSON.stringify(userInfo))
      return userInfo
    },

    // Logout: clear token, user info AND chat session
    logout() {
      this._clearChatSession()
      this.token    = ''
      this.userInfo = {}
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
    },

    // ── Internal ─────────────────────────────────────────────────────────────

    /**
     * Resets the chat store state and removes the persisted sessionId from
     * localStorage.  Called on both login and logout so a new user always
     * starts with an empty conversation.
     */
    _clearChatSession() {
      // Remove from localStorage first so initSession() won't reload it
      localStorage.removeItem('chatSessionId')

      // Reset Pinia chat store in-memory state.
      // Calling useChatStore() inside an action is safe in Pinia — the store
      // is only instantiated, not re-imported at module level (no cycle).
      try {
        const chatStore = useChatStore()
        chatStore.$patch(state => {
          state.messages  = []
          state.sessionId = ''
          state.loading   = false
          state.streaming = false
        })
      } catch (e) {
        // Chat store may not be mounted yet on very first page load
        console.warn('Could not reset chat store:', e)
      }
    }
  }
})
