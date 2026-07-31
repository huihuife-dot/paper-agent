import assert from 'node:assert/strict'
import test from 'node:test'
import { metricDelta, metricPassed, ragEvaluationSummary } from './ragEvaluationSummary.js'

test('final discovery evaluation reaches the fixed stop gates', () => {
  const recall = ragEvaluationSummary.metrics.find((metric) => metric.key === 'discoveryRecall')
  const precision = ragEvaluationSummary.metrics.find((metric) => metric.key === 'discoveryPrecision')

  assert.equal(metricPassed(recall), true)
  assert.equal(metricPassed(precision), true)
  assert.equal(metricDelta(recall), 14.34)
  assert.equal(metricDelta(precision), -1.33)
})

test('evaluation summary keeps the reproducible corpus scope', () => {
  assert.equal(ragEvaluationSummary.corpusSize, 16)
  assert.equal(ragEvaluationSummary.caseCount, 40)
  assert.equal(ragEvaluationSummary.executionSuccessRate, 100)
  assert.equal(ragEvaluationSummary.qualityGates.every((gate) => gate.status === 'passed'), true)
})
