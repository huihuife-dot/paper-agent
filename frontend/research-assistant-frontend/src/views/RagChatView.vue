<template>
  <section class="page-panel chat-page">
    <div class="writing-page-head">
      <div class="research-mode-switch" aria-label="论文工作模式">
        <RouterLink to="/chat">论文问答</RouterLink>
        <RouterLink to="/writing">论文写作</RouterLink>
      </div>
    </div>
    <div class="page-toolbar">
      <div>
        <p class="eyebrow">Chat</p>
        <h1>论文问答</h1>
        <p>从左侧历史对话继续追问；参考论文和引用片段按需打开，不占用回答空间。</p>
      </div>
      <div class="console-toolbar-actions">
        <span class="chat-session-status">{{ sessionId ? `Session #${sessionId}` : '新会话' }}</span>
        <el-button class="mobile-history-trigger" text @click="historyDrawerVisible = true">历史对话</el-button>
        <el-button text @click="openContextPanel('papers')">参考论文</el-button>
        <el-button text :disabled="!hasSources" @click="openContextPanel('sources')">引用片段 {{ sources.length }}</el-button>
      </div>
    </div>

    <div class="chat-workspace-grid">
      <aside class="chat-history-rail" aria-label="历史对话">
        <div class="chat-history-heading">
          <strong>历史对话</strong>
          <div>
            <el-button text size="small" :loading="sessionsLoading" @click="loadSessions">刷新</el-button>
            <el-button text size="small" type="primary" @click="startNewSession">＋ 新建</el-button>
          </div>
        </div>
        <div v-loading="sessionsLoading" class="chat-history-list scroll-clean">
          <span v-if="sessions.length === 0 && !sessionsLoading" class="empty-session-hint">暂无历史对话</span>
          <div
            v-for="session in sessions"
            :key="session.id"
            class="chat-history-item"
            :class="{ active: selectedSession?.id === session.id }"
          >
            <button class="chat-session-select" type="button" @click="selectSession(session)">
              <span class="chat-session-title">{{ session.title || `Session #${session.id}` }}</span>
              <span class="chat-session-meta">{{ formatDateTime(session.updateTime || session.createTime) }}</span>
            </button>
            <el-button
              class="chat-session-delete"
              type="danger"
              text
              size="small"
              :loading="deletingSessionId === session.id"
              @click.stop="handleDeleteSession(session)"
            >删除</el-button>
          </div>
        </div>
      </aside>

      <main class="chat-main-panel">
        <div class="chat-panel-header">
          <span>{{ selectedSession ? selectedSession.title || `Session #${selectedSession.id}` : '新会话' }}</span>
          <div class="chat-toolbar-actions">
            <span class="chat-scope-label">{{ selectedPaperSummary }}</span>
            <el-button type="primary" text size="small" :disabled="!sessionId" :loading="savingIdea" @click="handleSaveIdea">
              保存想法
            </el-button>
          </div>
        </div>

        <div ref="messageScrollRef" v-loading="messagesLoading" class="chat-message-scroll scroll-clean">
          <el-empty v-if="!sessionId && messages.length === 0" description="新建会话后可直接提问；不选参考论文将进行全库检索。" />
          <el-empty v-else-if="messages.length === 0 && !messagesLoading" description="这个会话还没有消息。" />

          <article
            v-for="message in messages"
            :key="message.id"
            class="message history-message"
            :class="message.role === 'assistant' ? 'answer-message' : 'question-message'"
          >
            <div class="answer-header">
              <strong>{{ formatMessageRole(message.role) }}</strong>
              <el-tag v-if="message.modelProvider || message.modelName" type="info" effect="plain">
                {{ formatModel(message) }}
              </el-tag>
            </div>
            <p>{{ message.content || '暂无内容' }}</p>
            <small>{{ formatDateTime(message.createTime) }}</small>

            <el-alert
              v-if="message.role === 'assistant' && suggestSaveAsIdea && message.id === messages[messages.length - 1]?.id"
              class="idea-suggestion"
              type="success"
              :closable="false"
              show-icon
              title="发现可保存的研究想法"
            >
              <div class="idea-suggestion-actions">
                <p>{{ ideaSuggestionReason || '这轮讨论包含可继续推进的研究线索。' }}</p>
                <el-button type="success" plain size="small" :loading="savingIdea" @click="handleSaveIdea">保存为想法</el-button>
              </div>
            </el-alert>
          </article>

          <article v-if="pendingQuestion" class="message history-message question-message streaming-message">
            <div class="answer-header"><strong>你</strong></div>
            <p>{{ pendingQuestion }}</p>
          </article>

          <article v-if="pendingQuestion" class="message history-message answer-message streaming-message">
            <div class="answer-header">
              <strong>AI 助手</strong>
              <el-tag type="success" effect="plain">{{ streamPhaseLabel }}</el-tag>
            </div>
            <p v-if="streamingAnswer" class="streaming-answer">{{ streamingAnswer }}<span class="streaming-caret"></span></p>
            <el-skeleton v-else :rows="3" animated />
            <el-alert
              v-if="streamError"
              type="error"
              :closable="false"
              show-icon
              :title="streamError"
            />
          </article>

          <el-alert
            v-if="savedIdea"
            class="idea-suggestion"
            type="info"
            :closable="false"
            show-icon
            title="已保存为研究想法草稿"
          >
            <p>{{ savedIdea.title || `Idea #${savedIdea.id}` }}</p>
          </el-alert>

          <div v-if="paperRelevance.length > 0" class="paper-relevance-panel">
            <h3>最相关的论文</h3>
            <ul class="paper-relevance-list">
              <li v-for="pr in paperRelevance" :key="pr.paperId" class="paper-relevance-item">
                <strong>{{ pr.paperTitle || '未知论文' }}</strong>
                <span class="paper-relevance-stats">
                  命中 {{ pr.hitCount }} 片段 · 最高相关度 {{ formatScore(pr.maxScore) }}
                </span>
                <div v-if="pr.topChunks && pr.topChunks.length > 0" class="paper-relevance-chunks">
                  <p v-for="chunk in pr.topChunks" :key="chunk.chunkId">
                    "{{ (chunk.content || '').slice(0, 200) }}{{ (chunk.content || '').length > 200 ? '...' : '' }}"
                  </p>
                </div>
              </li>
            </ul>
          </div>

          <el-collapse v-if="timing" class="execution-details-panel">
            <el-collapse-item name="timing">
              <template #title>
                <span class="execution-details-title">
                  执行详情 · {{ contextStrategy || 'RAG' }} · {{ formatTimingDuration(timing.totalMs) }}
                </span>
              </template>
              <div class="execution-details-summary">
                <span>引用片段 {{ sourceCount ?? sources.length }}</span>
                <span>上下文约 {{ contextTokenCount }} tokens</span>
                <span v-if="timing.firstContentMs != null">首段等待 {{ formatTimingDuration(timing.firstContentMs) }}</span>
                <span v-if="dominantTimingRow">主要耗时：{{ dominantTimingRow.label }}</span>
              </div>
              <div v-if="evidencePlan" class="evidence-plan-detail">
                <div class="evidence-plan-tags">
                  <el-tag size="small" effect="plain">{{ formatEvidenceScope(evidencePlan.scope) }}</el-tag>
                  <el-tag size="small" effect="plain">{{ formatEvidenceIntent(evidencePlan.intent) }}</el-tag>
                  <el-tag size="small" type="success" effect="plain">
                    主资料：{{ formatEvidenceLayer(evidencePlan.primaryLayer) }}
                  </el-tag>
                  <el-tag v-if="evidencePlan.ragUsed" size="small" type="warning" effect="plain">RAG 仅作补漏</el-tag>
                </div>
                <p>{{ evidencePlan.explanation }}</p>
                <small>
                  结构化来源 {{ evidencePlan.structuredSourceCount || 0 }} 条
                  <template v-if="evidencePlan.ragUsed">
                    · RAG 补充 {{ evidencePlan.ragSourceCount || 0 }} 条
                    <template v-if="evidencePlan.ragReason">（{{ evidencePlan.ragReason }}）</template>
                  </template>
                </small>
              </div>
              <div v-if="wasHistoryRewritten" class="retrieval-question-detail">
                <span><strong>原始追问：</strong>{{ lastOriginalQuestion }}</span>
                <span><strong>实际检索：</strong>{{ retrievalQuestion }}</span>
              </div>
              <div class="timing-stage-list">
                <div v-for="row in timingRows" :key="row.key" class="timing-stage-item">
                  <div class="timing-stage-heading">
                    <span>{{ row.label }}</span>
                    <strong>{{ formatTimingDuration(row.milliseconds) }} · {{ row.percent }}%</strong>
                  </div>
                  <el-progress :percentage="row.percent" :show-text="false" :stroke-width="8" />
                </div>
              </div>
            </el-collapse-item>
          </el-collapse>
        </div>

        <div class="chat-composer">
          <el-form label-position="top" class="chat-form compact-form">
            <el-form-item label="继续提问">
              <el-input
                v-model="question"
                type="textarea"
                :rows="3"
                placeholder="基于当前会话继续提问，例如：这个方法还能怎么改进？"
              />
            </el-form-item>

            <div class="chat-composer-actions">
              <el-form-item label="引用数量">
                <el-input-number v-model="topK" :min="1" :max="10" />
              </el-form-item>
              <el-button type="primary" :loading="asking" @click="handleAsk">发送</el-button>
            </div>
          </el-form>
        </div>
      </main>

    </div>

    <el-drawer v-model="historyDrawerVisible" class="chat-history-drawer" direction="ltr" size="min(86vw, 320px)" title="历史对话">
      <div class="chat-drawer-actions">
        <el-button text :loading="sessionsLoading" @click="loadSessions">刷新</el-button>
        <el-button text type="primary" @click="startNewSession(); historyDrawerVisible = false">＋ 新建会话</el-button>
      </div>
      <div v-loading="sessionsLoading" class="chat-history-list drawer-list scroll-clean">
        <span v-if="sessions.length === 0 && !sessionsLoading" class="empty-session-hint">暂无历史对话</span>
        <div
          v-for="session in sessions"
          :key="session.id"
          class="chat-history-item"
          :class="{ active: selectedSession?.id === session.id }"
        >
          <button class="chat-session-select" type="button" @click="selectSession(session); historyDrawerVisible = false">
            <span class="chat-session-title">{{ session.title || `Session #${session.id}` }}</span>
            <span class="chat-session-meta">{{ formatDateTime(session.updateTime || session.createTime) }}</span>
          </button>
          <el-button
            class="chat-session-delete"
            type="danger"
            text
            size="small"
            :loading="deletingSessionId === session.id"
            @click.stop="handleDeleteSession(session)"
          >删除</el-button>
        </div>
      </div>
    </el-drawer>

    <el-dialog
      v-model="contextPanelOpen"
      class="chat-context-dialog"
      :title="contextPanelMode === 'papers' ? '参考论文' : '引用片段'"
      width="760px"
      destroy-on-close
    >
        <div class="chat-context-header">
          <span>选择需要查看的内容</span>
          <div class="context-toggle-row">
            <el-button size="small" plain @click="contextPanelMode = 'papers'">论文</el-button>
            <el-button size="small" plain :disabled="!hasSources" @click="contextPanelMode = 'sources'">引用</el-button>
            <el-button size="small" text @click="closeContextPanel">关闭</el-button>
          </div>
        </div>

        <div class="chat-context-body scroll-clean">
          <template v-if="contextPanelMode === 'papers'">
            <p class="selected-paper-summary">{{ selectedPaperSummary }}</p>
            <el-alert
              v-if="selectedPaperIds.length === 0"
              type="info"
              :closable="false"
              show-icon
              title="全库检索模式"
              class="library-mode-hint"
            >
              <p>当前未选择任何参考论文，系统将从所有文献中检索相关内容。</p>
              <p>回答下方会显示最相关的论文及其依据片段。</p>
            </el-alert>
            <el-alert
              v-if="shouldShowHybridProfileHint"
              class="hybrid-profile-hint"
              type="warning"
              :closable="false"
              show-icon
              title="多篇比较依赖文献画像"
            >
              <div class="hybrid-profile-hint-body">
                <p>画像齐全时会启用 HYBRID_RAG；如果回答仍像零散片段，请先到文献页为选中的论文生成画像。</p>
                <el-button size="small" plain type="warning" @click="router.push('/papers')">去文献页生成画像</el-button>
              </div>
            </el-alert>
            <div v-loading="papersLoading" class="reference-paper-list">
              <el-empty v-if="papers.length === 0 && !papersLoading" description="暂无可选文献，请先上传并向量化论文。" />
              <template v-else>
                <button
                  v-for="paper in papers"
                  :key="paper.id"
                  class="reference-paper-item"
                  :class="{ active: isReferencePaperSelected(paper) }"
                  type="button"
                  @click="handlePaperToggle(paper)"
                >
                  <span class="reference-paper-check" aria-hidden="true"></span>
                  <span>
                    <strong class="reference-paper-title">{{ paper.title || paper.fileName || `Paper #${paper.id}` }}</strong>
                    <small class="reference-paper-meta">{{ formatReferencePaperMeta(paper) }}</small>
                  </span>
                </button>
              </template>
            </div>
          </template>

          <template v-else>
            <div class="drawer-source-list">
              <el-empty v-if="sources.length === 0" description="发送问题后，这里会显示本轮引用片段。" />
              <template v-else>
                <div v-for="(source, index) in sources" :key="sourceKey(source, index)" class="source-item">
                  <div class="source-title-row">
                    <span class="source-route">{{ source.retrievalRoute || 'retrieved' }}</span>
                    <div class="source-tag-row">
                      <el-tag v-if="source.confidenceLevel" size="small" type="success" effect="plain">
                        {{ source.confidenceLevel }}
                      </el-tag>
                      <el-tag size="small" effect="plain">score {{ formatScore(source.score) }}</el-tag>
                    </div>
                  </div>
                  <strong>{{ source.paperTitle || `Paper #${source.paperId}` }}</strong>
                  <small>{{ sourceLocator(source) }}</small>
                  <p>{{ source.content || '该引用片段暂未返回文本内容。' }}</p>
                </div>
              </template>
            </div>
          </template>
        </div>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { chatWithRagStream } from '../api/rag.js'
