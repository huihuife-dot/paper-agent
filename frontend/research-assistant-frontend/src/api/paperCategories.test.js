import test from 'node:test'
import assert from 'node:assert/strict'

import {
  createPaperCategory,
  deletePaperCategory,
  listPaperCategories,
  updatePaperCategory,
} from './paperCategories.js'

function createFakeClient() {
  const calls = []

  return {
    calls,
    get(path) {
      calls.push({ method: 'get', path })
      return Promise.resolve({ data: [] })
    },
    post(path, body) {
      calls.push({ method: 'post', path, body })
      return Promise.resolve({ data: body ?? null })
    },
    put(path, body) {
      calls.push({ method: 'put', path, body })
      return Promise.resolve({ data: body ?? null })
    },
    delete(path) {
      calls.push({ method: 'delete', path })
      return Promise.resolve({ data: undefined })
    },
  }
}

test('listPaperCategories calls the category list endpoint', async () => {
  const client = createFakeClient()

  const result = await listPaperCategories(client)

  assert.deepEqual(result, [])
  assert.deepEqual(client.calls, [{ method: 'get', path: '/api/paper-categories' }])
})

test('createPaperCategory posts category payload', async () => {
  const client = createFakeClient()
  const payload = { name: 'RAG 核心论文', description: '重点阅读' }

  const result = await createPaperCategory(payload, client)

  assert.deepEqual(result, payload)
  assert.deepEqual(client.calls, [{ method: 'post', path: '/api/paper-categories', body: payload }])
})

test('updatePaperCategory puts category payload', async () => {
  const client = createFakeClient()
  const payload = { name: 'RAG 重点论文', description: '已精读' }

  const result = await updatePaperCategory(2, payload, client)

  assert.deepEqual(result, payload)
  assert.deepEqual(client.calls, [{ method: 'put', path: '/api/paper-categories/2', body: payload }])
})

test('deletePaperCategory deletes one category', async () => {
  const client = createFakeClient()

  const result = await deletePaperCategory(2, client)

  assert.deepEqual(result, { data: undefined })
  assert.deepEqual(client.calls, [{ method: 'delete', path: '/api/paper-categories/2' }])
})
