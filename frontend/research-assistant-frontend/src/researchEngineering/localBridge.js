export async function bootstrapPaperWithBridge({ bridgeUrl, token, paperId }, fetcher = fetch) {
  const base = String(bridgeUrl || '').replace(/\/$/, '')
  if (!/^http:\/\/(127\.0\.0\.1|localhost)(:\d+)?$/.test(base)) {
    throw new Error('Bridge URL must be a localhost HTTP address')
  }
  if (token && token.length < 24) throw new Error('Bridge token must contain at least 24 characters')
  const response = await fetcher(`${base}/v1/paper-projects`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...(token ? { 'X-Agent-Bridge-Token': token } : {}) },
    body: JSON.stringify({ paper_id: paperId }),
  })
  const body = await response.json()
  if (!response.ok) {
    throw new Error(body?.error || 'Local Bridge request failed')
  }
  return body
}

export async function runPaperWithBridge(bridgeUrl, paperId, request = '复现当前论文的代码', fetcher = fetch) {
  const base = bridgeUrl.replace(/\/$/, '')
  const response = await fetcher(`${base}/v1/paper-projects/${paperId}/run`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ request }) })
  return response.json()
}

export async function decideBridgeApproval(bridgeUrl, paperId, approved, fetcher = fetch) {
  const response = await fetcher(`${bridgeUrl.replace(/\/$/, '')}/v1/paper-projects/${paperId}/approval`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ approved }) })
  return response.json()
}

export async function selectIdeaWorkspace(bridgeUrl, ideaId) { const base=bridgeUrl.replace(/\/$/, ''); const response = await fetch(`${base}/v1/idea-projects/${ideaId}/select-workspace`, { method:'POST', headers:{'Content-Type':'application/json'}, body:'{}' }); if(!response.ok) throw new Error('无法启动本地文件夹选择窗口'); for(let i=0;i<120;i++){ await new Promise(r=>setTimeout(r,500)); const status=await (await fetch(`${base}/v1/idea-projects/${ideaId}/selection-status`)).json(); if(status.status==='READY') return status.result; if(status.status==='CANCELLED') throw new Error('你取消了文件夹选择') } throw new Error('选择代码目录超时') }
export async function runIdeaWithBridge(bridgeUrl, ideaId) { const response = await fetch(`${bridgeUrl.replace(/\/$/, '')}/v1/idea-projects/${ideaId}/run`, { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({request:'Improve the existing code according to the selected Idea. Inspect first, implement one focused safe change, run a basic check, then provide a handoff.'}) }); return response.json() }
