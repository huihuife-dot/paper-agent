<template>
  <section class="page-panel history-page">
    <div class="page-toolbar">
      <div>
        <p class="eyebrow">History</p>
        <h1>对话历史</h1>
        <p>查看历史问答，回到已有会话继续追问。</p>
      </div>
      <div class="page-actions">
        <el-button type="primary" plain :loading="sessionsLoading" @click="loadSessions">刷新会话</el-button>
      </div>
    </div>

    <div class="workbench-grid side-main">
      <el-card class="workflow-card workbench-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span>会话列表</span>
            <el-tag type="info" effect="plain">{{ sessions.length }} 条</el-tag>
          </div>
        </template>

        <div class="panel-scroll">
          <el-empty v-if="sessions.length === 0 && !sessionsLoading" description="暂无历史会话，先完成一次论文问答。" />

          <div v-else v-loading="sessionsLoading" class="history-session-list">
            <button
              v-for="session in sessions"
              :key="session.id"
              class="history-session-item"
              :class="{ active: selectedSession?.id === session.id }"
              type="button"
              @click="selectSession(session)"
            >
              <strong>{{ session.title || `Session #${session.id}` }}</strong>
              <span>#{{ session.id }} · {{ formatDateTime(session.updateTime || session.createTime) }}</span>
              <small v-if="session.paperId">Paper #{{ session.paperId }}</small>
            </button>
          </div>
        </div>
      </el-card>

      <el-card class="workflow-card workbench-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span>{{ selectedSession ? selectedSession.title || `Session #${selectedSession.id}` : '消息记录' }}</span>
            <el-button type="success" plain :disabled="!selectedSession" @click="continueSession">
              继续问答
            </el-button>
          </div>
        </template>

        <div class="panel-scroll">
          <el-empty v-if="!selectedSession" description="选择左侧会话后，这里会显示消息记录。" />

          <div v-else v-loading="messagesLoading" class="history-message-list">
            <el-empty v-if="messages.length === 0 && !messagesLoading" description="该会话还没有消息。" />

            <article
              v-for="message in messages"
              :key="message.id"
              class="message history-message"
              :class="message.role === 'assistant' ? 'answer-message' : 'question-message'"
            >
              <div class="answer-header">
                <strong>{{ message.role === 'assistant' ? 'AI 回答' : '用户问题' }}</strong>
                <el-tag v-if="message.modelProvider || message.modelName" type="info" effect="plain">
                  {{ formatModel(message) }}
                </el-tag>
              </div>
              <p>{{ message.content || '暂无内容' }}</p>
              <small>{{ formatDateTime(message.createTime) }}</small>

              <el-collapse v-if="message.sourcesJson" class="history-sources">
                <el-collapse-item title="查看原始引用数据" :name="message.id">
                  <pre>{{ message.sourcesJson }}</pre>
                </el-collapse-item>
              </el-collapse>
            </article>
          </div>
        </div>
      </el-card>
    </div>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listChatMessages, listChatSessions } from '../api/chatHistory.js'

const router = useRouter()

const sessions = ref([])
const messages = ref([])
const selectedSession = ref(null)
const sessionsLoading = ref(false)
const messagesLoading = ref(false)

async function loadSessions() {
  sessionsLoading.value = true

  try {
    sessions.value = await listChatSessions()
    if (!selectedSession.value && sessions.value.length > 0) {
      await selectSession(sessions.value[0])
    }
  } catch (error) {
    ElMessage.error(`会话列表加载失败：${error.message}`)
  } finally {
    sessionsLoading.value = false
  }
}

async function selectSession(session) {
  selectedSession.value = session
  messagesLoading.value = true

  try {
    messages.value = await listChatMessages(session.id)
  } catch (error) {
    ElMessage.error(`消息记录加载失败：${error.message}`)
  } finally {
    messagesLoading.value = false
  }
}

function continueSession() {
  if (!selectedSession.value) {
    return
  }

  router.push({
    path: '/chat',
    query: { sessionId: selectedSession.value.id },
  })
}

function formatModel(message) {
  return [message.modelProvider, message.modelName].filter(Boolean).join(' · ')
}

function formatDateTime(value) {
  if (!value) {
    return '-'
  }

  return String(value).replace('T', ' ').slice(0, 19)
}

onMounted(loadSessions)
</script>
