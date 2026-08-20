import assert from 'node:assert/strict'
import test from 'node:test'

import {
  buildPaperKnowledge,
  exportKnowledgeDataset,
  listKnowledgeCatalogs,
  listPaperKnowledge,
  submitKnowledgeFeedback,
} from './knowledge.js'

test('knowledge catalog and unit APIs unwrap response data', async () => {
  const calls = []
  const client = {
    get(url) {
      calls.push(['get', url])
      return Promise.resolve({ data: [{ id: 1 }] })
    },
    post(url, payload) {
      calls.push(['post', url, payload])
      return Promise.resolve({ data: { status: 'BUILDING' } })
    },
  }

  assert.deepEqual(await listKnowledgeCatalogs(client), [{ id: 1 }])
  assert.deepEqual(await listPaperKnowledge(7, { knowledgeType: 'METHOD' }, client), [{ id: 1 }])
  assert.deepEqual(await buildPaperKnowledge(7, {}, client), { status: 'BUILDING' })
  assert.equal(calls[1][1], '/api/papers/7/knowledge?knowledgeType=METHOD&includeRejected=false')
  assert.equal(calls[2][1], '/api/papers/7/knowledge/build?async=true')
})

test('feedback and dataset APIs keep trust boundary explicit', async () => {
  const calls = []
  const client = {
    get(url) {
      calls.push(['get', url])
      return Promise.resolve({ data: [] })
    },
    post(url, payload) {
      calls.push(['post', url, payload])
      return Promise.resolve({ data: payload })
    },
  }

  await submitKnowledgeFeedback(9, { action: 'CONFIRM' }, client)
  await exportKnowledgeDataset('GOLD', client)
  assert.deepEqual(calls[0], ['post', '/api/knowledge/units/9/feedback', { action: 'CONFIRM' }])
  assert.deepEqual(calls[1], ['get', '/api/knowledge/dataset?minimumConfidence=GOLD'])
})
