import fs from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = path.dirname(fileURLToPath(import.meta.url))
const caseFile = process.env.RAG_EVAL_CASES_FILE || 'rag-evaluation-cases.json'
const casePath = path.isAbsolute(caseFile) ? caseFile : path.join(currentDir, caseFile)
const allCases = JSON.parse(await fs.readFile(casePath, 'utf8'))
const requestedCaseIds = new Set(
  String(process.env.RAG_EVAL_CASE_IDS || '')
    .split(',')
    .map((caseId) => caseId.trim())
    .filter(Boolean),
)
const cases = requestedCaseIds.size
  ? allCases.filter((testCase) => requestedCaseIds.has(testCase.caseId))
  : allCases
if (!cases.length) {
  throw new Error('没有匹配到可运行的评测用例，请检查 RAG_EVAL_CASE_IDS')
}
const baseUrl = process.env.MYAGENT_BASE_URL || 'http://localhost:8080'
const topK = Number(process.env.RAG_EVAL_TOP_K || 12)
const outputFile = process.env.RAG_EVAL_OUTPUT_FILE
const corpusPaperIds = [...new Set(cases.flatMap((testCase) => [
  ...(testCase.paperIds || []),
  ...(testCase.expectedPaperIds || []),
]).map(Number).filter(Boolean))].sort((left, right) => left - right)

const round = (value) => Number((value || 0).toFixed(4))
const average = (values) => values.length ? values.reduce((sum, value) => sum + value, 0) / values.length : 0
const timingStageKeys = [
  'strategySelectionMs',
  'queryRewriteMs',
  'embeddingMs',
  'vectorSearchMs',
  'sourceHydrationMs',
  'bm25Ms',
  'fusionRankingMs',
  'contextBuildMs',
  'promptBuildMs',
  'llmGenerationMs',
  'historySaveMs',
  'postProcessingMs',
  'otherMs',
  'totalMs',
]
const percentile = (values, p) => {
  if (!values.length) return 0
  const sorted = [...values].sort((a, b) => a - b)
  return sorted[Math.min(sorted.length - 1, Math.ceil(sorted.length * p) - 1)]
}

function includesKeyword(answer, keyword) {
  return String(answer || '').toLowerCase().includes(String(keyword).toLowerCase())
}

function isRefusal(answer) {
  return /(未找到|没有足够|无足够|无法判断|不涉及|未涉及|没有提及|无相关|无一篇|均未|不存在|不能根据)/.test(String(answer || ''))
}

function summarizeTimings(results) {
  const timings = results.map((result) => result.timing).filter(Boolean)
  if (!timings.length) return null

  const averagesMs = Object.fromEntries(timingStageKeys.map((key) => [
    key,
    Math.round(average(timings.map((timing) => Number(timing[key]) || 0))),
  ]))
  const dominantStageCounts = timings.reduce((counts, timing) => {
    const stage = timing.dominantStage || 'unknown'
    counts[stage] = (counts[stage] || 0) + 1
    return counts
  }, {})

  return { sampleCount: timings.length, averagesMs, dominantStageCounts }
}

