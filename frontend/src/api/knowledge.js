import request from './request'

// ─── Knowledge Bases (kb_base) ───────────────────────────────────────────────

export function listKnowledgeBasesAPI() {
  return request.get('/knowledge/bases')
}

export function createKnowledgeBaseAPI(data) {
  return request.post('/knowledge/bases', data)
}

export function updateKnowledgeBaseAPI(id, data) {
  return request.put(`/knowledge/bases/${id}`, data)
}

export function deleteKnowledgeBaseAPI(id) {
  return request.delete(`/knowledge/bases/${id}`)
}

// ─── Documents ───────────────────────────────────────────────────────────────

export function uploadDocumentAPI(file, kbId) {
  const formData = new FormData()
  formData.append('file', file)
  if (kbId) formData.append('kbId', kbId)
  return request.post('/knowledge/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function listDocumentsAPI(params) {
  return request.get('/knowledge/documents', { params })
}

export function deleteDocumentAPI(docId) {
  return request.delete(`/knowledge/${docId}`)
}

export function searchAPI(data) {
  return request.post('/knowledge/search', data)
}
