import test from 'node:test'
import assert from 'node:assert/strict'

import { getPaperWorkflowState, splitPaperRows } from './paperWorkflowState.js'

function paper(overrides = {}) {
  return {
    id: 7,
    parseStatus: 'PENDING',
    vectorStatus: 'PENDING',
    ...overrides,
  }
}

function status(overrides = {}) {
  return {
    paperId: 7,
    hasProfile: false,
    jobStatus: null,
    currentStep: null,
    progressPercent: 0,
    processedSections: 0,
    totalSections: 0,
    retryCount: 0,
    errorMessage: null,
    ...overrides,
  }
}

test('workflow state is pending parse before parsing completes', () => {
  const state = getPaperWorkflowState(paper(), status())

  assert.equal(state.key, 'pendingParse')
  assert.equal(state.actionLabel, '解析')
  assert.equal(state.actionType, 'parse')
  assert.equal(state.ready, false)
})

test('workflow state is pending vector after parsing completes', () => {
  const state = getPaperWorkflowState(paper({ parseStatus: 'COMPLETED' }), status())

  assert.equal(state.key, 'pendingVector')
  assert.equal(state.actionLabel, '向量化')
  assert.equal(state.actionType, 'vectorize')
})

test('workflow state is pending profile after vectorization completes', () => {
  const state = getPaperWorkflowState(paper({ parseStatus: 'COMPLETED', vectorStatus: 'COMPLETED' }), status())

  assert.equal(state.key, 'pendingProfile')
  assert.equal(state.actionLabel, '生成画像')
  assert.equal(state.actionType, 'profile')
})

test('workflow state shows profile processing progress', () => {
  const state = getPaperWorkflowState(
    paper({ parseStatus: 'COMPLETED', vectorStatus: 'COMPLETED' }),
    status({ jobStatus: 'PROCESSING', currentStep: 'GENERATING_SECTIONS', progressPercent: 35, processedSections: 12, totalSections: 51 }),
  )

  assert.equal(state.key, 'profileProcessing')
  assert.equal(state.actionLabel, '生成中')
  assert.equal(state.processing, true)
  assert.match(state.description, /12\/51/)
  assert.match(state.description, /35%/)
})

test('workflow state shows failed profile retry', () => {
  const state = getPaperWorkflowState(
    paper({ parseStatus: 'COMPLETED', vectorStatus: 'COMPLETED' }),
    status({ jobStatus: 'FAILED', currentStep: 'FAILED', errorMessage: 'Qwen API Key 未配置' }),
  )

  assert.equal(state.key, 'profileFailed')
  assert.equal(state.actionLabel, '重试画像')
  assert.equal(state.actionType, 'profile')
  assert.equal(state.failed, true)
})

test('workflow state waits for profile index after profile generation', () => {
  const state = getPaperWorkflowState(
    paper({ parseStatus: 'COMPLETED', vectorStatus: 'COMPLETED' }),
    status({ hasProfile: true, jobStatus: 'COMPLETED', currentStep: 'COMPLETED', progressPercent: 100 }),
  )

  assert.equal(state.key, 'pendingProfileIndex')
  assert.equal(state.actionType, 'indexProfile')
  assert.equal(state.ready, false)
})

test('workflow state is ready only when profile and summary index are complete', () => {
  const state = getPaperWorkflowState(
    paper({ parseStatus: 'COMPLETED', vectorStatus: 'COMPLETED' }),
    status({ hasProfile: true, profileIndexComplete: true, sectionSummaryCount: 50, indexedSectionSummaryCount: 50 }),
  )

  assert.equal(state.key, 'ready')
  assert.equal(state.ready, true)
  assert.equal(state.actionLabel, '问答')
})

test('splitPaperRows separates pending and ready papers', () => {
  const papers = [
    paper({ id: 1, parseStatus: 'PENDING', vectorStatus: 'PENDING' }),
    paper({ id: 2, parseStatus: 'COMPLETED', vectorStatus: 'COMPLETED' }),
  ]
  const statuses = [
    status({ paperId: 1, hasProfile: false }),
    status({ paperId: 2, hasProfile: true, profileIndexComplete: true, jobStatus: 'COMPLETED', currentStep: 'COMPLETED', progressPercent: 100 }),
  ]

  const result = splitPaperRows(papers, statuses)

  assert.deepEqual(result.pendingRows.map((row) => row.id), [1])
  assert.deepEqual(result.readyRows.map((row) => row.id), [2])
})