async function runCase(testCase) {
  const startedAt = Date.now()
  try {
    const response = await fetch(`${baseUrl}/api/rag/chat`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ question: testCase.question, paperIds: testCase.paperIds, topK }),
    })
    const payload = await response.json()
    if (!response.ok || payload.code !== 200) {
      throw new Error(payload.message || `HTTP ${response.status}`)
    }
    const data = payload.data || {}
    const sources = Array.isArray(data.sources) ? data.sources : []
    const actualPaperIds = [...new Set(sources.map((source) => Number(source.paperId)).filter(Boolean))]
    const recommendedPaperIds = [...new Set((data.paperRelevance || [])
      .map((paper) => Number(paper.paperId)).filter(Boolean))]
    const expectedPaperIds = testCase.expectedPaperIds || []
    const expectedHits = expectedPaperIds.filter((id) => actualPaperIds.includes(id)).length
    const recommendedHits = expectedPaperIds.filter((id) => recommendedPaperIds.includes(id)).length
    const scoped = Array.isArray(testCase.paperIds) && testCase.paperIds.length > 0
    const inScope = scoped ? sources.filter((source) => testCase.paperIds.includes(Number(source.paperId))).length : sources.length
    const noisy = sources.filter((source) => source.isReference === true || source.isNoise === true).length
    const keywordHits = (testCase.expectedAnswerKeywords || []).filter((keyword) => includesKeyword(data.answer, keyword))
    const sourceTypes = Object.fromEntries(
      Object.entries(sources.reduce((counts, source) => {
        const type = source.sourceType || 'unknown'
        counts[type] = (counts[type] || 0) + 1
        return counts
      }, {})).sort(),
    )

    return {
      caseId: testCase.caseId,
      category: testCase.category,
      success: true,
      latencyMs: Date.now() - startedAt,
      expectedStrategy: testCase.expectedStrategy,
      actualStrategy: data.contextStrategy,
      strategyCorrect: data.contextStrategy === testCase.expectedStrategy,
      expectedPaperIds,
      actualPaperIds,
      recommendedPaperIds,
      expectedPaperRecall: expectedPaperIds.length ? round(expectedHits / expectedPaperIds.length) : null,
      expectedPaperPrecision: expectedPaperIds.length && actualPaperIds.length
        ? round(expectedHits / actualPaperIds.length) : null,
      recommendedPaperRecall: expectedPaperIds.length ? round(recommendedHits / expectedPaperIds.length) : null,
      recommendedPaperPrecision: expectedPaperIds.length && recommendedPaperIds.length
        ? round(recommendedHits / recommendedPaperIds.length) : null,
      scopePurity: scoped && sources.length ? round(inScope / sources.length) : null,
      referenceNoiseRate: sources.length ? round(noisy / sources.length) : 0,
      sourceCount: sources.length,
      sourceTypes,
      keywordCoverage: testCase.expectedAnswerKeywords?.length
        ? round(keywordHits.length / testCase.expectedAnswerKeywords.length) : null,
      keywordHits,
      refusalExpected: testCase.expectRefusal === true,
      refusalDetected: isRefusal(data.answer),
      sessionId: data.sessionId,
      timing: data.timing || null,
      answer: String(data.answer || ''),
      sources,
      answerExcerpt: String(data.answer || '').slice(0, 180),
    }
  } catch (error) {
    return { caseId: testCase.caseId, category: testCase.category, success: false, latencyMs: Date.now() - startedAt, error: error.message }
  }
}

const results = []
for (const testCase of cases) {
  const result = await runCase(testCase)
  results.push(result)
  process.stderr.write(`${testCase.caseId}: ${result.success ? 'OK' : 'FAILED'} (${result.latencyMs}ms)\n`)
}

const successful = results.filter((result) => result.success)
const recallResults = successful.filter((result) => result.expectedPaperRecall !== null)
const precisionResults = successful.filter((result) => result.expectedPaperPrecision !== null)
const recommendedRecallResults = successful.filter((result) => result.recommendedPaperRecall !== null)
const recommendedPrecisionResults = successful.filter((result) => result.recommendedPaperPrecision !== null)
const scopedResults = successful.filter((result) => result.scopePurity !== null)
const keywordResults = successful.filter((result) => result.keywordCoverage !== null)
const refusalResults = successful.filter((result) => result.refusalExpected)
const discoveryResults = successful.filter((result) => result.category === 'discovery')
const latencies = successful.map((result) => result.latencyMs)

