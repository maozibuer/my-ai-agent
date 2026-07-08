import axios from 'axios'
import { ElMessage } from 'element-plus'

// Create axios instance with base URL
const request = axios.create({
  baseURL: '/api',
  timeout: 30000
})

// Request interceptor: attach Authorization Bearer token
request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// Response interceptor: extract data and handle errors
request.interceptors.response.use(
  (response) => {
    const result = response.data
    // Check business-level Result.code; reject if not success
    if (result && typeof result.code === 'number' && result.code !== 200) {
      ElMessage.error(result.message || `请求失败 (${result.code})`)
      return Promise.reject(new Error(result.message || `请求失败 (${result.code})`))
    }
    // Return the Result wrapper (callers should access .data for payload)
    return result
  },
  (error) => {
    if (error.response) {
      const status = error.response.status
      switch (status) {
        case 401:
          // For login endpoint, 401 means bad credentials — handled by caller
          // For other endpoints, token expired — clear and redirect
          if (error.config.url.includes('/auth/login')) {
            ElMessage.error('用户名或密码错误')
          } else {
            localStorage.removeItem('token')
            localStorage.removeItem('userInfo')
            ElMessage.error('登录已过期，请重新登录')
            window.location.href = '/login'
          }
          break
        case 403:
          ElMessage.error('没有权限访问该资源')
          break
        case 404:
          ElMessage.error('请求的资源不存在')
          break
        case 500:
          ElMessage.error('服务器内部错误，请稍后重试')
          break
        default:
          ElMessage.error(error.response.data?.message || `请求失败 (${status})`)
      }
    } else if (error.code === 'ECONNABORTED') {
      ElMessage.error('请求超时，请稍后重试')
    } else if (error.message) {
      // Custom error thrown from business code check above
      // Message already shown, just reject
    } else {
      ElMessage.error('网络异常，请检查网络连接')
    }
    return Promise.reject(error)
  }
)

export default request
