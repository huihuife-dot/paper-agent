import test from 'node:test'
import assert from 'node:assert/strict'
import { startIdeaAgent, startPaperAgent } from './agentExecutions.js'

test('paper Agent start calls the MyAgent backend instead of localhost bridge', async () => {
  let request
  await startPaperAgent(38, '只做基础复现', {
    post: async (url, body) => { request = { url, body }; return { data: { status: 'RUNNING' } } },
  })
  assert.deepEqual(request, { url: '/api/agent-executions/papers/38', body: { request: '只做基础复现' } })
})

test('idea Agent start sends a server workspace path but never an executable command', async () => {
  let request
  await startIdeaAgent(7, 'E:/AgentIdeaWorkspaces/demo', '改进训练入口', {
    post: async (url, body) => { request = { url, body }; return { data: { status: 'RUNNING' } } },
  })
  assert.deepEqual(request, {
    url: '/api/agent-executions/ideas/7',
    body: { workspacePath: 'E:/AgentIdeaWorkspaces/demo', request: '改进训练入口' },
  })
})
