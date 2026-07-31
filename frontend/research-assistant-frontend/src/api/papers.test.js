import test from 'node:test'
import assert from 'node:assert/strict'

import {
  listPapers,
  uploadPaper,
  parsePaper,
  vectorizePaper,
  deletePaper,
  updatePaperCategoryOfPaper,
  updatePaperMetadata,
  getPaperContentUrl,
  getPaperProfile,
  generatePaperProfile,
  startPaperProfileJob,
  getPaperProfileJob,
  listPaperProfileStatuses,
  indexPaperProfile,
  getPaperProfileIndexStatus,
  analyzePaperAsset,
  analyzeEligiblePaperAssets,
  getPaperAssetAnalysisStatus,
  rebuildPaperReproductionFacts,
  listPaperReproductionFacts,
  searchPaperReproductionFacts,
  getPaperReproductionSpec,
  rebuildPaperReproductionSpec,
  getPaperReproductionContext,
} from './papers.js'

function createFakeClient() {
  const calls = []

  return {
    calls,
    get(path) {
      calls.push({ method: 'get', path })
      return Promise.resolve({ data: [] })
    },
    post(path, body, config) {
      calls.push({ method: 'post', path, body, config })
      return Promise.resolve({ data: body ?? null })
    },
    patch(path, body) {
      calls.push({ method: 'patch', path, body })
      return Promise.resolve({ data: body ?? null })
    },
    delete(path) {
      calls.push({ method: 'delete', path })
      return Promise.resolve({ data: null })
    },
  }
}

test('listPapers calls the paper list endpoint', async () => {
  const client = createFakeClient()

  const result = await listPapers(client)

  assert.deepEqual(result, [])
  assert.deepEqual(client.calls, [{ method: 'get', path: '/api/papers' }])
})

test('uploadPaper posts form data to the upload endpoint', async () => {
  const client = createFakeClient()
  const formData = { file: 'paper.pdf' }

  const result = await uploadPaper(formData, client)

  assert.deepEqual(result, formData)
  assert.deepEqual(client.calls, [
    { method: 'post', path: '/api/papers/upload', body: formData, config: undefined },
  ])
})

test('listPapers can filter by category id', async () => {
  const client = createFakeClient()

  await listPapers({ categoryId: 2 }, client)

  assert.deepEqual(client.calls, [{ method: 'get', path: '/api/papers?categoryId=2' }])
})

test('parsePaper posts to the parse endpoint for one paper', async () => {
  const client = createFakeClient()

  await parsePaper(12, client)

  assert.deepEqual(client.calls, [
    { method: 'post', path: '/api/papers/12/parse', body: undefined, config: undefined },
  ])
})

test('vectorizePaper posts to the vectorize endpoint for one paper', async () => {
  const client = createFakeClient()

  await vectorizePaper(12, client)

  assert.deepEqual(client.calls, [
    { method: 'post', path: '/api/papers/12/vectorize', body: undefined, config: undefined },
  ])
})

test('deletePaper deletes one paper', async () => {
  const client = createFakeClient()

  await deletePaper(12, client)

  assert.deepEqual(client.calls, [{ method: 'delete', path: '/api/papers/12' }])
})

test('updatePaperCategoryOfPaper patches the category endpoint', async () => {
  const client = createFakeClient()

  const result = await updatePaperCategoryOfPaper(12, 2, client)

  assert.deepEqual(result, { categoryId: 2 })
  assert.deepEqual(client.calls, [
    { method: 'patch', path: '/api/papers/12/category', body: { categoryId: 2 } },
  ])
})

test('updatePaperMetadata patches editable bibliography fields', async () => {
  const client = createFakeClient()
  const metadata = { title: 'Updated paper', publishYear: 2025, authors: 'Zhang' }

  const result = await updatePaperMetadata(12, metadata, client)

  assert.deepEqual(result, metadata)
  assert.deepEqual(client.calls, [
    { method: 'patch', path: '/api/papers/12', body: metadata },
  ])
})

