<template>
  <section class="page-panel writing-page">
    <div class="writing-page-head">
      <div class="research-mode-switch" aria-label="论文工作模式">
        <RouterLink to="/chat">论文问答</RouterLink>
        <RouterLink to="/writing">论文写作</RouterLink>
      </div>
      <div class="writing-head-actions">
        <el-button plain :loading="loadingProjects" @click="loadProjects">刷新</el-button>
        <el-button type="primary" @click="openCreateDialog">新建写作项目</el-button>
      </div>
    </div>

    <section class="writing-project-strip" aria-label="写作项目切换">
      <span class="filter-bar-label">写作项目</span>
      <div class="writing-project-tabs scroll-clean">
        <button
          v-for="item in projects"
          :key="item.project.id"
          type="button"
          class="writing-project-tab"
          :class="{ active: item.project.id === project?.id }"
          @click="selectProject(item.project.id)"
        >
          <strong>{{ item.project.title }}</strong>
          <span>{{ typeLabel(item.project.documentType) }} · {{ formatTime(item.project.updateTime) }}</span>
        </button>
        <span v-if="!loadingProjects && projects.length === 0" class="empty-session-hint">暂无项目，先选择论文创建一个写作项目。</span>
      </div>
    </section>

    <main v-if="project" class="writing-studio">
      <header class="writing-document-toolbar">
        <div class="writing-title-block">
          <p class="eyebrow">Academic writing</p>
          <h1>{{ project.title }}</h1>
          <p>{{ project.topic }}</p>
        </div>
        <div class="writing-toolbar-actions">
          <el-tag type="info" effect="plain">{{ selectedPapers.length }} 篇论文</el-tag>
          <el-tag type="info" effect="plain">{{ wordCount }} 字</el-tag>
          <el-button plain @click="openSettingsDialog">写作设置</el-button>
          <el-button plain :loading="saving" @click="saveDocument">保存</el-button>
          <el-dropdown trigger="click" @command="handleMoreCommand">
            <el-button plain>更多</el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="papers">查看所选论文</el-dropdown-item>
                <el-dropdown-item command="delete" divided>删除项目</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <div class="writing-tab-bar" role="tablist">
        <button v-for="tab in tabs" :key="tab.value" type="button" :class="{ active: activeTab === tab.value }" @click="activeTab = tab.value">
          {{ tab.label }}
          <span v-if="tab.value === 'citations' && auditWarningCount">{{ auditWarningCount }}</span>
        </button>
      </div>

      <section class="writing-stage scroll-clean">
        <div v-if="activeTab === 'document'" class="writing-document-view">
          <div class="writing-generation-bar">
            <div>
              <strong>正文草稿</strong>
              <span>内容可直接编辑；点击保存会留下可恢复版本。</span>
            </div>
            <div>
              <el-button plain :loading="generating === 'outline'" @click="generateOutline">生成大纲</el-button>
              <el-button type="primary" :loading="generating === 'draft'" @click="generateDraft">生成草稿</el-button>
            </div>
          </div>
          <article class="writing-paper-canvas">
            <div class="writing-paper-meta">{{ typeLabel(project.documentType) }} · {{ citationLabel(project.citationStyle) }} · {{ project.targetWordCount }} 字目标</div>
            <el-input
              ref="contentInputRef"
              v-model="editableContent"
              class="writing-content-editor"
              type="textarea"
              :autosize="{ minRows: 24, maxRows: 60 }"
              placeholder="先生成大纲和草稿，或直接在这里开始写作。引用请使用 [P论文ID]，保存时系统会自动核查。"
            />
          </article>
        </div>

        <div v-else-if="activeTab === 'outline'" class="writing-outline-view">
          <div class="writing-section-heading">
            <div><strong>结构化大纲</strong><span>每一节都记录可使用的论文范围。</span></div>
            <el-button type="primary" :loading="generating === 'outline'" @click="generateOutline">重新生成</el-button>
          </div>
          <el-empty v-if="outlineSections.length === 0" description="尚未生成大纲" />
          <div v-else class="writing-outline-list">
            <article v-for="(section, index) in outlineSections" :key="`${section.heading}-${index}`" class="writing-outline-item">
              <span>{{ String(index + 1).padStart(2, '0') }}</span>
              <div><h3>{{ section.heading }}</h3><p>{{ section.purpose }}</p></div>
              <small>{{ (section.sourcePaperIds || []).map((id) => `[P${id}]`).join(' ') || '待补充来源' }}</small>
            </article>
          </div>
        </div>

        <div v-else-if="activeTab === 'citations'" class="writing-citation-view">
          <div class="writing-audit-grid">
            <div><span>有效引用</span><strong>{{ audit.validCitationCount || 0 }}</strong></div>
            <div><span>已引用论文</span><strong>{{ audit.citedPaperCount || 0 }}/{{ audit.selectedPaperCount || 0 }}</strong></div>
            <div><span>段落引用覆盖</span><strong>{{ audit.paragraphCoverage || 0 }}%</strong></div>
            <div><span>无效引用</span><strong>{{ audit.invalidCitationCount || 0 }}</strong></div>
          </div>
          <el-alert
            v-for="warning in audit.warnings || []"
            :key="warning"
            class="writing-audit-alert"
            type="warning"
            :closable="false"
            show-icon
            :title="warning"
          />
          <el-empty v-if="citations.length === 0" description="正文还没有通过核查的引用" />
          <div v-else class="writing-citation-list">
            <article v-for="citation in citations" :key="citation.paperId" class="writing-citation-card">
              <div class="writing-citation-marker">{{ citation.marker }}</div>
              <div>
                <h3>{{ citation.title || `Paper #${citation.paperId}` }}</h3>
                <p>{{ [citation.authors, citation.publishYear, citation.journal].filter(Boolean).join(' · ') }}</p>
                <blockquote v-if="citation.excerpt">{{ citation.excerpt }}</blockquote>
                <small v-if="citation.chunkId">原文 chunk #{{ citation.chunkId }} · {{ citation.sectionTitle || '未知章节' }} · 第 {{ citation.pageNumber || '?' }} 页</small>
              </div>
            </article>
          </div>
        </div>

        <div v-else class="writing-revision-view">
          <div class="writing-section-heading">
            <div><strong>修改记录</strong><span>生成、保存和 AI 修改都会留下完整快照。</span></div>
            <el-button plain :loading="loadingRevisions" @click="loadRevisions">刷新记录</el-button>
          </div>
          <el-empty v-if="revisions.length === 0 && !loadingRevisions" description="暂无历史版本" />
          <div class="writing-revision-list">
            <article v-for="revision in revisions" :key="revision.id" class="writing-revision-card">
              <div><strong>{{ revisionLabel(revision.revisionType) }}</strong><span>{{ formatTime(revision.createTime) }}</span></div>
              <p>{{ revision.instruction || '未填写修改说明' }}</p>
              <el-button plain size="small" @click="restoreRevision(revision)">恢复此版本</el-button>
            </article>
          </div>
        </div>
      </section>

      <footer class="writing-ai-composer">
        <div class="writing-ai-icon">AI</div>
        <el-input v-model="revisionInstruction" placeholder="告诉 AI 如何修改，例如：将选中段落改得更通俗，并补充不同方法的比较" @keyup.enter="openRevisionDialog" />
        <el-button type="primary" :disabled="!revisionInstruction.trim()" @click="openRevisionDialog">按要求修改</el-button>
      </footer>
    </main>

    <section v-else class="writing-empty-state">
      <div class="writing-empty-icon">✎</div>
      <h1>把阅读结果整理成能继续修改的第一章草稿</h1>
      <p>从本地论文库选择资料，系统会先整理证据，再生成大纲、引言或综述，并保留引用出处和修改版本。</p>
      <el-button type="primary" size="large" @click="openCreateDialog">创建第一个写作项目</el-button>
    </section>

    <el-dialog v-model="projectDialogOpen" :title="editingProject ? '写作设置' : '新建写作项目'" width="760px" destroy-on-close>
      <el-form label-position="top" class="writing-project-form">
        <div class="writing-form-grid">
          <el-form-item label="项目标题"><el-input v-model="projectForm.title" placeholder="例如：多模态 RAG 文献综述" /></el-form-item>
          <el-form-item label="内容类型">
            <el-select v-model="projectForm.documentType">
              <el-option v-for="option in documentTypes" :key="option.value" :label="option.label" :value="option.value" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="写作主题"><el-input v-model="projectForm.topic" type="textarea" :rows="2" placeholder="说明要围绕什么问题进行总结，例如：多模态检索增强生成的研究背景与技术演进" /></el-form-item>
        <div class="writing-form-grid three">
          <el-form-item label="目标字数"><el-input-number v-model="projectForm.targetWordCount" :min="600" :max="8000" :step="100" /></el-form-item>
          <el-form-item label="输出语言"><el-select v-model="projectForm.targetLanguage"><el-option label="中文" value="zh-CN" /><el-option label="English" value="en" /></el-select></el-form-item>
          <el-form-item label="引用偏好"><el-select v-model="projectForm.citationStyle"><el-option label="GB/T 7714" value="GB_T_7714" /><el-option label="IEEE" value="IEEE" /><el-option label="作者-年份" value="AUTHOR_YEAR" /></el-select></el-form-item>
        </div>
        <div class="writing-paper-picker-head"><strong>选择论文</strong><span>已选 {{ projectForm.paperIds.length }}/20 篇，只会使用这些论文生成内容。</span></div>
        <el-input v-model="paperKeyword" class="writing-paper-search" placeholder="搜索标题、作者或年份" clearable />
        <el-checkbox-group v-model="projectForm.paperIds" class="writing-paper-picker scroll-clean">
          <el-checkbox v-for="paper in filteredPapers" :key="paper.id" :value="paper.id" border>
            <span class="writing-picker-title">{{ paper.title || paper.fileName || `Paper #${paper.id}` }}</span>
            <small>{{ [paper.authors, paper.publishYear].filter(Boolean).join(' · ') || '元数据待完善' }}</small>
          </el-checkbox>
        </el-checkbox-group>
      </el-form>
      <template #footer>
        <el-button @click="projectDialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="savingSettings" @click="submitProjectForm">{{ editingProject ? '保存设置' : '创建项目' }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="papersDialogOpen" title="本项目使用的论文" width="720px">
      <div class="writing-selected-paper-list">
        <article v-for="paper in selectedPapers" :key="paper.id"><strong>{{ paper.title }}</strong><span>{{ [paper.authors, paper.publishYear, paper.journal].filter(Boolean).join(' · ') }}</span></article>
      </div>
    </el-dialog>

    <el-dialog v-model="revisionDialogOpen" title="确认 AI 修改范围" width="680px">
      <el-form label-position="top">
        <el-form-item label="修改要求"><el-input v-model="revisionInstruction" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="选中文字（可选）"><el-input v-model="revisionSelectedText" type="textarea" :rows="5" placeholder="如果这里为空，AI 会按要求修订全文；填写后只替换这段文字。" /></el-form-item>
      </el-form>
      <el-alert type="info" :closable="false" show-icon title="局部修改只替换选中的文字，其他正文由系统原样保留。" />
      <template #footer>
        <el-button @click="revisionDialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="generating === 'revise'" @click="reviseDocument">开始修改</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listPapers } from '../api/papers.js'
