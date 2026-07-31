import fs from 'node:fs/promises'
import path from 'node:path'

import { chatWithRagStream } from '../frontend/research-assistant-frontend/src/api/rag.js'

const baseUrl = process.env.MYAGENT_BASE_URL || 'http://localhost:8080'
const outputFile = process.env.RAG_STREAM_OUTPUT_FILE || 'test/results/rag-streaming-2026-07-16.json'
const allCases = JSON.parse(await fs.readFile('test/rag-performance-cases.json', 'utf8'))
const selectedIds = new Set(
  String(process.env.RAG_STREAM_CASE_IDS || 'PERF-SINGLE-13,PERF-DISCOVERY-FREQUENCY')
    .split(',').map((value) => value.trim()).filter(Boolean),
)
const cases = allCases.filter((testCase) => selectedIds.has(testCase.caseId))

if (!cases.length) throw new Error('没有匹配到流式评测用例')

async function runCase(testCase) {
  const startedAt = Date.now()
  let metadataAt = null
  let firstDeltaAt = null
  let deltaCount = 0
  let streamedAnswer = ''

  const response = await chatWithRagStream(
    { question: testCase.question, paperIds: testCase.paperIds, topK: 12 },
    {
      onMetadata() {
        metadataAt ??= Date.now()
      },
      onDelta(content) {
        firstDeltaAt ??= Date.now()
        deltaCount += 1
        streamedAnswer += content
      },
    },
    (url, options) => fetch(url.replace('http://localhost:8080', baseUrl), options),
  )
  const completedAt = Date.now()

  return {
    caseId: testCase.caseId,
    expectedStrategy: testCase.expectedStrategy,
    actualStrategy: response.contextStrategy,
    strategyCorrect: response.contextStrategy === testCase.expectedStrategy,
    metadataMs: metadataAt === null ? null : metadataAt - startedAt,
    firstContentMs: firstDeltaAt === null ? null : firstDeltaAt - startedAt,
    completeMs: completedAt - startedAt,
    waitReductionRate: firstDeltaAt === null
      ? null
      : Number((1 - ((firstDeltaAt - startedAt) / (completedAt - startedAt))).toFixed(4)),
    deltaCount,
    answerMatches: streamedAnswer === response.answer,
    serverTiming: response.timing,
    sessionId: response.sessionId,
    answerExcerpt: response.answer?.slice(0, 180),
  }
}

const results = []
for (const testCase of cases) {
  const result = await runCase(testCase)
  results.push(result)
  process.stderr.write(`${result.caseId}: first=${result.firstContentMs}ms complete=${result.completeMs}ms deltas=${result.deltaCount}\n`)
}

const average = (values) => Math.round(values.reduce((sum, value) => sum + value, 0) / values.length)
const report = {
  metadata: {
    generatedAt: new Date().toISOString(),
    baseUrl,
    caseCount: results.length,
  },
  metrics: {
    executionSuccessRate: 1,
    strategyAccuracy: results.filter((result) => result.strategyCorrect).length / results.length,
    answerMatchRate: results.filter((result) => result.answerMatches).length / results.length,
    averageFirstContentMs: average(results.map((result) => result.firstContentMs)),
    averageCompleteMs: average(results.map((result) => result.completeMs)),
    averageWaitReductionRate: Number((results.reduce((sum, result) => sum + result.waitReductionRate, 0) / results.length).toFixed(4)),
  },
  results,
}

const outputPath = path.resolve(outputFile)
await fs.mkdir(path.dirname(outputPath), { recursive: true })
await fs.writeFile(outputPath, `${JSON.stringify(report, null, 2)}\n`, 'utf8')
process.stdout.write(`${JSON.stringify(report, null, 2)}\n`)
