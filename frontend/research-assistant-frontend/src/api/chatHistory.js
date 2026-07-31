import apiClient from './client.js'

function unwrapResult(result) {
  return Object.prototype.hasOwnProperty.call(result, 'data') ? result.data : result
}

function compactParams(params = {}) {
  return Object.fromEntries(
    Object.entries(params).filter(([, value]) => value !== '' && value !== null && value !== undefined),
  )
}

export function listChatSessions(client = apiClient) {
  return client.get('/api/chat/sessions').then(unwrapResult)
}

export function listChatMessages(sessionId, client = apiClient) {
  return client.get(`/api/chat/sessions/${sessionId}/messages`).then(unwrapResult)
}

export function createChatSession({ title, paperId } = {}, client = apiClient) {
  return client
    .post('/api/chat/sessions', null, {
      params: compactParams({ title, paperId }),
    })
    .then(unwrapResult)
}

export function deleteChatSession(sessionId, client = apiClient) {
  return client.delete(`/api/chat/sessions/${sessionId}`).then(unwrapResult)
}
