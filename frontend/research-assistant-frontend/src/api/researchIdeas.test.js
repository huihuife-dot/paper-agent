import test from 'node:test'
import assert from 'node:assert/strict'

import {
  countResearchIdeaSaveTypes,
  createResearchIdea,
  deleteResearchIdea,
  listResearchIdeas,
  saveDraftFromSession,
  updateResearchIdea,
  updateResearchIdeaSaveType,
} from './researchIdeas.js'

function createFakeClient() {
  const calls = []

  return {
    calls,
    get(path, config) {
      calls.push({ method: 'get', path, config })
      return Promise.resolve({ data: config?.params ?? { total: 0 } })
    },
    post(path, body) {
      calls.push({ method: 'post', path, body })
      return Promise.resolve({ data: body ?? { id: 1 } })
    },
    patch(path, body) {
      calls.push({ method: 'patch', path, body })
      return Promise.resolve({ data: body })
    },
    put(path, body) {
      calls.push({ method: 'put', path, body })
      return Promise.resolve({ data: body })
    },
    delete(path) {
      calls.push({ method: 'delete', path })
      return Promise.resolve({ data: null })
    },
  }
}

test('listResearchIdeas sends non-empty filters as query params', async () => {
  const client = createFakeClient()

  const result = await listResearchIdeas(
    {
      keyword: 'RAG',
      saveType: 'draft',
      sourceType: '',
      sourceSessionId: null,
    },
    client,
  )

  assert.deepEqual(result, { keyword: 'RAG', saveType: 'draft' })
  assert.deepEqual(client.calls, [
    {
      method: 'get',
      path: '/api/research-ideas',
      config: { params: { keyword: 'RAG', saveType: 'draft' } },
    },
  ])
})

test('countResearchIdeaSaveTypes calls the stats endpoint', async () => {
  const client = createFakeClient()

  await countResearchIdeaSaveTypes(client)

  assert.deepEqual(client.calls, [
    {
      method: 'get',
      path: '/api/research-ideas/stats/save-type',
      config: undefined,
    },
  ])
})

test('createResearchIdea posts a manual idea payload', async () => {
  const client = createFakeClient()
  const payload = { title: '手动想法', sourceType: 'manual', saveType: 'draft' }

  const result = await createResearchIdea(payload, client)

  assert.deepEqual(result, payload)
  assert.deepEqual(client.calls, [
    { method: 'post', path: '/api/research-ideas', body: payload },
  ])
})

test('updateResearchIdeaSaveType patches the saveType body', async () => {
  const client = createFakeClient()

  const result = await updateResearchIdeaSaveType(8, 'todo', client)

  assert.deepEqual(result, { saveType: 'todo' })
  assert.deepEqual(client.calls, [
    {
      method: 'patch',
      path: '/api/research-ideas/8/save-type',
      body: { saveType: 'todo' },
    },
  ])
})

test('updateResearchIdeaSaveType unwraps backend Result data', async () => {
  const client = createFakeClient()
  client.patch = (path, body) => {
    client.calls.push({ method: 'patch', path, body })
    return Promise.resolve({
      code: 200,
      message: 'success',
      data: { id: 8, saveType: 'implemented' },
    })
  }

  const result = await updateResearchIdeaSaveType(8, 'implemented', client)

  assert.deepEqual(result, { id: 8, saveType: 'implemented' })
})

test('updateResearchIdea puts the complete editable payload', async () => {
  const client = createFakeClient()
  const payload = { title: '图结构研究', saveType: 'idea', relatedPaperIds: '13,15' }

  const result = await updateResearchIdea(8, payload, client)

  assert.deepEqual(result, payload)
  assert.deepEqual(client.calls, [
    { method: 'put', path: '/api/research-ideas/8', body: payload },
  ])
})

test('deleteResearchIdea calls the delete endpoint', async () => {
  const client = createFakeClient()

  const result = await deleteResearchIdea(8, client)

  assert.equal(result, null)
  assert.deepEqual(client.calls, [
    { method: 'delete', path: '/api/research-ideas/8' },
  ])
})

test('saveDraftFromSession keeps the existing session save endpoint', async () => {
  const client = createFakeClient()

  await saveDraftFromSession(12, client)

  assert.deepEqual(client.calls, [
    {
      method: 'post',
      path: '/api/research-ideas/save-draft-from-session/12',
      body: undefined,
    },
  ])
})
