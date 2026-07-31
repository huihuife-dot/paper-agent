import test from 'node:test'
import assert from 'node:assert/strict'

import {
  buildResearchIdeaUpdatePayload,
  normalizeRelatedPaperIds,
  parseRelatedPaperIds,
} from './researchIdeaState.js'

test('parseRelatedPaperIds keeps unique positive integer ids', () => {
  assert.deepEqual(parseRelatedPaperIds('13, 15,13,0,bad,30'), [13, 15, 30])
  assert.equal(normalizeRelatedPaperIds(['3', 5, 3, null]), '3,5')
})

test('buildResearchIdeaUpdatePayload trims content and preserves source metadata', () => {
  const payload = buildResearchIdeaUpdatePayload(
    {
      title: '  图学习想法  ',
      refinedContent: '  比较构图方式 ',
      relatedPaperIds: '13, 15,13',
      saveType: 'todo',
    },
    {
      sourceType: 'rag_chat',
      sourceSessionId: 8,
      sourceMessageId: 20,
    },
  )

  assert.deepEqual(payload, {
    title: '图学习想法',
    originalContent: '',
    refinedContent: '比较构图方式',
    innovationPoints: '',
    researchQuestion: '',
    possibleMethod: '',
    tags: '',
    saveType: 'todo',
    relatedPaperIds: '13,15',
    sourceType: 'rag_chat',
    sourceSessionId: 8,
    sourceMessageId: 20,
  })
})

test('buildResearchIdeaUpdatePayload creates a manual draft without fake source links', () => {
  const payload = buildResearchIdeaUpdatePayload({ title: ' 新想法 ' }, null)

  assert.equal(payload.title, '新想法')
  assert.equal(payload.sourceType, 'manual')
  assert.equal(payload.saveType, 'draft')
  assert.equal(payload.sourceSessionId, null)
  assert.equal(payload.sourceMessageId, null)
})
