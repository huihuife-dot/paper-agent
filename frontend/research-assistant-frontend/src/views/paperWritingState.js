export function parseOutline(outlineJson) {
  if (!outlineJson) return []
  try {
    const parsed = typeof outlineJson === 'string' ? JSON.parse(outlineJson) : outlineJson
    return Array.isArray(parsed?.sections) ? parsed.sections : []
  } catch {
    return []
  }
}

export function buildWritingPayload(form, content = '', outlineJson = '') {
  return {
    title: String(form.title || '').trim(),
    topic: String(form.topic || '').trim(),
    documentType: form.documentType || 'CHAPTER_ONE',
    targetLanguage: form.targetLanguage || 'zh-CN',
    targetWordCount: Number(form.targetWordCount) || 2500,
    citationStyle: form.citationStyle || 'GB_T_7714',
    paperIds: [...new Set((form.paperIds || []).map(Number).filter((id) => Number.isFinite(id) && id > 0))],
    content,
    outlineJson,
  }
}

export function countChineseWords(content) {
  const text = String(content || '').trim()
  if (!text) return 0
  const chinese = text.match(/[\u3400-\u9fff]/g)?.length || 0
  const latin = text.replace(/[\u3400-\u9fff]/g, ' ').match(/[A-Za-z0-9]+(?:[-'][A-Za-z0-9]+)*/g)?.length || 0
  return chinese + latin
}

export function revisionLabel(type) {
  return ({
    PROJECT_CREATED: '创建项目',
    MANUAL_SAVE: '手动保存',
    OUTLINE_GENERATED: '生成大纲',
    DRAFT_GENERATED: '生成草稿',
    AI_REVISED: 'AI 修改',
    VERSION_RESTORED: '恢复版本',
  })[type] || type || '历史版本'
}