import {
  createWritingProject, deleteWritingProject, generateWritingDraft, generateWritingOutline,
  getWritingProject, listWritingProjects, listWritingRevisions, restoreWritingRevision,
  reviseWritingContent, updateWritingProject,
} from '../api/writingProjects.js'
import { buildWritingPayload, countChineseWords, parseOutline, revisionLabel } from './paperWritingState.js'

const tabs = [
  { value: 'document', label: '正文' }, { value: 'outline', label: '写作大纲' },
  { value: 'citations', label: '引用核查' }, { value: 'revisions', label: '修改记录' },
]
const documentTypes = [
  { value: 'CHAPTER_ONE', label: '第一章（完整结构）' },
  { value: 'INTRODUCTION', label: '引言' },
  { value: 'LITERATURE_REVIEW', label: '文献综述' },
  { value: 'RESEARCH_STATUS', label: '国内外研究现状' },
]

const projects = ref([])
const current = ref(null)
const papers = ref([])
const revisions = ref([])
const editableContent = ref('')
const activeTab = ref('document')
const loadingProjects = ref(false)
const loadingRevisions = ref(false)
const saving = ref(false)
const savingSettings = ref(false)
const generating = ref('')
const projectDialogOpen = ref(false)
const papersDialogOpen = ref(false)
const revisionDialogOpen = ref(false)
const editingProject = ref(false)
const paperKeyword = ref('')
const revisionInstruction = ref('')
const revisionSelectedText = ref('')
const contentInputRef = ref(null)
const projectForm = ref(emptyProjectForm())

