import apiClient from './client.js'

function unwrap(result) { return result.data ?? result }

export function createAgentTaskPackage(mode, sourceId, client = apiClient) {
  const plural = mode === 'paper' ? 'papers' : 'ideas'
  return client.post(`/api/agent-packages/${plural}/${sourceId}`).then(unwrap)
}

export function packageDownloadUrl(result) {
  const base = import.meta.env?.VITE_API_BASE_URL || 'http://localhost:8080'
  return `${base.replace(/\/$/, '')}${result.downloadUrl}`
}

export function prepareExternalGitProject(mode, sourceId, workspacePath = '', client = apiClient) {
  return client.post(`/api/agent-git-projects/${mode}/${sourceId}/prepare-external`, { workspacePath }).then(unwrap)
}

export function publishGitProjectToGitee(mode, sourceId, repositoryName, description = '', client = apiClient) {
  return client.post(`/api/agent-git-projects/${mode}/${sourceId}/gitee`, { repositoryName, description }).then(unwrap)
}

export function refreshGitProject(mode, sourceId, client = apiClient) {
  return client.post(`/api/agent-git-projects/${mode}/${sourceId}/refresh`).then(unwrap)
}

export function retryGitProjectPush(mode, sourceId, client = apiClient) {
  return client.post(`/api/agent-git-projects/${mode}/${sourceId}/push`).then(unwrap)
}

export function listAgentGitProjects(client = apiClient) {
  return client.get('/api/agent-git-projects').then(unwrap)
}

export function deliveryDownloadUrl(project) {
  const base = import.meta.env?.VITE_API_BASE_URL || 'http://localhost:8080'
  return `${base.replace(/\/$/, '')}${project.deliveryDownloadUrl}`
}
