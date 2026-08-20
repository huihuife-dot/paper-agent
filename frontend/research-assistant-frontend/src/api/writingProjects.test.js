import test from 'node:test'
import assert from 'node:assert/strict'
import {
  createWritingProject,
  generateWritingDraft,
  restoreWritingRevision,
  reviseWritingContent,
} from './writingProjects.js'

function fakeClient() {
  const calls = []
  return {
    calls,
    post(path, body, config) {
      calls.push({ path, body, config })
      return Promise.resolve({ data: body ?? { ok: true } })
    },
  }
}

test('creates a writing project with selected paper scope', async () => {
  const client = fakeClient()
  const payload = { title: '综述', topic: 'RAG', paperIds: [13, 38] }
  await createWritingProject(payload, client)
  assert.deepEqual(client.calls[0], { path: '/api/writing-projects', body: payload, config: undefined })
})

test('draft generation uses the long request timeout', async () => {
  const client = fakeClient()
  await generateWritingDraft(7, '突出方法演进', client)
  assert.deepEqual(client.calls[0], {
    path: '/api/writing-projects/7/draft',
    body: { instruction: '突出方法演进' },
    config: { timeout: 300000 },
  })
})

test('revision can be limited to selected text', async () => {
  const client = fakeClient()
  await reviseWritingContent(7, { instruction: '更通俗', selectedText: '原段落' }, client)
  assert.equal(client.calls[0].path, '/api/writing-projects/7/revise')
  assert.equal(client.calls[0].body.selectedText, '原段落')
})

test('restores an owned project revision', async () => {
  const client = fakeClient()
  await restoreWritingRevision(7, 21, client)
  assert.equal(client.calls[0].path, '/api/writing-projects/7/revisions/21/restore')
})