const project = computed(() => current.value?.project || null)
const selectedPapers = computed(() => current.value?.selectedPapers || [])
const citations = computed(() => current.value?.citations || [])
const audit = computed(() => current.value?.citationAudit || {})
const outlineSections = computed(() => parseOutline(project.value?.outlineJson))
const wordCount = computed(() => countChineseWords(editableContent.value))
const auditWarningCount = computed(() => (audit.value.warnings || []).length + (audit.value.invalidCitationCount || 0))
const filteredPapers = computed(() => {
  const keyword = paperKeyword.value.trim().toLowerCase()
  if (!keyword) return papers.value
  return papers.value.filter((paper) => [paper.title, paper.fileName, paper.authors, paper.publishYear].filter(Boolean).join(' ').toLowerCase().includes(keyword))
})

function emptyProjectForm() {
  return { title: '', topic: '', documentType: 'CHAPTER_ONE', targetLanguage: 'zh-CN', targetWordCount: 2500, citationStyle: 'GB_T_7714', paperIds: [] }
}

function applyResult(result) {
  current.value = result
  editableContent.value = result?.project?.content || ''
  const index = projects.value.findIndex((item) => item.project.id === result?.project?.id)
  if (index >= 0) projects.value.splice(index, 1, result)
  else if (result) projects.value.unshift(result)
}