import { deleteChatSession, listChatMessages, listChatSessions } from '../api/chatHistory.js'
import { saveDraftFromSession } from '../api/researchIdeas.js'
import { listPapers } from '../api/papers.js'
import {
  buildRagChatPayload,
  buildTimingRows,
  formatPaperRelevanceSummary,
  formatSelectedPaperSummary,
  formatTimingDuration,
  isPaperSelected,
  isRetrievalQuestionRewritten,
  normalizePaperRows,
  parsePaperId,
  togglePaperSelection,
} from './ragChatState.js'

const route = useRoute()
const router = useRouter()

const question = ref('')
const topK = ref(5)
const asking = ref(false)
const savingIdea = ref(false)
const historyDrawerVisible = ref(false)

const sessions = ref([])
const messages = ref([])
const sessionsLoading = ref(false)
const messagesLoading = ref(false)
const deletingSessionId = ref(null)
const selectedSession = ref(null)
const messageScrollRef = ref(null)

const sources = ref([])
const sessionId = ref(parseSessionId(route.query.sessionId))
const retrievalQuestion = ref('')
const lastOriginalQuestion = ref('')
const modelProvider = ref('')
const modelName = ref('')
const sourceCount = ref(null)
const suggestSaveAsIdea = ref(false)
const ideaSuggestionReason = ref('')
const savedIdea = ref(null)
const paperRelevance = ref([])
const pendingQuestion = ref('')
const streamingAnswer = ref('')
const streamPhase = ref('idle')
const streamError = ref('')
const timing = ref(null)
const contextStrategy = ref('')
const contextTokenCount = ref(0)
const evidencePlan = ref(null)

