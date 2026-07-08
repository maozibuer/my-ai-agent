import request from './request'

// Login with username and password
export function loginAPI(data) {
  return request.post('/auth/login', data)
}

// Register a new user account
export function registerAPI(data) {
  return request.post('/auth/register', data)
}

// Get current authenticated user info
export function getUserInfoAPI() {
  return request.get('/auth/info')
}
