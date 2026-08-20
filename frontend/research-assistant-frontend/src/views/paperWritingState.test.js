import test from 'node:test'
import assert from 'node:assert/strict'
import { buildWritingPayload, countChineseWords, parseOutline, revisionLabel } from './paperWritingState.js'

test('parses structured outline and tolerates malformed data', () => {
  assert.equal(parseOutline('{"sections":[{"heading":"1.1 背景"}]}').length, 1)
  assert.deepEqual(parseOutline('not-json'), [])
})

test('normalizes and de-duplicates selected paper ids', () => {
  const payload = buildWritingPayload({ title: '  标题 ', topic: '主题', paperIds: [13, '13', 38, -1] })
  assert.equal(payload.title, '标题')
  assert.deepEqual(payload.paperIds, [13, 38])
})

test('counts Chinese characters and Latin words for the editor status', () => {
  assert.equal(countChineseWords('论文 RAG system'), 4)
  assert.equal(revisionLabel('AI_REVISED'), 'AI 修改')
})