const papers = ref([])
const papersLoading = ref(false)
const initialPaperId = parsePaperId(route.query.paperId)
const selectedPaperIds = ref(initialPaperId ? [initialPaperId] : [])
const contextPanelOpen = ref(false)
const contextPanelMode = ref('papers')

const selectedPaperSummary = computed(() => formatSelectedPaperSummary(papers.value, selectedPaperIds.value))
const hasSources = computed(() => sources.value.length > 0)
const shouldShowHybridProfileHint = computed(() => selectedPaperIds.value.length >= 2)
const timingRows = computed(() => buildTimingRows(timing.value))
const dominantTimingRow = computed(() => timingRows.value.reduce((dominant, row) => (
  !dominant || row.milliseconds > dominant.milliseconds ? row : dominant
), null))
const streamPhaseLabel = computed(() => ({
  retrieving: '正在检索资料',
  generating: '正在生成回答',
  completed: '生成完成',
  error: '生成中断',
}[streamPhase.value] || '准备中'))
const wasHistoryRewritten = computed(() => isRetrievalQuestionRewritten(
  lastOriginalQuestion.value,
  retrievalQuestion.value,
))

function scrollMessagesToBottom() {
  nextTick(() => {
    const container = messageScrollRef.value
    if (container) container.scrollTop = container.scrollHeight
  })
}

