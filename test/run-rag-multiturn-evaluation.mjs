import fs from 'node:fs/promises'
import path from 'node:path'

import { chatWithRagStream } from '../frontend/research-assistant-frontend/src/api/rag.js'

const baseUrl = process.env.MYAGENT_BASE_URL || 'http://localhost:8080'
const outputFile = process.env.RAG_MULTITURN_OUTPUT_FILE || 'test/results/rag-multiturn-2026-07-16.json'
const scenarios = [
  {
    id: 'MT-SINGLE-PRONOUN',
    paperIds: [13],
    initialQuestion: '论文13的核心方法是什么？请说明主要模块。',
    followUp: '它有什么局限？',
    expectedStrategy: 'FULL_TEXT_PARSED',
    expectedRetrievalAnchors: ['13'],
    expectedPaperIds: [13],
  },
  {
    id: 'MT-MULTI-COMPARISON',
    paperIds: [13, 15],
    initialQuestion: '比较论文13和论文15的核心方法。',
    followUp: '二者在空间建模方面有什么区别？',
    expectedStrategy: 'HYBRID_RAG',
    expectedRetrievalAnchors: ['13', '15'],
    expectedPaperIds: [13, 15],
  },
  {
    id: 'MT-DISCOVERY-REFERENCE',
    paperIds: [],
    initialQuestion: '文献库中哪些论文使用频域或时频方法进行风速预测？',
    followUp: '其中哪一篇更强调图神经网络？',
    expectedStrategy: 'LIBRARY_DISCOVERY',
    expectedRetrievalAnchors: ['频', '图'],
    expectedPaperIds: [13, 15],
  },
]

async function ask(payload) {
  const startedAt = Date.now()
  let firstContentAt = null
  const response = await chatWithRagStream(payload, {
    onDelta() {
      firstContentAt ??= Date.now()
    },
  }, (url, options) => fetch(url.replace('http://localhost:8080', baseUrl), options))
  return {
    response,
    firstContentMs: firstContentAt === null ? null : firstContentAt - startedAt,
    completeMs: Date.now() - startedAt,
  }
}

const results = []
for (const scenario of scenarios) {
  const initial = await ask({
    question: scenario.initialQuestion,
    paperIds: scenario.paperIds,
    topK: 12,
  })
  const follow = await ask({
    question: scenario.followUp,
    sessionId: initial.response.sessionId,
    paperIds: scenario.paperIds,
    topK: 12,
  })

  const retrievalQuestion = String(follow.response.retrievalQuestion || '')
  const actualPaperIds = [...new Set((follow.response.sources || [])
    .map((source) => Number(source.paperId)).filter(Boolean))]
  const anchorsFound = scenario.expectedRetrievalAnchors
    .filter((anchor) => retrievalQuestion.toLowerCase().includes(anchor.toLowerCase()))
  const expectedPapersFound = scenario.expectedPaperIds
    .filter((paperId) => actualPaperIds.includes(paperId))

  const result = {
    scenarioId: scenario.id,
    sessionId: initial.response.sessionId,
    originalFollowUp: scenario.followUp,
    retrievalQuestion,
    rewritten: retrievalQuestion.trim() !== scenario.followUp.trim(),
    expectedRetrievalAnchors: scenario.expectedRetrievalAnchors,
    anchorsFound,
    anchorCoverage: anchorsFound.length / scenario.expectedRetrievalAnchors.length,
    expectedStrategy: scenario.expectedStrategy,
    actualStrategy: follow.response.contextStrategy,
    strategyCorrect: follow.response.contextStrategy === scenario.expectedStrategy,
    expectedPaperIds: scenario.expectedPaperIds,
    actualPaperIds,
    expectedPaperRecall: expectedPapersFound.length / scenario.expectedPaperIds.length,
    firstContentMs: follow.firstContentMs,
    completeMs: follow.completeMs,
    answerExcerpt: follow.response.answer?.slice(0, 240),
  }
  results.push(result)
  process.stderr.write(`${scenario.id}: ${retrievalQuestion}\n`)
}

const average = (values) => values.reduce((sum, value) => sum + value, 0) / values.length
const report = {
  metadata: {
    generatedAt: new Date().toISOString(),
    baseUrl,
    scenarioCount: results.length,
    historyWindow: '最近 6 条消息（3 轮）',
  },
  metrics: {
    executionSuccessRate: 1,
    rewriteRate: average(results.map((result) => result.rewritten ? 1 : 0)),
    anchorCoverage: average(results.map((result) => result.anchorCoverage)),
    strategyAccuracy: average(results.map((result) => result.strategyCorrect ? 1 : 0)),
    expectedPaperRecall: average(results.map((result) => result.expectedPaperRecall)),
    averageFirstContentMs: Math.round(average(results.map((result) => result.firstContentMs))),
    averageCompleteMs: Math.round(average(results.map((result) => result.completeMs))),
  },
  results,
}

const outputPath = path.resolve(outputFile)
await fs.mkdir(path.dirname(outputPath), { recursive: true })
await fs.writeFile(outputPath, `${JSON.stringify(report, null, 2)}\n`, 'utf8')
process.stdout.write(`${JSON.stringify(report, null, 2)}\n`)
