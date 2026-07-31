import axios from 'axios'

const apiClient = axios.create({
  baseURL: import.meta.env?.VITE_API_BASE_URL || 'http://localhost:8080',
  timeout: 30000,
})

apiClient.interceptors.response.use(
  (response) => {
    const result = response.data

    if (result && typeof result === 'object' && 'code' in result && result.code !== 200) {
      return Promise.reject(new Error(result.message || '请求失败'))
    }

    return result
  },
  (error) => {
    const message = error.response?.data?.message || error.message || '请求失败'
    return Promise.reject(new Error(message))
  },
)

export default apiClient
