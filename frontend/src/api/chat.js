import request from './request'

// Send a chat message and get a full response
export function chatAPI(data) {
  return request.post('/chat', data)
}

// Build SSE streaming URL for chat (used only as fallback)
export function chatStreamAPI(params) {
  const { message, sessionId, kbId } = params
  const token = localStorage.getItem('token')
  const queryParams = new URLSearchParams()

  queryParams.append('message', message)
  if (sessionId) queryParams.append('sessionId', sessionId)
  if (kbId) queryParams.append('kbId', kbId)
  if (token) queryParams.append('token', token)

  return `/api/chat/stream?${queryParams.toString()}`
}

// Get chat history for a specific session
export function getHistoryAPI(sessionId) {
  return request.get(`/chat/history/${sessionId}`)
}

// Clear conversation memory for a specific session
export function clearMemoryAPI(sessionId) {
  return request.delete(`/chat/memory/${sessionId}`)
}
