function positiveId(value, name) {
  const numeric = Number(value)
  if (!Number.isInteger(numeric) || numeric <= 0) {
    throw new Error(`${name} must be a positive integer`)
  }
  return numeric
}

export function buildPaperAgentCommands(paperId) {
  const id = positiveId(paperId, 'paperId')
  const workspace = `E:\\AgentWorkspaces\\paper-${id}`
  return [
    `research-engineering init --workspace "${workspace}" --mode paper --source-id ${id}`,
    `research-engineering fetch-context --workspace "${workspace}" --server-url "http://localhost:8080"`,
    `research-engineering deliver --workspace "${workspace}" --request "Read the selected paper context, reproduce the code in this isolated workspace, and ask before each write or basic verification command." --env-file ".deepseek.env"`,
  ]
}

export function buildIdeaAgentCommands(ideaId) {
  const id = positiveId(ideaId, 'ideaId')
  const workspace = `E:\\AgentWorkspaces\\idea-${id}`
  return [
    `research-engineering init --workspace "${workspace}" --mode idea --source-id ${id}`,
    `research-engineering fetch-context --workspace "${workspace}" --server-url "http://localhost:8080" --baseline-file "${workspace}\\repository-baseline.json"`,
    `research-engineering deliver --workspace "${workspace}" --request "Read the selected Idea and repository baseline, improve only allowed paths, and ask before each write or basic verification command." --env-file ".deepseek.env"`,
  ]
}