async function loadProjects() {
  loadingProjects.value = true
  try {
    projects.value = await listWritingProjects()
    if (!project.value && projects.value.length) await selectProject(projects.value[0].project.id)
  } catch (error) { ElMessage.error(`写作项目加载失败：${error.message}`) }
  finally { loadingProjects.value = false }
}

async function selectProject(id) {
  try { applyResult(await getWritingProject(id)); revisions.value = []; activeTab.value = 'document' }
  catch (error) { ElMessage.error(`项目加载失败：${error.message}`) }
}

function openCreateDialog() {
  editingProject.value = false; projectForm.value = emptyProjectForm(); paperKeyword.value = ''; projectDialogOpen.value = true
}

function openSettingsDialog() {
  editingProject.value = true
  projectForm.value = { ...project.value, paperIds: [...(current.value.selectedPaperIds || [])] }
  paperKeyword.value = ''; projectDialogOpen.value = true
}

async function submitProjectForm() {
  const payload = buildWritingPayload(projectForm.value, editingProject.value ? editableContent.value : '', editingProject.value ? project.value.outlineJson : '')
  if (!payload.title || !payload.topic) return ElMessage.warning('请填写项目标题和写作主题')
  if (!payload.paperIds.length) return ElMessage.warning('请至少选择 1 篇论文')
  if (payload.paperIds.length > 20) return ElMessage.warning('单个项目最多选择 20 篇论文')
  savingSettings.value = true
  try {
    const result = editingProject.value ? await updateWritingProject(project.value.id, payload) : await createWritingProject(payload)
    applyResult(result); projectDialogOpen.value = false; ElMessage.success(editingProject.value ? '写作设置已保存' : '写作项目已创建')
  } catch (error) { ElMessage.error(`保存失败：${error.message}`) }
  finally { savingSettings.value = false }
}

