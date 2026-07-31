import test from 'node:test'
import assert from 'node:assert/strict'

import { buildIdeaAgentCommands, buildPaperAgentCommands } from './commandTemplates.js'

test('paper command template keeps one selected paper ID across all steps', () => {
  const commands = buildPaperAgentCommands(38)
  assert.equal(commands.length, 3)
  assert.ok(commands.every((command) => command.includes('paper-38')))
  assert.ok(commands[0].includes('--mode paper --source-id 38'))
})

test('Idea command template requires a local baseline instead of sending repository details online', () => {
  const commands = buildIdeaAgentCommands(3)
  assert.ok(commands[0].includes('--mode idea --source-id 3'))
  assert.ok(commands[1].includes('--baseline-file'))
  assert.ok(commands[1].includes('repository-baseline.json'))
})
