import apiClient from './client.js'

export function chatWithRag(payload, client = apiClient) {
  return client.post('/api/rag/chat', payload, { timeout: 120000 }).then((result) => result.data ?? result)
}

const apiBaseUrl = import.meta.env?.VITE_API_BASE_URL || 'http://localhost:8080'

export function parseSseEventBlock(block) {
  const lines = String(block || '').split(/\r?\n/)
  const event = lines.find((line) => line.startsWith('event:'))?.slice('event:'.length).trim() || 'message'
  const dataText = lines
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice('data:'.length).trimStart())
    .join('\n')
  if (!dataText) return null

  return { event, data: JSON.parse(dataText) }
}

/**
 * POST 请求不能使用浏览器 EventSource，因此通过 fetch 读取响应流并解析 SSE 帧。
 */
export async function chatWithRagStream(payload, handlers = {}, fetchImpl = globalThis.fetch) {
  const response = await fetchImpl(`${apiBaseUrl}/api/rag/chat/stream`, {
    method: 'POST',
    headers: {
      Accept: 'text/event-stream',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
    signal: handlers.signal,
  })

  if (!response.ok) {
    throw new Error((await response.text()) || `HTTP ${response.status}`)
  }
  if (!response.body) {
    throw new Error('浏览器未提供流式响应内容')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let completedResponse = null

  const dispatch = (block) => {
    const parsed = parseSseEventBlock(block)
    if (!parsed) return
    handlers.onEvent?.(parsed.event, parsed.data)
    if (parsed.event === 'phase') handlers.onPhase?.(parsed.data)
    if (parsed.event === 'metadata') handlers.onMetadata?.(parsed.data)
    if (parsed.event === 'delta') handlers.onDelta?.(parsed.data?.content || '')
    if (parsed.event === 'complete') completedResponse = parsed.data
    if (parsed.event === 'error') throw new Error(parsed.data?.message || '流式回答生成失败')
  }

  while (true) {
    const { value, done } = await reader.read()
    buffer += decoder.decode(value || new Uint8Array(), { stream: !done })

    let boundary = buffer.match(/\r?\n\r?\n/)
    while (boundary?.index !== undefined) {
      dispatch(buffer.slice(0, boundary.index))
      buffer = buffer.slice(boundary.index + boundary[0].length)
      boundary = buffer.match(/\r?\n\r?\n/)
    }
    if (done) break
  }

  if (buffer.trim()) dispatch(buffer)
  if (!completedResponse) {
    throw new Error('流式连接结束，但未收到完整回答')
  }
  return completedResponse
}
