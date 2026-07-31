import apiClient from './client.js'

export function listPapers(optionsOrClient = {}, maybeClient = apiClient) {
  const hasExplicitClient = typeof optionsOrClient?.get === 'function'
  const options = hasExplicitClient ? {} : optionsOrClient || {}
  const client = hasExplicitClient ? optionsOrClient : maybeClient
  const query = options.categoryId ? `?categoryId=${encodeURIComponent(options.categoryId)}` : ''

  return client.get(`/api/papers${query}`).then((result) => result.data ?? result)
}

export function uploadPaper(formData, client = apiClient) {
  return client.post('/api/papers/upload', formData).then((result) => result.data ?? result)
}

export function parsePaper(id, client = apiClient) {
  return client.post(`/api/papers/${id}/parse`).then((result) => result.data ?? result)
}

export function vectorizePaper(id, client = apiClient) {
  return client.post(`/api/papers/${id}/vectorize`).then((result) => result.data ?? result)
}

export function deletePaper(id, client = apiClient) {
  return client.delete(`/api/papers/${id}`).then((result) => result.data ?? result)
}

export function updatePaperCategoryOfPaper(id, categoryId, client = apiClient) {
  return client.patch(`/api/papers/${id}/category`, { categoryId }).then((result) => result.data ?? result)
}

export function updatePaperMetadata(id, metadata, client = apiClient) {
  return client.patch(`/api/papers/${id}`, metadata).then((result) => result.data ?? result)
}

export function getPaperContentUrl(id, baseUrl = import.meta.env?.VITE_API_BASE_URL || 'http://localhost:8080') {
  return `${baseUrl.replace(/\/$/, '')}/api/papers/${encodeURIComponent(id)}/content`
}

export function getPaperProfile(id, client = apiClient) {
  return client.get(`/api/papers/${id}/profile`).then((result) => result.data ?? result)
}

export function generatePaperProfile(id, client = apiClient) {
  return client.post(`/api/papers/${id}/profile`, undefined, { timeout: 180000 }).then((result) => result.data ?? result)
}

export function startPaperProfileJob(id, client = apiClient) {
  return client.post(`/api/papers/${id}/profile/async`).then((result) => result.data ?? result)
}

export function getPaperProfileJob(id, client = apiClient) {
  return client.get(`/api/papers/${id}/profile/job`).then((result) => result.data ?? result)
}

export function listPaperProfileStatuses(client = apiClient) {
  return client.get('/api/papers/profile-status').then((result) => result.data ?? result)
}

export function indexPaperProfile(id, client = apiClient) {
  return client.post(`/api/papers/${id}/profile/index`, undefined, { timeout: 300000 })
    .then((result) => result.data ?? result)
}

export function getPaperProfileIndexStatus(id, client = apiClient) {
  return client.get(`/api/papers/${id}/profile/index/status`).then((result) => result.data ?? result)
}

export function extractPaperAssets(id, client = apiClient) { return client.post(`/api/papers/${id}/assets/extract`, undefined, { timeout: 180000 }).then((result) => result.data ?? result) }
export function listPaperAssets(id, client = apiClient) { return client.get(`/api/papers/${id}/assets`).then((result) => result.data ?? result) }
export function getPaperAssetContentUrl(paperId, assetId, baseUrl = import.meta.env?.VITE_API_BASE_URL || 'http://localhost:8080') { return `${baseUrl.replace(/\/$/, '')}/api/papers/${encodeURIComponent(paperId)}/assets/${encodeURIComponent(assetId)}/content` }
export function confirmPaperAsset(paperId, assetId, client = apiClient) { return client.post(`/api/papers/${paperId}/assets/${assetId}/confirm`).then((result) => result.data ?? result) }
export function rejectPaperAsset(paperId, assetId, client = apiClient) { return client.post(`/api/papers/${paperId}/assets/${assetId}/reject`).then((result) => result.data ?? result) }
export function analyzePaperAsset(paperId, assetId, client = apiClient) { return client.post(`/api/papers/${paperId}/assets/${assetId}/analyze`, undefined, { timeout: 120000 }).then((result) => result.data ?? result) }
export function analyzeEligiblePaperAssets(paperId, client = apiClient) { return client.post(`/api/papers/${paperId}/assets/analyze`, undefined, { timeout: 600000 }).then((result) => result.data ?? result) }
export function getPaperAssetAnalysisStatus(paperId, client = apiClient) { return client.get(`/api/papers/${paperId}/assets/status`).then((result) => result.data ?? result) }
export function rebuildPaperReproductionFacts(paperId, client = apiClient) { return client.post(`/api/papers/${paperId}/reproduction-facts/rebuild`, undefined, { timeout: 300000 }).then((result) => result.data ?? result) }
export function listPaperReproductionFacts(paperId, includeModelInferred = false, client = apiClient) { return client.get(`/api/papers/${paperId}/reproduction-facts?includeModelInferred=${includeModelInferred}`).then((result) => result.data ?? result) }
export function searchPaperReproductionFacts(paperId, query, includeModelInferred = false, client = apiClient) { return client.get(`/api/papers/${paperId}/reproduction-facts/search?query=${encodeURIComponent(query)}&topK=20&includeModelInferred=${includeModelInferred}`).then((result) => result.data ?? result) }
export function getPaperReproductionSpec(paperId, client = apiClient) { return client.get(`/api/papers/${paperId}/reproduction-spec`).then((result) => result.data ?? result) }
export function rebuildPaperReproductionSpec(paperId, client = apiClient) { return client.post(`/api/papers/${paperId}/reproduction-spec/rebuild`, undefined, { timeout: 300000 }).then((result) => result.data ?? result) }
export function getPaperReproductionContext(paperId, version = 2, client = apiClient) { return client.get(`/api/papers/${paperId}/reproduction-context?version=${encodeURIComponent(version)}`).then((result) => result.data ?? result) }
