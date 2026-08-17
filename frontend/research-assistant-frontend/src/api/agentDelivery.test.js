import test from 'node:test'
import assert from 'node:assert/strict'
import { createAgentTaskPackage, prepareExternalGitProject, publishGitProjectToGitee } from './agentDelivery.js'

test('creates a provider-neutral paper task package without running an Agent', async () => {
  let request
  await createAgentTaskPackage('paper', 38, { post: async (url) => { request = url; return { data: { packageId: 'p1' } } } })
  assert.equal(request, '/api/agent-packages/papers/38')
})

test('prepares an external Agent branch before publishing Gitee', async () => {
  let request
  await prepareExternalGitProject('idea', 7, 'E:/idea/demo', {
    post: async (url, body) => { request = { url, body }; return { data: { status: 'WAITING_EXTERNAL_AGENT' } } },
  })
  assert.deepEqual(request, {
    url: '/api/agent-git-projects/idea/7/prepare-external',
    body: { workspacePath: 'E:/idea/demo' },
  })
})

test('publishes only a repository name and description, never a token', async () => {
  let request
  await publishGitProjectToGitee('paper', 38, 'paper-reproduction-38', 'demo', {
    post: async (url, body) => { request = { url, body }; return { data: { remoteStatus: 'PUSHED' } } },
  })
  assert.deepEqual(request, {
    url: '/api/agent-git-projects/paper/38/gitee',
    body: { repositoryName: 'paper-reproduction-38', description: 'demo' },
  })
})
