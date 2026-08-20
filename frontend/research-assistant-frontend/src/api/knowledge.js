import apiClient from './client.js'

export function listKnowledgeCatalogs(client = apiClient) {
  return client.get('/api/knowledge/catalogs').then((result) => result.data ?? result)
}

export function listKnowledgeTopics(options = {}, client = apiClient) {
  const query = new URLSearchParams()
  if (options.knowledgeType) query.set('knowledgeType', options.knowledgeType)
  query.set('limit', String(options.limit || 60))
  return client.get(`/api/knowledge/topics?${query}`).then((result) => result.data ?? result)
}

export function buildPaperKnowledge(paperId, options = {}, client = apiClient) {
  const async = options.async !== false
  return client.post(`/api/papers/${encodeURIComponent(paperId)}/knowledge/build?async=${async}`, undefined, {
    timeout: async ? 30000 : 600000,
  }).then((result) => result.data ?? result)
}

export function getPaperKnowledgeStatus(paperId, client = apiClient) {
  return client.get(`/api/papers/${encodeURIComponent(paperId)}/knowledge/status`)
    .then((result) => result.data ?? result)
}

export function listPaperKnowledge(paperId, options = {}, client = apiClient) {
  const query = new URLSearchParams()
  if (options.knowledgeType) query.set('knowledgeType', options.knowledgeType)
  query.set('includeRejected', String(Boolean(options.includeRejected)))
  return client.get(`/api/papers/${encodeURIComponent(paperId)}/knowledge?${query}`)
    .then((result) => result.data ?? result)
}

export function submitKnowledgeFeedback(unitId, payload, client = apiClient) {
  return client.post(`/api/knowledge/units/${encodeURIComponent(unitId)}/feedback`, payload)
    .then((result) => result.data ?? result)
}

export function exportKnowledgeDataset(minimumConfidence = 'SILVER', client = apiClient) {
  return client.get(`/api/knowledge/dataset?minimumConfidence=${encodeURIComponent(minimumConfidence)}`)
    .then((result) => result.data ?? result)
}
