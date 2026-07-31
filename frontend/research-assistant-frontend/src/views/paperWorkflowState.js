export function isCompletedStatus(status) {
  const normalizedStatus = String(status || '').toUpperCase()
  return ['COMPLETED', 'PARSED', 'VECTORIZED', 'SUCCESS'].includes(normalizedStatus)
}

export function isFailedStatus(status) {
  const normalizedStatus = String(status || '').toUpperCase()
  return ['FAILED', 'ERROR'].includes(normalizedStatus)
}

export function isProcessingStatus(status) {
  return String(status || '').toUpperCase() === 'PROCESSING'
}

export function getProfileStatusByPaperId(statuses = []) {
  return new Map(statuses.map((status) => [status.paperId, status]))
}

export function getPaperWorkflowState(paper, profileStatus = {}) {
  if (!isCompletedStatus(paper?.parseStatus)) {
    return {
      key: isFailedStatus(paper?.parseStatus) ? 'parseFailed' : 'pendingParse',
      label: isFailedStatus(paper?.parseStatus) ? '解析失败' : '待解析',
      description: isFailedStatus(paper?.parseStatus) ? 'PDF 解析失败，请重试解析。' : '还没有解析 PDF。',
      actionLabel: isFailedStatus(paper?.parseStatus) ? '重试解析' : '解析',
      actionType: 'parse',
      ready: false,
      processing: isProcessingStatus(paper?.parseStatus),
      failed: isFailedStatus(paper?.parseStatus),
    }
  }

  if (!isCompletedStatus(paper?.vectorStatus)) {
    return {
      key: isFailedStatus(paper?.vectorStatus) ? 'vectorFailed' : 'pendingVector',
      label: isFailedStatus(paper?.vectorStatus) ? '向量化失败' : '待向量化',
      description: isFailedStatus(paper?.vectorStatus) ? '写入 Qdrant 失败，请重试向量化。' : '已解析，等待写入 Qdrant。',
      actionLabel: isFailedStatus(paper?.vectorStatus) ? '重试向量化' : '向量化',
      actionType: 'vectorize',
      ready: false,
      processing: isProcessingStatus(paper?.vectorStatus),
      failed: isFailedStatus(paper?.vectorStatus),
    }
  }

  if (profileStatus?.jobStatus === 'PROCESSING') {
    return {
      key: 'profileProcessing',
      label: '画像生成中',
      description: profileProgressText(profileStatus),
      actionLabel: '生成中',
      actionType: null,
      ready: false,
      processing: true,
      failed: false,
    }
  }

  if (profileStatus?.jobStatus === 'FAILED') {
    return {
      key: 'profileFailed',
      label: '画像失败',
      description: profileStatus.errorMessage || '文献画像生成失败，请重试。',
      actionLabel: '重试画像',
      actionType: 'profile',
      ready: false,
      processing: false,
      failed: true,
    }
  }

  if (!profileStatus?.hasProfile) {
    return {
      key: 'pendingProfile',
      label: '待生成画像',
      description: '已解析并向量化，等待生成文献画像。',
      actionLabel: '生成画像',
      actionType: 'profile',
      ready: false,
      processing: false,
      failed: false,
    }
  }

  if (!profileStatus?.profileIndexComplete) {
    return {
      key: 'pendingProfileIndex',
      label: '待索引画像',
      description: `画像已生成，Qdrant 已索引摘要 ${profileStatus.indexedSectionSummaryCount || 0}/${profileStatus.sectionSummaryCount || 0} 条。`,
      actionLabel: '索引画像',
      actionType: 'indexProfile',
      ready: false,
      processing: false,
      failed: false,
    }
  }

  return {
    key: 'ready',
    label: '已入库',
    description: `画像与 ${profileStatus.sectionSummaryCount || 0} 条章节摘要均已建立 Qdrant 索引。`,
    actionLabel: '问答',
    actionType: 'chat',
    ready: true,
    processing: false,
    failed: false,
  }
}

export function profileProgressText(profileStatus = {}) {
  const percent = Number(profileStatus.progressPercent || 0)
  const processed = Number(profileStatus.processedSections || 0)
  const total = Number(profileStatus.totalSections || 0)
  const stepText = currentStepText(profileStatus.currentStep)

  if (total > 0) {
    return `${stepText} · 章节摘要 ${processed}/${total} · ${percent}%`
  }

  return `${stepText} · ${percent}%`
}

export function currentStepText(step) {
  const stepMap = {
    WAITING: '等待开始',
    PREPARING: '准备正文和章节',
    GENERATING_SECTIONS: '正在生成章节摘要',
    GENERATING_PROFILE: '正在生成整篇画像',
    SAVING_RESULT: '正在保存画像结果',
    COMPLETED: '已完成',
    FAILED: '已失败',
  }

  return stepMap[step] || '正在处理'
}

export function splitPaperRows(papers = [], profileStatuses = []) {
  const statusByPaperId = getProfileStatusByPaperId(profileStatuses)
  const rows = papers.map((paper) => {
    const profileStatus = statusByPaperId.get(paper.id) || { paperId: paper.id, hasProfile: false, progressPercent: 0 }
    const workflowState = getPaperWorkflowState(paper, profileStatus)
    return { ...paper, profileStatus, workflowState }
  })

  return {
    pendingRows: rows.filter((row) => !row.workflowState.ready),
    readyRows: rows.filter((row) => row.workflowState.ready),
  }
}
