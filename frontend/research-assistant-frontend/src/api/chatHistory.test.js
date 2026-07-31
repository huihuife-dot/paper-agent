import test from 'node:test'
import assert from 'node:assert/strict'

import { createChatSession, deleteChatSession, listChatMessages, listChatSessions } from './chatHistory.js'

function createFakeClient() {
  const calls = []

  return {
    calls,
    get(path) {
      calls.push({ method: 'get', path })
      return Promise.resolve({ data: [{ id: 1 }] })
    },
    post(path, body, config) {
      calls.push({ method: 'post', path, body, config })
      return Promise.resolve({ data: { id: 2 } })
    },
    delete(path) {
      calls.push({ method: 'delete', path })
      return Promise.resolve({ data: null })
    },
  }
}

test('listChatSessions calls the session list endpoint', async () => {
  const client = createFakeClient()

  const result = await listChatSessions(client)

  assert.deepEqual(result, [{ id: 1 }])
  assert.deepEqual(client.calls, [
    { method: 'get', path: '/api/chat/sessions' },
  ])
})

test('listChatMessages calls the session messages endpoint', async () => {
  const client = createFakeClient()

  await listChatMessages(7, client)

  assert.deepEqual(client.calls, [
    { method: 'get', path: '/api/chat/sessions/7/messages' },
  ])
})

test('createChatSession sends non-empty params', async () => {
  const client = createFakeClient()

  const result = await createChatSession({ title: '论文问答', paperId: 3 }, client)

  assert.deepEqual(result, { id: 2 })
  assert.deepEqual(client.calls, [
    {
      method: 'post',
      path: '/api/chat/sessions',
      body: null,
      config: { params: { title: '论文问答', paperId: 3 } },
    },
  ])
})

test('deleteChatSession calls the session delete endpoint', async () => {
  const client = createFakeClient()

  await deleteChatSession(7, client)

  assert.deepEqual(client.calls, [
    { method: 'delete', path: '/api/chat/sessions/7' },
  ])
})