async function loadSessions(options = {}) {
  sessionsLoading.value = true

  try {
    sessions.value = await listChatSessions()
    const routeSessionId = parseSessionId(route.query.sessionId)
    const routePaperId = parsePaperId(route.query.paperId)

    if (routeSessionId) {
      const preferredSession = sessions.value.find((session) => session.id === routeSessionId) || { id: routeSessionId }
      await selectSession(preferredSession, {
        updateRoute: false,
        preserveExecutionDetails: options.preserveExecutionDetails === true,
      })
      return
    }

    if (!routePaperId && !selectedSession.value && sessions.value.length > 0) {
      await selectSession(sessions.value[0], {
        updateRoute: false,
        preserveExecutionDetails: options.preserveExecutionDetails === true,
      })
    }
  } catch (error) {
    ElMessage.error(`会话列表加载失败：${error.message}`)
  } finally {
    sessionsLoading.value = false
  }
}

async function selectSession(session, options = {}) {
  selectedSession.value = session
  sessionId.value = session?.id || null

  if (options.preserveExecutionDetails !== true) {
    sources.value = []
    paperRelevance.value = []
    timing.value = null
    contextStrategy.value = ''
    contextTokenCount.value = 0
    evidencePlan.value = null
    retrievalQuestion.value = ''
    lastOriginalQuestion.value = ''
  }

  if (options.updateRoute !== false && session?.id) {
    router.replace({ path: '/chat', query: { sessionId: session.id } })
  }

  if (!session?.id) {
    messages.value = []
    return
  }

  await loadMessages(session.id)
}

