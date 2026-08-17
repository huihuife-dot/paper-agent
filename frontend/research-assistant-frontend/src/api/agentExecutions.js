import apiClient from './client.js'

function unwrap(result) { return result.data ?? result }

export function startPaperAgent(paperId, request = '', client = apiClient) {
  return client.post(`/api/agent-executions/papers/${paperId}`, { request }).then(unwrap)
}

export function getPaperAgentStatus(paperId, client = apiClient) {
  return client.get(`/api/agent-executions/papers/${paperId}`).then(unwrap)
}

export function stopPaperAgent(paperId, client = apiClient) {
  return client.post(`/api/agent-executions/papers/${paperId}/stop`).then(unwrap)
}

export function startIdeaAgent(ideaId, workspacePath, request = '', client = apiClient) {
  return client.post(`/api/agent-executions/ideas/${ideaId}`, { workspacePath, request }).then(unwrap)
}

export function getIdeaAgentStatus(ideaId, client = apiClient) {
  return client.get(`/api/agent-executions/ideas/${ideaId}`).then(unwrap)
}

export function stopIdeaAgent(ideaId, client = apiClient) {
  return client.post(`/api/agent-executions/ideas/${ideaId}/stop`).then(unwrap)
}
