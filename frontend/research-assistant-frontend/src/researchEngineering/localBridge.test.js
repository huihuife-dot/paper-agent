import test from 'node:test'
import assert from 'node:assert/strict'
import { bootstrapPaperWithBridge } from './localBridge.js'

test('paper bootstrap sends only paper id and token to localhost bridge', async () => {
  let request
  const result = await bootstrapPaperWithBridge(
    { bridgeUrl: 'http://127.0.0.1:8765/', token: 'x'.repeat(24), paperId: 38 },
    async (url, options) => {
      request = { url, options }
      return { ok: true, json: async () => ({ project_id: 'repro_1', state: 'CONTEXT_READY' }) }
    },
  )
  assert.equal(request.url, 'http://127.0.0.1:8765/v1/paper-projects')
  assert.equal(request.options.headers['X-Agent-Bridge-Token'], 'x'.repeat(24))
  assert.equal(request.options.body, '{"paper_id":38}')
  assert.equal(result.state, 'CONTEXT_READY')
})