function summarizeCategory(categoryResults) {
  const categorySuccessful = categoryResults.filter((result) => result.success)
  const categoryRecall = categorySuccessful.filter((result) => result.expectedPaperRecall !== null)
  const categoryPrecision = categorySuccessful.filter((result) => result.expectedPaperPrecision !== null)
  const categoryRecommendedRecall = categorySuccessful.filter((result) => result.recommendedPaperRecall !== null)
  const categoryRecommendedPrecision = categorySuccessful.filter((result) => result.recommendedPaperPrecision !== null)
  const categoryScoped = categorySuccessful.filter((result) => result.scopePurity !== null)
  const categoryKeywords = categorySuccessful.filter((result) => result.keywordCoverage !== null)
  const categoryRefusals = categorySuccessful.filter((result) => result.refusalExpected)
  const categoryLatencies = categorySuccessful.map((result) => result.latencyMs)
  return {
    caseCount: categoryResults.length,
    executionSuccessRate: round(categorySuccessful.length / categoryResults.length),
    strategyAccuracy: round(average(categorySuccessful.map((result) => result.strategyCorrect ? 1 : 0))),
    expectedPaperRecall: categoryRecall.length
      ? round(average(categoryRecall.map((result) => result.expectedPaperRecall))) : null,
    expectedPaperPrecision: categoryPrecision.length
      ? round(average(categoryPrecision.map((result) => result.expectedPaperPrecision))) : null,
    recommendedPaperRecall: categoryRecommendedRecall.length
      ? round(average(categoryRecommendedRecall.map((result) => result.recommendedPaperRecall))) : null,
    recommendedPaperPrecision: categoryRecommendedPrecision.length
      ? round(average(categoryRecommendedPrecision.map((result) => result.recommendedPaperPrecision))) : null,
    scopePurity: categoryScoped.length
      ? round(average(categoryScoped.map((result) => result.scopePurity))) : null,
    referenceNoiseRate: round(average(categorySuccessful.map((result) => result.referenceNoiseRate))),
    keywordCoverage: categoryKeywords.length
      ? round(average(categoryKeywords.map((result) => result.keywordCoverage))) : null,
    refusalAccuracy: categoryRefusals.length
      ? round(average(categoryRefusals.map((result) => result.refusalDetected ? 1 : 0))) : null,
    averageLatencyMs: Math.round(average(categoryLatencies)),
    p50LatencyMs: percentile(categoryLatencies, 0.5),
    p95LatencyMs: percentile(categoryLatencies, 0.95),
    timing: summarizeTimings(categorySuccessful),
  }
}

const categoryMetrics = Object.fromEntries(
  [...new Set(results.map((result) => result.category))]
    .map((category) => [category, summarizeCategory(results.filter((result) => result.category === category))]),
)

const report = {
  metadata: {
    generatedAt: new Date().toISOString(),
    baseUrl,
    topK,
    caseCount: cases.length,
    caseFile: path.basename(casePath),
    corpusPaperIds,
  },
  metrics: {
    executionSuccessRate: round(successful.length / cases.length),
    strategyAccuracy: round(average(successful.map((result) => result.strategyCorrect ? 1 : 0))),
    expectedPaperRecall: round(average(recallResults.map((result) => result.expectedPaperRecall))),
    expectedPaperPrecision: round(average(precisionResults.map((result) => result.expectedPaperPrecision))),
    recommendedPaperRecall: round(average(recommendedRecallResults.map((result) => result.recommendedPaperRecall))),
    recommendedPaperPrecision: round(average(recommendedPrecisionResults.map((result) => result.recommendedPaperPrecision))),
    scopePurity: round(average(scopedResults.map((result) => result.scopePurity))),
    referenceNoiseRate: round(average(successful.map((result) => result.referenceNoiseRate))),
    keywordCoverage: round(average(keywordResults.map((result) => result.keywordCoverage))),
    refusalAccuracy: round(average(refusalResults.map((result) => result.refusalDetected ? 1 : 0))),
    discoveryMultiGranularityRate: round(average(discoveryResults.map((result) => {
      const types = Object.keys(result.sourceTypes || {})
      return types.includes('paper_profile') && types.includes('section_summary') && types.includes('raw_chunk') ? 1 : 0
    }))),
    averageLatencyMs: Math.round(average(latencies)),
    p50LatencyMs: percentile(latencies, 0.5),
    p95LatencyMs: percentile(latencies, 0.95),
    timing: summarizeTimings(successful),
  },
  categoryMetrics,
  results,
}

const serializedReport = `${JSON.stringify(report, null, 2)}\n`
if (outputFile) {
  const outputPath = path.isAbsolute(outputFile) ? outputFile : path.resolve(process.cwd(), outputFile)
  await fs.mkdir(path.dirname(outputPath), { recursive: true })
  await fs.writeFile(outputPath, serializedReport, 'utf8')
  process.stderr.write(`Saved report: ${outputPath}\n`)
}
process.stdout.write(serializedReport)
