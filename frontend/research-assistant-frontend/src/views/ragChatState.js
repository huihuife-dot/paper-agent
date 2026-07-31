export function parsePaperId(value) {
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null
}

export function buildPaperChatRoute(paper) {
  const paperId = parsePaperId(paper?.id)

  if (!paperId) {
    return { path: '/chat' }
  }

  return {
    path: '/chat',
    query: { paperId },
  }
}

export function togglePaperSelection(selectedIds = [], paperId) {
  const normalizedPaperId = Number(paperId)

  if (!Number.isFinite(normalizedPaperId)) {
    return [...selectedIds]
  }

  if (selectedIds.map(Number).includes(normalizedPaperId)) {
    return selectedIds.map(Number).filter((id) => id !== normalizedPaperId)
  }

  return [...selectedIds.map(Number), normalizedPaperId]
}

export function isPaperSelected(selectedIds = [], paperId) {
  const normalizedPaperId = Number(paperId)

  return selectedIds.map(Number).includes(normalizedPaperId)
}

export function normalizeSelectedPaperIds(selectedIds = []) {
  return selectedIds
    .map(Number)
    .filter((id) => Number.isFinite(id) && id > 0)
    .filter((id, index, ids) => ids.indexOf(id) === index)
}

export function buildRagChatPayload({ question, topK, sessionId, selectedPaperIds = [] }) {
  const paperIds = normalizeSelectedPaperIds(selectedPaperIds)

  return {
    question,
    topK,
    sessionId,
    paperId: paperIds.length === 1 ? paperIds[0] : null,
    paperIds,
  }
}

export function formatSelectedPaperSummary(papers = [], selectedIds = []) {
  const normalizedSelectedIds = selectedIds.map(Number)

  if (normalizedSelectedIds.length === 0) {
    return '全库检索模式'
  }

  if (normalizedSelectedIds.length === 1) {
    const selectedPaper = papers.find((paper) => Number(paper.id) === normalizedSelectedIds[0])
    const paperTitle = selectedPaper?.title || selectedPaper?.fileName || `Paper #${normalizedSelectedIds[0]}`

    return `已选择：${paperTitle}`
  }

  return `已选择 ${normalizedSelectedIds.length} 篇参考论文`
}

/**
 * 格式化论文相关度的简洁描述。
 */
export function formatPaperRelevanceSummary(paperRelevance = []) {
  if (!paperRelevance || paperRelevance.length === 0) {
    return ''
  }

  return paperRelevance
    .map((pr) => `${pr.paperTitle || '未知论文'}（命中 ${pr.hitCount} 个片段，最高相关度 ${formatRelevanceScore(pr.maxScore)}）`)
    .join('；')
}

function formatRelevanceScore(score) {
  if (score === undefined || score === null) {
    return '-'
  }

  return Number(score).toFixed(4)
}

export function normalizePaperRows(result) {
  if (Array.isArray(result)) {
    return result
  }

  return result?.records || result?.list || result?.data || []
}

const timingStages = [
  ['strategySelectionMs', '策略选择'],
  ['queryRewriteMs', '查询改写'],
  ['embeddingMs', 'Embedding'],
  ['vectorSearchMs', 'Qdrant 检索'],
  ['sourceHydrationMs', '来源回查'],
  ['bm25Ms', 'BM25'],
  ['fusionRankingMs', '融合与重排'],
  ['contextBuildMs', '上下文构造'],
  ['promptBuildMs', 'Prompt 构造'],
  ['llmGenerationMs', '模型生成'],
  ['historySaveMs', '历史保存'],
  ['postProcessingMs', '后处理'],
  ['otherMs', '其他'],
]

export function buildTimingRows(timing) {
  if (!timing || typeof timing !== 'object') {
    return []
  }

  const totalMs = Math.max(0, Number(timing.totalMs) || 0)
  return timingStages
    .map(([key, label]) => {
      const milliseconds = Math.max(0, Number(timing[key]) || 0)
      return {
        key,
        label,
        milliseconds,
        percent: totalMs > 0 ? Number(((milliseconds / totalMs) * 100).toFixed(1)) : 0,
      }
    })
    .filter((row) => row.milliseconds > 0)
}

export function formatTimingDuration(milliseconds) {
  const value = Math.max(0, Number(milliseconds) || 0)
  return value >= 1000 ? `${(value / 1000).toFixed(2)} 秒` : `${Math.round(value)} 毫秒`
}

export function isRetrievalQuestionRewritten(originalQuestion, retrievalQuestion) {
  const original = String(originalQuestion || '').replace(/\s+/g, ' ').trim()
  const retrieval = String(retrievalQuestion || '').replace(/\s+/g, ' ').trim()
  return Boolean(original && retrieval && original !== retrieval)
}
