import assert from 'node:assert/strict'
import test from 'node:test'
import {
  buildPaperChatRoute,
  buildRagChatPayload,
  buildTimingRows,
  formatSelectedPaperSummary,
  formatTimingDuration,
  isRetrievalQuestionRewritten,
  isPaperSelected,
  normalizePaperRows,
  parsePaperId,
  togglePaperSelection,
} from './ragChatState.js'

test('buildPaperChatRoute opens chat with the selected paper id', () => {
  assert.deepEqual(buildPaperChatRoute({ id: 7 }), {
    path: '/chat',
    query: { paperId: 7 },
  })
})

test('parsePaperId accepts positive numeric ids only', () => {
  assert.equal(parsePaperId('9'), 9)
  assert.equal(parsePaperId(3), 3)
  assert.equal(parsePaperId('abc'), null)
  assert.equal(parsePaperId('0'), null)
})

test('togglePaperSelection adds and removes a paper id without mutating the input', () => {
  const original = [1, 3]

  const added = togglePaperSelection(original, 5)
  const removed = togglePaperSelection(added, 3)

  assert.deepEqual(original, [1, 3])
  assert.deepEqual(added, [1, 3, 5])
  assert.deepEqual(removed, [1, 5])
})

test('isPaperSelected checks selected ids with numeric conversion', () => {
  assert.equal(isPaperSelected([1, 2, 3], '2'), true)
  assert.equal(isPaperSelected([1, 2, 3], '8'), false)
})

test('buildRagChatPayload sends all selected paper ids as the retrieval scope', () => {
  assert.deepEqual(
    buildRagChatPayload({
      question: '总结选中文献的改进方向',
      topK: 6,
      sessionId: 12,
      selectedPaperIds: [3, '5', 3, null, 0, 'abc'],
    }),
    {
      question: '总结选中文献的改进方向',
      topK: 6,
      sessionId: 12,
      paperId: null,
      paperIds: [3, 5],
    },
  )
})

test('formatSelectedPaperSummary describes no selection, one selection, and many selections', () => {
  const papers = [
    { id: 1, title: 'RAG Survey' },
    { id: 2, fileName: 'agent-paper.pdf' },
    { id: 3, title: 'Embedding Notes' },
  ]

  assert.equal(formatSelectedPaperSummary(papers, []), '全库检索模式')
  assert.equal(formatSelectedPaperSummary(papers, [1]), '已选择：RAG Survey')
  assert.equal(formatSelectedPaperSummary(papers, [1, 2, 3]), '已选择 3 篇参考论文')
})

test('normalizePaperRows accepts direct arrays and common paged shapes', () => {
  const rows = [{ id: 1 }, { id: 2 }]

  assert.deepEqual(normalizePaperRows(rows), rows)
  assert.deepEqual(normalizePaperRows({ records: rows }), rows)
  assert.deepEqual(normalizePaperRows({ list: rows }), rows)
  assert.deepEqual(normalizePaperRows({ data: rows }), rows)
  assert.deepEqual(normalizePaperRows(null), [])
})

test('buildTimingRows keeps measured stages and calculates total percentage', () => {
  const rows = buildTimingRows({ totalMs: 2000, embeddingMs: 200, llmGenerationMs: 1500, bm25Ms: 0 })

  assert.deepEqual(rows, [
    { key: 'embeddingMs', label: 'Embedding', milliseconds: 200, percent: 10 },
    { key: 'llmGenerationMs', label: '模型生成', milliseconds: 1500, percent: 75 },
  ])
  assert.equal(formatTimingDuration(920), '920 毫秒')
  assert.equal(formatTimingDuration(2345), '2.35 秒')
})

test('isRetrievalQuestionRewritten ignores whitespace but detects contextual rewrite', () => {
  assert.equal(isRetrievalQuestionRewritten('它 有什么局限？', '它   有什么局限？'), false)
  assert.equal(isRetrievalQuestionRewritten('它有什么局限？', '论文13的方法有什么局限？'), true)
})
