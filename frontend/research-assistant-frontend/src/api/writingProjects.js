import apiClient from './client.js'

const unwrap = (result) => result?.data ?? result

export function listWritingProjects(client = apiClient) {
  return client.get('/api/writing-projects').then(unwrap)
}

export function getWritingProject(id, client = apiClient) {
  return client.get(`/api/writing-projects/${id}`).then(unwrap)
}

export function createWritingProject(payload, client = apiClient) {
  return client.post('/api/writing-projects', payload).then(unwrap)
}

export function updateWritingProject(id, payload, client = apiClient) {
  return client.put(`/api/writing-projects/${id}`, payload).then(unwrap)
}

export function deleteWritingProject(id, client = apiClient) {
  return client.delete(`/api/writing-projects/${id}`).then(unwrap)
}

export function generateWritingOutline(id, instruction = '', client = apiClient) {
  return client.post(`/api/writing-projects/${id}/outline`, { instruction }, { timeout: 180000 }).then(unwrap)
}

export function generateWritingDraft(id, instruction = '', client = apiClient) {
  return client.post(`/api/writing-projects/${id}/draft`, { instruction }, { timeout: 300000 }).then(unwrap)
}

export function reviseWritingContent(id, payload, client = apiClient) {
  return client.post(`/api/writing-projects/${id}/revise`, payload, { timeout: 300000 }).then(unwrap)
}

export function listWritingRevisions(id, client = apiClient) {
  return client.get(`/api/writing-projects/${id}/revisions`).then(unwrap)
}

export function restoreWritingRevision(id, revisionId, client = apiClient) {
  return client.post(`/api/writing-projects/${id}/revisions/${revisionId}/restore`).then(unwrap)
}
