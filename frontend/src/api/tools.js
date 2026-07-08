import request from './request'

// Get list of available tools from the backend
export function getToolsAPI() {
  return request.get('/tools')
}