async function loadMessages(nextSessionId = sessionId.value) {
  if (!nextSessionId) {
    messages.value = []
    return
  }

  messagesLoading.value = true

  try {
    messages.value = await listChatMessages(nextSessionId)
  } catch (error) {
    ElMessage.error(`会话消息加载失败：${error.message}`)
  } finally {
    messagesLoading.value = false
  }
}

function parseSessionId(value) {
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null
}

watch(
  () => route.query.sessionId,
  async (value) => {
    const nextSessionId = parseSessionId(value)
    if (!nextSessionId || nextSessionId === sessionId.value) {
      return
    }

    const session = sessions.value.find((item) => item.id === nextSessionId) || { id: nextSessionId }
    await selectSession(session, { updateRoute: false })
  },
)

async function handleAsk() {
  const trimmedQuestion = question.value.trim()

  if (!trimmedQuestion) {
    ElMessage.warning('请输入问题')
    return
  }

  asking.value = true
  savedIdea.value = null
  pendingQuestion.value = trimmedQuestion
  lastOriginalQuestion.value = trimmedQuestion
  streamingAnswer.value = ''
  streamPhase.value = 'retrieving'
  streamError.value = ''
  timing.value = null
  paperRelevance.value = []
  scrollMessagesToBottom()

  try {
    const response = await chatWithRagStream(
      buildRagChatPayload({
        question: trimmedQuestion,
        topK: topK.value,
        sessionId: sessionId.value,
        selectedPaperIds: selectedPaperIds.value,
      }),
      {
        onPhase: ({ phase }) => {
          streamPhase.value = phase || streamPhase.value
        },
        onMetadata: (metadata) => {
          streamPhase.value = 'generating'
          sources.value = metadata.sources || []
          contextStrategy.value = metadata.contextStrategy || ''
          contextTokenCount.value = metadata.contextTokenCount ?? 0
          evidencePlan.value = metadata.evidencePlan || null
          modelProvider.value = metadata.modelProvider || ''
          modelName.value = metadata.modelName || ''
          sourceCount.value = sources.value.length
          retrievalQuestion.value = metadata.retrievalQuestion || trimmedQuestion
        },
        onDelta: (content) => {
          streamPhase.value = 'generating'
          streamingAnswer.value += content
          scrollMessagesToBottom()
        },
      },
    )

    sources.value = response.sources || []
    if (sources.value.length > 0) {
      contextPanelMode.value = 'sources'
    }
    sessionId.value = response.sessionId || sessionId.value
    retrievalQuestion.value = response.retrievalQuestion || response.question || trimmedQuestion
    lastOriginalQuestion.value = response.question || trimmedQuestion
    modelProvider.value = response.modelProvider || ''
    modelName.value = response.modelName || ''
    sourceCount.value = response.sourceCount ?? sources.value.length
    suggestSaveAsIdea.value = Boolean(response.suggestSaveAsIdea)
    ideaSuggestionReason.value = response.ideaSuggestionReason || ''
    paperRelevance.value = response.paperRelevance || []
    timing.value = response.timing || null
    contextStrategy.value = response.contextStrategy || ''
    contextTokenCount.value = response.contextTokenCount ?? 0
    evidencePlan.value = response.evidencePlan || null
    question.value = ''

    await loadSessions({ preserveExecutionDetails: true })

    if (sessionId.value) {
      const current = sessions.value.find((session) => session.id === sessionId.value) || selectedSession.value || { id: sessionId.value }
      await selectSession(current, { preserveExecutionDetails: true })
    }

    streamPhase.value = 'completed'
    pendingQuestion.value = ''
    streamingAnswer.value = ''

    ElMessage.success('回答已生成')
  } catch (error) {
    streamPhase.value = 'error'
    streamError.value = error.message
    ElMessage.error(`发送失败：${error.message}`)
  } finally {
    asking.value = false
  }
}

