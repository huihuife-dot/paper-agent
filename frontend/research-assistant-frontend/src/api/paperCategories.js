import apiClient from './client.js'

export function listPaperCategories(client = apiClient) {
  return client.get('/api/paper-categories').then((result) => result.data ?? result)
}

export function createPaperCategory(payload, client = apiClient) {
  return client.post('/api/paper-categories', payload).then((result) => result.data ?? result)
}

export function updatePaperCategory(id, payload, client = apiClient) {
  return client.put(`/api/paper-categories/${id}`, payload).then((result) => result.data ?? result)
}

export function deletePaperCategory(id, client = apiClient) {
  return client.delete(`/api/paper-categories/${id}`).then((result) => result.data ?? result)
}
