import test from 'node:test'
import assert from 'node:assert/strict'

import { chatWithRag, chatWithRagStream, parseSseEventBlock } from './rag.js'
import { saveDraftFromSession } from './researchIdeas.js'

function createFakeClient() {
  const calls = []

  return {
    calls,
    post(path, body, config) {
      calls.push({ method: 'post', path, body, config })
      return Promise.resolve({ data: body ?? { id: 1 } })
    },
  }
}

test('chatWithRag posts the question payload to the RAG chat endpoint', async () => {
  const client = createFakeClient()
  const payload = {
    question: '这篇论文的方法有什么改进空间？',
    topK: 5,
    sessionId: 12,
    paperId: 3,
  }

  const result = await chatWithRag(payload, client)

  assert.deepEqual(result, payload)
  assert.deepEqual(client.calls, [
    { method: 'post', path: '/api/rag/chat', body: payload, config: { timeout: 120000 } },
  ])
})

test('parseSseEventBlock parses named JSON events', () => {
  assert.deepEqual(parseSseEventBlock('event: delta\ndata: {"content":"片段"}'), {
    event: 'delta',
    data: { content: '片段' },
  })
})

test('chatWithRagStream handles SSE frames split across network chunks', async () => {
  const encoder = new TextEncoder()
  const chunks = [
    'event: phase\ndata: {"phase":"retrieving"}\n\nevent: delta\ndata: {"content":"流',
    '式"}\n\nevent: complete\ndata: {"answer":"流式"}\n\n',
  ]
  const body = new ReadableStream({
    start(controller) {
      chunks.forEach((chunk) => controller.enqueue(encoder.encode(chunk)))
      controller.close()
    },
  })
  const calls = []
  const fakeFetch = async () => ({ ok: true, body })

  const result = await chatWithRagStream(
    { question: 'test' },
    {
      onPhase: (data) => calls.push(data.phase),
      onDelta: (content) => calls.push(content),
    },
    fakeFetch,
  )

  assert.deepEqual(calls, ['retrieving', '流式'])
  assert.equal(result.answer, '流式')
})

test('saveDraftFromSession posts to the session draft save endpoint', async () => {
  const client = createFakeClient()

  await saveDraftFromSession(12, client)

  assert.deepEqual(client.calls, [
    {
      method: 'post',
      path: '/api/research-ideas/save-draft-from-session/12',
      body: undefined,
      config: undefined,
    },
  ])
})