async function handleSaveIdea() {
  if (!sessionId.value) {
    ElMessage.warning('请先完成一次论文问答')
    return
  }

  savingIdea.value = true

  try {
    savedIdea.value = await saveDraftFromSession(sessionId.value)
    ElMessage.success('已保存为研究想法草稿')
  } catch (error) {
    ElMessage.error(`保存失败：${error.message}`)
  } finally {
    savingIdea.value = false
  }
}

async function handleDeleteSession(session) {
  if (!session?.id) {
    return
  }

  try {
    await ElMessageBox.confirm(
      `确定删除会话“${session.title || `Session #${session.id}`}”及其消息吗？已保存的 Research Idea 不会被删除。`,
      '删除会话',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    return
  }

  deletingSessionId.value = session.id

  try {
    await deleteChatSession(session.id)
    ElMessage.success('会话已删除')

    const deletedCurrentSession = sessionId.value === session.id
    sessions.value = sessions.value.filter((item) => item.id !== session.id)

    if (deletedCurrentSession) {
      const nextSession = sessions.value[0] || null
      if (nextSession) {
        await selectSession(nextSession)
      } else {
        startNewSession()
        closeContextPanel()
      }
    } else {
      await loadSessions()
    }
  } catch (error) {
    ElMessage.error(`删除失败：${error.message}`)
  } finally {
    deletingSessionId.value = null
  }
}

async function loadPapersForContext() {
  papersLoading.value = true

  try {
    const result = await listPapers()
    papers.value = normalizePaperRows(result)
  } catch (error) {
    ElMessage.error(`参考论文加载失败：${error.message}`)
  } finally {
    papersLoading.value = false
  }
}

function startNewSession() {
  selectedSession.value = null
  sessionId.value = null
  messages.value = []
  sources.value = []
  paperRelevance.value = []
  timing.value = null
  contextStrategy.value = ''
  contextTokenCount.value = 0
  evidencePlan.value = null
  retrievalQuestion.value = ''
  lastOriginalQuestion.value = ''
  pendingQuestion.value = ''
  streamingAnswer.value = ''
  streamPhase.value = 'idle'
  streamError.value = ''
  savedIdea.value = null
  suggestSaveAsIdea.value = false
  ideaSuggestionReason.value = ''
  question.value = ''
  router.replace({ path: '/chat' })
  openContextPanel('papers')
}

function openContextPanel(mode = 'papers') {
  contextPanelMode.value = mode
  contextPanelOpen.value = true

  if (mode === 'papers' && papers.value.length === 0) {
    loadPapersForContext()
  }
}

function closeContextPanel() {
  contextPanelOpen.value = false
}

function handlePaperToggle(paper) {
  selectedPaperIds.value = togglePaperSelection(selectedPaperIds.value, paper.id)
}

function isReferencePaperSelected(paper) {
  return isPaperSelected(selectedPaperIds.value, paper.id)
}

function formatReferencePaperMeta(paper) {
  return [paper.categoryName || '未分类', paper.authors || '未知作者', paper.publishYear || '未知年份'].join(' · ')
}

function formatScore(score) {
  if (score === undefined || score === null) {
    return '-'
  }

  return Number(score).toFixed(4)
}

function formatEvidenceScope(scope) {
  return ({ SINGLE: '单篇论文', MULTI: '多篇对比', LIBRARY: '全库发现' })[scope] || scope || '自动范围'
}

function formatEvidenceIntent(intent) {
  return ({
    TASK: '研究任务',
    METHOD: '方法',
    DATASET: '数据集',
    SETTING: '实验设置',
    METRIC: '指标',
    RESULT: '实验结果',
    CONTRIBUTION: '贡献',
    LIMITATION: '局限',
    OVERVIEW: '概览',
    FUZZY: '开放问题',
  })[intent] || intent || '综合问题'
}

function formatEvidenceLayer(layer) {
  return ({
    CATALOG: '论文目录',
    PROFILE: '论文画像',
    KNOWLEDGE: '知识单元',
    KNOWLEDGE_UNIT: '知识单元',
    SECTION: '章节摘要',
    SECTION_SUMMARY: '章节摘要',
    FULL_TEXT: '全文',
  })[layer]
    || layer
    || '自动选择'
}

function sourceKey(source, index) {
  return [source.sourceType, source.paperId, source.knowledgeUnitId, source.profileId,
    source.sectionSummaryId, source.chunkId, index].filter((value) => value !== undefined && value !== null).join('-')
}

function sourceLocator(source) {
  const page = source.pageNumber ? ` · PDF 第 ${source.pageNumber} 页` : ''

  if (source.knowledgeUnitId) {
    return `结构化知识 #${source.knowledgeUnitId}${page}`
  }
  if (source.profileId) {
    return `论文画像 #${source.profileId}`
  }
  if (source.sectionSummaryId) {
    return `${source.sectionTitle || '章节摘要'}${page}`
  }
  if (source.sourceType === 'paper_catalog') {
    return '论文目录索引'
  }
  return `原文片段 ${source.chunkIndex ?? source.chunkId ?? '-'}${page}`
}

function formatDateTime(value) {
  if (!value) {
    return '-'
  }

  return String(value).replace('T', ' ').slice(0, 19)
}

function formatMessageRole(role) {
  return role === 'assistant' ? 'AI 回答' : '用户问题'
}

function formatModel(message) {
  return [message.modelProvider, message.modelName].filter(Boolean).join(' · ')
}

onMounted(async () => {
  await Promise.all([loadSessions(), loadPapersForContext()])
})
</script>