test('getPaperContentUrl builds the inline PDF endpoint URL', () => {
  assert.equal(
    getPaperContentUrl(12, 'http://localhost:8080/'),
    'http://localhost:8080/api/papers/12/content',
  )
})

test('getPaperProfile calls the paper profile query endpoint', async () => {
  const client = createFakeClient()

  await getPaperProfile(12, client)

  assert.deepEqual(client.calls, [{ method: 'get', path: '/api/papers/12/profile' }])
})

test('generatePaperProfile posts to the paper profile generation endpoint', async () => {
  const client = createFakeClient()

  await generatePaperProfile(12, client)

  assert.deepEqual(client.calls, [
    { method: 'post', path: '/api/papers/12/profile', body: undefined, config: { timeout: 180000 } },
  ])
})

test('startPaperProfileJob posts to the async profile generation endpoint', async () => {
  const client = createFakeClient()

  await startPaperProfileJob(12, client)

  assert.deepEqual(client.calls, [
    { method: 'post', path: '/api/papers/12/profile/async', body: undefined, config: undefined },
  ])
})

test('getPaperProfileJob calls the profile job status endpoint', async () => {
  const client = createFakeClient()

  await getPaperProfileJob(12, client)

  assert.deepEqual(client.calls, [{ method: 'get', path: '/api/papers/12/profile/job' }])
})

test('listPaperProfileStatuses calls the batch profile status endpoint', async () => {
  const client = createFakeClient()

  await listPaperProfileStatuses(client)

  assert.deepEqual(client.calls, [{ method: 'get', path: '/api/papers/profile-status' }])
})

test('indexPaperProfile posts to the explicit profile index endpoint', async () => {
  const client = createFakeClient()
  await indexPaperProfile(13, client)
  assert.deepEqual(client.calls, [
    { method: 'post', path: '/api/papers/13/profile/index', body: undefined, config: { timeout: 300000 } },
  ])
})

test('getPaperProfileIndexStatus queries one paper index status', async () => {
  const client = createFakeClient()
  await getPaperProfileIndexStatus(13, client)
  assert.deepEqual(client.calls, [{ method: 'get', path: '/api/papers/13/profile/index/status' }])
})

test('vision asset APIs call explicit single, gated batch and status endpoints', async () => {
  const client = createFakeClient()
  await analyzePaperAsset(38, 9, client)
  await analyzeEligiblePaperAssets(38, client)
  await getPaperAssetAnalysisStatus(38, client)
  assert.deepEqual(client.calls, [
    { method: 'post', path: '/api/papers/38/assets/9/analyze', body: undefined, config: { timeout: 120000 } },
    { method: 'post', path: '/api/papers/38/assets/analyze', body: undefined, config: { timeout: 600000 } },
    { method: 'get', path: '/api/papers/38/assets/status' },
  ])
})

test('reproduction fact APIs keep rebuild, list and explicit search separate', async () => {
  const client = createFakeClient()
  await rebuildPaperReproductionFacts(38, client)
  await listPaperReproductionFacts(38, true, client)
  await searchPaperReproductionFacts(38, 'learning rate & batch size', false, client)
  assert.deepEqual(client.calls, [
    { method: 'post', path: '/api/papers/38/reproduction-facts/rebuild', body: undefined, config: { timeout: 300000 } },
    { method: 'get', path: '/api/papers/38/reproduction-facts?includeModelInferred=true' },
    { method: 'get', path: '/api/papers/38/reproduction-facts/search?query=learning%20rate%20%26%20batch%20size&topK=20&includeModelInferred=false' },
  ])
})

test('reproduction spec and context APIs expose explicit rebuild and protocol version', async () => {
  const client = createFakeClient()
  await getPaperReproductionSpec(38, client)
  await rebuildPaperReproductionSpec(38, client)
  await getPaperReproductionContext(38, 1, client)
  assert.deepEqual(client.calls, [
    { method: 'get', path: '/api/papers/38/reproduction-spec' },
    { method: 'post', path: '/api/papers/38/reproduction-spec/rebuild', body: undefined, config: { timeout: 300000 } },
    { method: 'get', path: '/api/papers/38/reproduction-context?version=1' },
  ])
})
