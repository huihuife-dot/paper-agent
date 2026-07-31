export function parseRelatedPaperIds(value) {
  const values = Array.isArray(value) ? value : String(value || '').split(',')

  return [...new Set(values
    .map((item) => Number(String(item).trim()))
    .filter((id) => Number.isInteger(id) && id > 0))]
}

export function normalizeRelatedPaperIds(value) {
  return parseRelatedPaperIds(value).join(',')
}

export function buildResearchIdeaUpdatePayload(form = {}, original = {}) {
  const source = original || {}

  return {
    title: String(form.title || '').trim(),
    originalContent: String(form.originalContent || '').trim(),
    refinedContent: String(form.refinedContent || '').trim(),
    innovationPoints: String(form.innovationPoints || '').trim(),
    researchQuestion: String(form.researchQuestion || '').trim(),
    possibleMethod: String(form.possibleMethod || '').trim(),
    tags: String(form.tags || '').trim(),
    saveType: form.saveType || source.saveType || 'draft',
    relatedPaperIds: normalizeRelatedPaperIds(form.relatedPaperIds),
    sourceType: source.sourceType || 'manual',
    sourceSessionId: source.sourceSessionId ?? null,
    sourceMessageId: source.sourceMessageId ?? null,
  }
}