async function saveDocument() {
  saving.value = true
  try {
    const form = { ...project.value, paperIds: current.value.selectedPaperIds }
    applyResult(await updateWritingProject(project.value.id, buildWritingPayload(form, editableContent.value, project.value.outlineJson)))
    ElMessage.success('正文已保存，并记录了一个版本')
  } catch (error) { ElMessage.error(`保存失败：${error.message}`) }
  finally { saving.value = false }
}

async function generateOutline() {
  generating.value = 'outline'
  try { applyResult(await generateWritingOutline(project.value.id)); activeTab.value = 'outline'; ElMessage.success('大纲已生成') }
  catch (error) { ElMessage.error(`大纲生成失败：${error.message}`) }
  finally { generating.value = '' }
}

async function generateDraft() {
  generating.value = 'draft'
  try { applyResult(await generateWritingDraft(project.value.id)); activeTab.value = 'document'; ElMessage.success('草稿已生成，建议继续人工核对和修改') }
  catch (error) { ElMessage.error(`草稿生成失败：${error.message}`) }
  finally { generating.value = '' }
}

function openRevisionDialog() {
  if (!revisionInstruction.value.trim()) return ElMessage.warning('请填写修改要求')
  const textarea = contentInputRef.value?.textarea
  revisionSelectedText.value = textarea && textarea.selectionStart !== textarea.selectionEnd
    ? editableContent.value.slice(textarea.selectionStart, textarea.selectionEnd) : ''
  revisionDialogOpen.value = true
}

async function reviseDocument() {
  generating.value = 'revise'
  try {
    applyResult(await reviseWritingContent(project.value.id, { instruction: revisionInstruction.value.trim(), selectedText: revisionSelectedText.value.trim() }))
    revisionDialogOpen.value = false; revisionInstruction.value = ''; revisionSelectedText.value = ''; ElMessage.success('修改完成，并已保存版本')
  } catch (error) { ElMessage.error(`修改失败：${error.message}`) }
  finally { generating.value = '' }
}

async function loadRevisions() {
  if (!project.value) return
  loadingRevisions.value = true
  try { revisions.value = await listWritingRevisions(project.value.id) }
  catch (error) { ElMessage.error(`版本加载失败：${error.message}`) }
  finally { loadingRevisions.value = false }
}

async function restoreRevision(revision) {
  try { await ElMessageBox.confirm(`恢复“${revisionLabel(revision.revisionType)}”版本？当前内容也会先保留为恢复记录。`, '恢复版本', { type: 'warning' }) }
  catch { return }
  try { applyResult(await restoreWritingRevision(project.value.id, revision.id)); activeTab.value = 'document'; await loadRevisions(); ElMessage.success('版本已恢复') }
  catch (error) { ElMessage.error(`恢复失败：${error.message}`) }
}

async function handleMoreCommand(command) {
  if (command === 'papers') { papersDialogOpen.value = true; return }
  try { await ElMessageBox.confirm(`确定删除写作项目“${project.value.title}”及其版本记录吗？`, '删除项目', { type: 'warning' }) }
  catch { return }
  const deletingId = project.value.id
  try { await deleteWritingProject(deletingId); current.value = null; projects.value = projects.value.filter((item) => item.project.id !== deletingId); await loadProjects(); ElMessage.success('写作项目已删除') }
  catch (error) { ElMessage.error(`删除失败：${error.message}`) }
}

function typeLabel(value) { return documentTypes.find((item) => item.value === value)?.label || value }
function citationLabel(value) { return ({ GB_T_7714: 'GB/T 7714', IEEE: 'IEEE', AUTHOR_YEAR: '作者-年份' })[value] || value }
function formatTime(value) { return value ? String(value).replace('T', ' ').slice(0, 16) : '-' }

watch(activeTab, (value) => { if (value === 'revisions' && revisions.value.length === 0) loadRevisions() })

onMounted(async () => {
  try { papers.value = await listPapers() } catch (error) { ElMessage.error(`论文列表加载失败：${error.message}`) }
  await loadProjects()
})
</script>
