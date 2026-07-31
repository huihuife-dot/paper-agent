import apiClient from './client.js'

function unwrapResult(result) {
  return Object.prototype.hasOwnProperty.call(result, 'data') ? result.data : result
}

function compactFilters(filters = {}) {
  return Object.fromEntries(
    Object.entries(filters).filter(([, value]) => value !== '' && value !== null && value !== undefined),
  )
}

export function listResearchIdeas(filters = {}, client = apiClient) {
  return client
    .get('/api/research-ideas', {
      params: compactFilters(filters),
    })
    .then(unwrapResult)
}

export function createResearchIdea(payload, client = apiClient) {
  return client.post('/api/research-ideas', payload).then(unwrapResult)
}

export function countResearchIdeaSaveTypes(client = apiClient) {
  return client.get('/api/research-ideas/stats/save-type').then(unwrapResult)
}

export function updateResearchIdeaSaveType(id, saveType, client = apiClient) {
  return client
    .patch(`/api/research-ideas/${id}/save-type`, { saveType })
    .then(unwrapResult)
}

export function updateResearchIdea(id, payload, client = apiClient) {
  return client.put(`/api/research-ideas/${id}`, payload).then(unwrapResult)
}

export function deleteResearchIdea(id, client = apiClient) {
  return client.delete(`/api/research-ideas/${id}`).then(unwrapResult)
}

export function saveDraftFromSession(sessionId, client = apiClient) {
  return client
    .post(`/api/research-ideas/save-draft-from-session/${sessionId}`)
    .then(unwrapResult)
}
