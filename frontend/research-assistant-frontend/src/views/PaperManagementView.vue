<template>
  <section class="page-panel paper-page">
    <div class="page-toolbar">
      <div>
        <p class="eyebrow">Library</p>
        <h1>文献</h1>
        <p>上传、分类、解析和向量化论文，让文献库保持可组织、可检索、可问答的状态。</p>
      </div>
      <div class="page-actions">
        <el-button type="primary" plain :loading="loading || categoryLoading" @click="reloadLibrary">刷新文献</el-button>
      </div>
    </div>

    <div class="module-layout paper-module-layout">
      <aside class="module-sidebar">
        <el-card class="workflow-card workbench-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span>文献侧栏</span>
              <el-tag type="info" effect="plain">{{ paperStats.total }} 篇</el-tag>
            </div>
          </template>

          <div class="panel-scroll sidebar-panel">
            <el-button type="primary" class="full-width-button" @click="openUploadDialog">添加文献</el-button>
            <el-button plain class="full-width-button" @click="openCategoryDialog()">新建分类</el-button>
            <el-button plain class="full-width-button" :loading="loading || categoryLoading" @click="reloadLibrary">刷新文献</el-button>

            <div class="sidebar-section">
              <p class="sidebar-section-title">分类文件夹</p>
              <button
                class="sidebar-filter-item"
                :class="{ active: selectedCategoryId === null }"
                type="button"
                @click="selectCategory(null)"
              >
                <span>全部文献</span>
                <strong>{{ totalPaperCount }}</strong>
              </button>

              <div v-loading="categoryLoading" class="category-folder-list">
                <div
                  v-for="category in categories"
                  :key="category.id"
                  class="category-folder-row"
                  :class="{ active: selectedCategoryId === category.id }"
                >
                  <button class="sidebar-filter-item category-folder-button" type="button" @click="selectCategory(category.id)">
                    <span>{{ category.name }}</span>
                    <strong>{{ category.paperCount ?? 0 }}</strong>
                  </button>
                  <div class="category-folder-actions">
                    <el-button size="small" text @click.stop="openCategoryDialog(category)">编辑</el-button>
                    <el-button
                      v-if="!category.systemFlag"
                      size="small"
                      text
                      type="danger"
                      @click.stop="handleDeleteCategory(category)"
                    >
                      删除
                    </el-button>
                  </div>
                </div>
              </div>
            </div>

            <div class="sidebar-section">
              <p class="sidebar-section-title">状态筛选</p>
              <button
                v-for="item in paperFilterOptions"
                :key="item.value"
                class="sidebar-filter-item"
                :class="{ active: paperStatusFilter === item.value }"
                type="button"
                @click="paperStatusFilter = item.value"
              >
                <span>{{ item.label }}</span>
                <strong>{{ item.count }}</strong>
              </button>
            </div>
          </div>
        </el-card>
      </aside>

      <main class="module-main paper-work-queue-main">
        <el-card class="workflow-card workbench-card paper-queue-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span>待处理文献</span>
              <el-tag type="warning" effect="plain">{{ pendingPaperRows.length }} 篇</el-tag>
            </div>
          </template>

          <div class="panel-scroll queue-panel-scroll">
            <el-table
              v-loading="loading || profileStatusLoading"
              :data="pendingPaperRows"
              class="paper-desktop-table"
              border
              height="100%"
              empty-text="暂无待处理文献。新上传或未完成解析、向量化、画像的文献会出现在这里。"
            >
              <el-table-column prop="title" label="文献" min-width="260">
                <template #default="{ row }">
                  <strong>{{ row.title || row.fileName || '未命名文献' }}</strong>
                  <p class="paper-meta">{{ formatPaperMeta(row) }}</p>
                  <el-tag size="small" type="info" effect="plain">{{ row.categoryName || '未分类' }}</el-tag>
                  <el-button size="small" text type="primary" @click="openAssets(row)">查看页面资产</el-button>
                </template>
              </el-table-column>

              <el-table-column label="处理状态" min-width="260">
                <template #default="{ row }">
                  <div class="workflow-status-cell">
                    <div class="workflow-status-tags">
                      <el-tag size="small" :type="statusTagType(row.parseStatus)" effect="plain">解析 {{ compactStatusText(row.parseStatus) }}</el-tag>
                      <el-tag size="small" :type="statusTagType(row.vectorStatus)" effect="plain">向量 {{ compactStatusText(row.vectorStatus) }}</el-tag>
                      <el-tag size="small" :type="workflowStateTagType(row.workflowState)" effect="plain">{{ row.workflowState.label }}</el-tag>
                    </div>
                    <p class="paper-meta">{{ row.workflowState.description }}</p>
                    <el-progress
                      v-if="row.workflowState.key === 'profileProcessing'"
                      :percentage="Number(row.profileStatus?.progressPercent || 0)"
                      :stroke-width="8"
                    />
                  </div>
                </template>
              </el-table-column>

              <el-table-column prop="uploadTime" label="上传时间" width="180">
                <template #default="{ row }">
                  {{ formatDateTime(row.uploadTime) }}
                </template>
              </el-table-column>

              <el-table-column label="操作" width="300" fixed="right">
                <template #default="{ row }">
                  <div class="compact-action-row">
                    <el-button size="small" type="success" plain @click="agentLaunchPaper = row; agentLaunchVisible = true">开始复现</el-button>
                    <el-button size="small" plain @click="openPaperContent(row)">查看原文</el-button>
                    <el-button
                      size="small"
                      type="primary"
                      :plain="row.workflowState.key !== 'pendingParse'"
                      :disabled="row.workflowState.processing"
                      :loading="activeAction === `${row.workflowState.actionType}-${row.id}` || activeAction === `profile-${row.id}`"
                      @click="handlePrimaryWorkflowAction(row)"
                    >
                      {{ row.workflowState.actionLabel }}
                    </el-button>
                    <el-dropdown trigger="click" @command="(command) => handlePaperCommand(command, row)">
                      <el-button size="small" plain>更多</el-button>
                      <template #dropdown>
                        <el-dropdown-menu>
                          <el-dropdown-item command="edit">编辑信息</el-dropdown-item>
                          <el-dropdown-item command="profile">查看画像</el-dropdown-item>
                          <el-dropdown-item command="regenerateProfile">重新生成画像</el-dropdown-item>
                          <el-dropdown-item command="indexProfile">重新索引画像</el-dropdown-item>
                          <el-dropdown-item command="move">改分类</el-dropdown-item>
                          <el-dropdown-item command="chat">问答</el-dropdown-item>
                          <el-dropdown-item command="assets">多模态资产</el-dropdown-item>
                          <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
                        </el-dropdown-menu>
                      </template>
                    </el-dropdown>
                  </div>
                </template>
              </el-table-column>
            </el-table>
            <div v-loading="loading || profileStatusLoading" class="paper-mobile-list">
              <article v-for="row in pendingPaperRows" :key="row.id" class="paper-mobile-card">
                <div class="paper-mobile-heading">
                  <div>
                    <strong>{{ row.title || row.fileName || '未命名文献' }}</strong>
                    <p class="paper-meta">{{ formatPaperMeta(row) }}</p>
                  </div>
                  <el-tag size="small" :type="workflowStateTagType(row.workflowState)" effect="plain">{{ row.workflowState.label }}</el-tag>
                </div>
                <div class="workflow-status-tags">
                  <el-tag size="small" :type="statusTagType(row.parseStatus)" effect="plain">解析 {{ compactStatusText(row.parseStatus) }}</el-tag>
                  <el-tag size="small" :type="statusTagType(row.vectorStatus)" effect="plain">向量 {{ compactStatusText(row.vectorStatus) }}</el-tag>
                  <el-tag size="small" type="info" effect="plain">{{ row.categoryName || '未分类' }}</el-tag>
                </div>
                <p class="paper-mobile-description">{{ row.workflowState.description }}</p>
                <el-progress v-if="row.workflowState.key === 'profileProcessing'" :percentage="Number(row.profileStatus?.progressPercent || 0)" :stroke-width="8" />
                <div class="paper-mobile-actions">
                  <el-button size="small" type="success" plain @click="agentLaunchPaper = row; agentLaunchVisible = true">开始复现</el-button>
                  <el-button size="small" plain @click="openPaperContent(row)">查看原文</el-button>
                  <el-button
                    size="small"
                    type="primary"
                    :plain="row.workflowState.key !== 'pendingParse'"
                    :disabled="row.workflowState.processing"
                    :loading="activeAction === `${row.workflowState.actionType}-${row.id}` || activeAction === `profile-${row.id}`"
                    @click="handlePrimaryWorkflowAction(row)"
                  >{{ row.workflowState.actionLabel }}</el-button>
                  <el-dropdown trigger="click" @command="(command) => handlePaperCommand(command, row)">
                    <el-button size="small" plain>更多操作</el-button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item command="edit">编辑信息</el-dropdown-item>
                        <el-dropdown-item command="profile">查看画像</el-dropdown-item>
                        <el-dropdown-item command="regenerateProfile">重新生成画像</el-dropdown-item>
                        <el-dropdown-item command="indexProfile">重新索引画像</el-dropdown-item>
                        <el-dropdown-item command="move">改分类</el-dropdown-item>
                        <el-dropdown-item command="chat">问答</el-dropdown-item>
                        <el-dropdown-item command="assets">多模态资产</el-dropdown-item>
                        <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </div>
              </article>
              <el-empty v-if="!pendingPaperRows.length" description="暂无待处理文献" />
            </div>
          </div>
        </el-card>

        <el-card class="workflow-card workbench-card paper-queue-card ready-paper-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span>已入库文献</span>
              <el-tag type="success" effect="plain">{{ readyPaperRows.length }} 篇</el-tag>
            </div>
          </template>

          <div class="panel-scroll queue-panel-scroll">
            <el-table
              v-loading="loading || profileStatusLoading"
              :data="readyPaperRows"
              class="paper-desktop-table"
              border
              height="100%"
              empty-text="暂无已入库文献。解析、向量化和画像全部完成后会进入这里。"
            >
              <el-table-column prop="title" label="文献" min-width="280">
                <template #default="{ row }">
                  <strong>{{ row.title || row.fileName || '未命名文献' }}</strong>
                  <p class="paper-meta">{{ formatPaperMeta(row) }}</p>
                  <el-tag size="small" type="info" effect="plain">{{ row.categoryName || '未分类' }}</el-tag>
                  <el-button size="small" text type="primary" @click="openAssets(row)">查看页面资产</el-button>
                </template>
              </el-table-column>

              <el-table-column label="画像资产" min-width="220">
                <template #default="{ row }">
                  <el-tag type="success" effect="plain">{{ row.profileStatus?.profileVersion || 'paper-profile-v1' }}</el-tag>
                  <p class="paper-meta">章节摘要 {{ row.profileStatus?.sectionSummaryCount || 0 }} 条</p>
                </template>
              </el-table-column>

              <el-table-column prop="uploadTime" label="上传时间" width="180">
                <template #default="{ row }">
                  {{ formatDateTime(row.uploadTime) }}
                </template>
              </el-table-column>

              <el-table-column label="操作" width="260" fixed="right">
                <template #default="{ row }">
                  <div class="compact-action-row">
                    <el-button size="small" plain @click="openPaperContent(row)">查看原文</el-button>
                    <el-button size="small" type="success" plain @click="goToChat(row)">问答</el-button>
                    <el-button size="small" type="primary" plain @click="agentLaunchPaper = row; agentLaunchVisible = true">开始复现</el-button>
                    <el-dropdown trigger="click" @command="(command) => handlePaperCommand(command, row)">
                      <el-button size="small" plain>更多</el-button>
                      <template #dropdown>
                        <el-dropdown-menu>
                          <el-dropdown-item command="edit">编辑信息</el-dropdown-item>
                          <el-dropdown-item command="profile">查看画像</el-dropdown-item>
                          <el-dropdown-item command="regenerateProfile">重新生成画像</el-dropdown-item>
                          <el-dropdown-item command="move">改分类</el-dropdown-item>
                          <el-dropdown-item command="assets">多模态资产</el-dropdown-item>
                          <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
                        </el-dropdown-menu>
                      </template>
                    </el-dropdown>
                  </div>
                </template>
              </el-table-column>
            </el-table>
            <div v-loading="loading || profileStatusLoading" class="paper-mobile-list">
              <article v-for="row in readyPaperRows" :key="row.id" class="paper-mobile-card">
                <div class="paper-mobile-heading">
                  <div>
                    <strong>{{ row.title || row.fileName || '未命名文献' }}</strong>
                    <p class="paper-meta">{{ formatPaperMeta(row) }}</p>
                  </div>
                  <el-tag size="small" type="success" effect="plain">已入库</el-tag>
                </div>
                <div class="workflow-status-tags">
                  <el-tag size="small" type="info" effect="plain">{{ row.categoryName || '未分类' }}</el-tag>
                  <el-tag size="small" type="success" effect="plain">{{ row.profileStatus?.profileVersion || 'paper-profile-v1' }}</el-tag>
                  <el-tag size="small" type="info" effect="plain">章节摘要 {{ row.profileStatus?.sectionSummaryCount || 0 }} 条</el-tag>
                </div>
                <div class="paper-mobile-actions">
                  <el-button size="small" plain @click="openPaperContent(row)">查看原文</el-button>
                  <el-button size="small" type="success" plain @click="goToChat(row)">问答</el-button>
                  <el-button size="small" type="primary" plain @click="agentLaunchPaper = row; agentLaunchVisible = true">开始复现</el-button>
                  <el-dropdown trigger="click" @command="(command) => handlePaperCommand(command, row)">
                    <el-button size="small" plain>更多操作</el-button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item command="edit">编辑信息</el-dropdown-item>
                        <el-dropdown-item command="profile">查看画像</el-dropdown-item>
                        <el-dropdown-item command="regenerateProfile">重新生成画像</el-dropdown-item>
                        <el-dropdown-item command="move">改分类</el-dropdown-item>
                        <el-dropdown-item command="assets">多模态资产</el-dropdown-item>
                        <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </div>
              </article>
              <el-empty v-if="!readyPaperRows.length" description="暂无已入库文献" />
            </div>
          </div>
        </el-card>
      </main>
    </div>

    <ResearchEngineeringLaunchDialog
      v-if="agentLaunchPaper"
      v-model="agentLaunchVisible"
      mode="paper"
      :source-id="Number(agentLaunchPaper.id)"
    />

    <el-dialog v-model="assetDialogVisible" :title="`多模态资产 · ${assetPaper?.title || ''}`" width="960px" destroy-on-close>
      <div class="compact-action-row" style="margin-bottom: 12px">
        <el-button type="primary" :loading="assetLoading" @click="extractAssets">提取/刷新页面资产</el-button>
        <el-button type="success" :loading="visionAnalyzing" :disabled="!paperAssets.length" @click="analyzeEligibleAssets">分析低置信度资产</el-button>
        <el-tag type="info">{{ paperAssets.length }} 条资产</el-tag>
        <el-tag v-if="visionJobStatus?.status" :type="visionStatusType">{{ visionJobStatus.status }}</el-tag>
      </div>
      <el-empty v-if="!assetLoading && !paperAssets.length" description="尚未提取页面资产，点击上方按钮开始。" />
      <el-table v-else v-loading="assetLoading" :data="paperAssets" max-height="480">
        <el-table-column prop="assetType" label="类型" width="120" />
        <el-table-column prop="assetLabel" label="标签" min-width="170" />
        <el-table-column prop="pageStart" label="页码" width="80" />
        <el-table-column label="状态" width="130"><template #default="{ row }"><el-tag size="small">{{ row.verificationStatus }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="320"><template #default="{ row }"><el-button size="small" @click="openAsset(row)">查看</el-button><el-button v-if="['FIGURE','TABLE','EQUATION'].includes(row.assetType)" size="small" type="primary" @click="analyzeAsset(row)">视觉分析</el-button><el-button size="small" type="success" @click="reviewAsset(row, true)">确认</el-button><el-button size="small" type="danger" @click="reviewAsset(row, false)">拒绝</el-button></template></el-table-column>
      </el-table>
      <el-divider content-position="left">复现事实（独立索引）</el-divider>
      <div class="compact-action-row" style="margin-bottom: 12px">
        <el-button type="warning" :loading="factLoading" :disabled="!paperAssets.length" @click="rebuildFacts">构建复现事实</el-button>
        <el-input v-model="factQuery" clearable placeholder="只在复现事实中检索，例如：学习率或网络结构" style="width: 360px" @keyup.enter="searchFacts" />
        <el-button :loading="factLoading" :disabled="!factQuery.trim()" @click="searchFacts">检索事实</el-button>
        <el-switch v-model="includeModelInferredFacts" active-text="显示模型推断" @change="loadFacts" />
        <el-tag type="info">{{ reproductionFacts.length }} 条事实</el-tag>
      </div>
      <el-table v-loading="factLoading" :data="reproductionFacts" max-height="320" empty-text="尚未构建复现事实">
        <el-table-column prop="factType" label="事实类型" width="170" />
        <el-table-column prop="factKey" label="键" width="170" />
        <el-table-column prop="factValue" label="事实内容" min-width="300" show-overflow-tooltip />
        <el-table-column prop="pageNumber" label="页码" width="70" />
        <el-table-column label="来源" width="150"><template #default="{ row }">{{ row.sourceKind }} #{{ row.sourceId }}</template></el-table-column>
        <el-table-column label="状态" width="150"><template #default="{ row }"><el-tag size="small" :type="row.verificationStatus === 'MODEL_INFERRED' ? 'warning' : 'success'">{{ row.verificationStatus }}</el-tag></template></el-table-column>
        <el-table-column label="冲突" width="90"><template #default="{ row }"><el-tag v-if="row.conflictGroup" size="small" type="danger">有冲突</el-tag><span v-else>-</span></template></el-table-column>
      </el-table>
      <el-dialog v-model="assetPreviewVisible" append-to-body title="资产原页与分析" width="760px"><img v-if="selectedAsset" :src="getPaperAssetContentUrl(assetPaper.id, selectedAsset.id)" style="max-width:100%"><h4 v-if="selectedAsset?.structuredContentJson">确定性提取</h4><pre v-if="selectedAsset?.structuredContentJson" style="white-space:pre-wrap">{{ selectedAsset.structuredContentJson }}</pre><h4 v-if="selectedAsset?.semanticDescription">视觉模型说明（待确认）</h4><pre v-if="selectedAsset?.semanticDescription" style="white-space:pre-wrap">{{ selectedAsset.semanticDescription }}</pre></el-dialog>
    </el-dialog>

    <el-dialog v-model="uploadDialogVisible" title="添加文献" width="680px" destroy-on-close>
      <el-form label-position="top" class="upload-form compact-form">
        <el-form-item label="PDF 文件">
          <el-upload
            v-model:file-list="fileList"
            drag
            action="#"
            accept=".pdf,application/pdf"
            :auto-upload="false"
            :limit="1"
            :on-change="handleFileChange"
            :on-remove="handleFileRemove"
          >
            <el-icon class="upload-icon"><UploadFilled /></el-icon>
            <div class="el-upload__text">拖入 PDF，或点击选择文件</div>
            <template #tip>
              <div class="el-upload__tip">选择 PDF 后上传，系统会保存到文献库。</div>
            </template>
          </el-upload>
        </el-form-item>

        <el-form-item label="文献分类">
          <el-select v-model="uploadForm.categoryId" placeholder="选择已有分类" filterable>
            <el-option v-for="category in categories" :key="category.id" :label="category.name" :value="category.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="标题">
          <el-input v-model="uploadForm.title" placeholder="不填时使用文件名" clearable />
        </el-form-item>

        <el-form-item label="作者">
          <el-input v-model="uploadForm.authors" placeholder="例如：Smith, Wang" clearable />
        </el-form-item>

        <el-form-item label="发表年份">
          <el-input-number
            v-model="uploadForm.publishYear"
            :min="1900"
            :max="2100"
            :controls="false"
            placeholder="例如：2025"
          />
        </el-form-item>

        <el-form-item label="期刊 / 会议">
          <el-input v-model="uploadForm.journal" placeholder="例如：ACL" clearable />
        </el-form-item>

        <el-form-item label="关键词">
          <el-input v-model="uploadForm.keywords" placeholder="例如：RAG, Embedding" clearable />
        </el-form-item>

        <el-form-item label="备注">
          <el-input v-model="uploadForm.remark" type="textarea" :rows="3" placeholder="记录这篇文献的用途" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="uploadDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="handleUpload">上传文献</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editDialogVisible" title="编辑文献信息" width="680px" destroy-on-close>
      <el-form label-position="top" class="compact-form">
        <el-form-item label="标题" required>
          <el-input v-model="editForm.title" maxlength="500" show-word-limit placeholder="请输入文献标题" clearable />
        </el-form-item>
        <el-form-item label="作者">
          <el-input v-model="editForm.authors" placeholder="例如：Smith, Wang" clearable />
        </el-form-item>
        <el-form-item label="发表年份">
          <el-input-number v-model="editForm.publishYear" :min="1000" :max="2100" :controls="false" placeholder="例如：2025" />
        </el-form-item>
        <el-form-item label="期刊 / 会议">
          <el-input v-model="editForm.journal" maxlength="500" placeholder="例如：ACL" clearable />
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="editForm.keywords" placeholder="多个关键词可用逗号分隔" clearable />
        </el-form-item>
        <el-form-item label="摘要">
          <el-input v-model="editForm.abstractText" type="textarea" :rows="4" placeholder="可补充文献摘要" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="editForm.remark" type="textarea" :rows="3" placeholder="记录阅读重点或用途" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingPaperMetadata" @click="handleSavePaperMetadata">保存信息</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="categoryDialogVisible" :title="editingCategory ? '编辑分类' : '新建分类'" width="520px" destroy-on-close>
      <el-form label-position="top" class="compact-form">
        <el-form-item label="分类名称">
          <el-input v-model="categoryForm.name" placeholder="例如：RAG 核心论文" clearable />
        </el-form-item>
        <el-form-item label="分类说明">
          <el-input v-model="categoryForm.description" type="textarea" :rows="3" placeholder="记录这个分类的用途" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="categoryDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingCategory" @click="handleSaveCategory">保存分类</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="paperCategoryDialogVisible" title="修改文献分类" width="520px" destroy-on-close>
      <el-form label-position="top" class="compact-form">
        <el-form-item label="当前文献">
          <el-input :model-value="movingPaper?.title || movingPaper?.fileName || '未命名文献'" disabled />
        </el-form-item>
        <el-form-item label="移动到分类">
          <el-select v-model="selectedMoveCategoryId" placeholder="选择目标分类" filterable>
            <el-option v-for="category in categories" :key="category.id" :label="category.name" :value="category.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="paperCategoryDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="movingPaperLoading" @click="handleMovePaper">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="profileDialogVisible"
      :title="`文献画像 · ${profilePaper?.title || profilePaper?.fileName || '未命名文献'}`"
      width="820px"
      destroy-on-close
      @closed="stopProfilePolling"
    >
      <div v-loading="profileLoading" class="profile-dialog-body">
        <el-alert
          v-if="isProfileProcessing()"
          class="profile-job-alert"
          type="warning"
          :closable="false"
          show-icon
          title="文献画像生成中"
        >
          <p>后台正在生成章节摘要和整篇画像，你可以关闭弹窗，稍后再回来查看。</p>
        </el-alert>
        <el-alert
          v-else-if="profileJob?.status === 'FAILED'"
          class="profile-job-alert"
          type="error"
          :closable="false"
          show-icon
          title="文献画像生成失败"
        >
          <p>{{ profileJob.errorMessage || '生成过程中发生未知错误。' }}</p>
        </el-alert>

        <el-empty v-if="!profileLoading && !hasProfile() && !isProfileProcessing()" description="这篇文献还没有画像。先生成画像，再用于多篇比较问答。">
          <el-button type="primary" :loading="profileGenerating" @click="handleGenerateProfile">生成文献画像</el-button>
        </el-empty>

        <template v-else-if="hasProfile()">
          <div class="profile-brief-card">
            <p class="eyebrow">Paper profile</p>
            <h3>{{ profileResult.profile.researchProblem || '暂未提取研究问题' }}</h3>
            <p>{{ profileResult.profile.profileText || '画像文本暂未返回。' }}</p>
          </div>

          <div class="profile-grid">
            <article class="profile-field-card">
              <span>方法概述</span>
              <p>{{ profileResult.profile.methodSummary || '信息不足' }}</p>
            </article>
            <article class="profile-field-card">
              <span>实验与评估</span>
              <p>{{ profileResult.profile.experimentSummary || '信息不足' }}</p>
            </article>
            <article class="profile-field-card">
              <span>主要贡献</span>
              <p>{{ profileResult.profile.keyContributions || '信息不足' }}</p>
            </article>
            <article class="profile-field-card">
              <span>局限性</span>
              <p>{{ profileResult.profile.limitations || '信息不足' }}</p>
            </article>
          </div>

          <div class="profile-meta-row">
            <el-tag type="info" effect="plain">{{ profileResult.profile.profileVersion || 'paper-profile-v1' }}</el-tag>
            <el-tag type="success" effect="plain">章节摘要 {{ profileResult.sectionSummaries?.length || 0 }} 条</el-tag>
            <el-tag v-if="profileResult.profile.keywords" type="warning" effect="plain">{{ profileResult.profile.keywords }}</el-tag>
          </div>
        </template>
      </div>

      <template #footer>
        <el-button @click="profileDialogVisible = false">关闭</el-button>
        <el-button v-if="hasProfile()" :loading="profileLoading" @click="loadPaperProfile(profilePaper)">刷新画像</el-button>
        <el-button type="primary" :loading="profileGenerating" :disabled="isProfileProcessing()" @click="handleGenerateProfile">
          {{ isProfileProcessing() ? '生成中' : hasProfile() ? '重新生成画像' : '生成文献画像' }}
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import {
  deletePaper,
  getPaperProfileJob,
  getPaperProfile,
  getPaperContentUrl,
  getPaperAssetContentUrl,
  extractPaperAssets,
  listPaperAssets,
  confirmPaperAsset,
  rejectPaperAsset,
  analyzePaperAsset,
  analyzeEligiblePaperAssets,
  getPaperAssetAnalysisStatus,
  rebuildPaperReproductionFacts,
  listPaperReproductionFacts,
  searchPaperReproductionFacts,
  indexPaperProfile,
  listPaperProfileStatuses,
  listPapers,
  parsePaper,
  startPaperProfileJob,
  uploadPaper,
  updatePaperCategoryOfPaper,
  updatePaperMetadata,
  vectorizePaper,
} from '../api/papers.js'
import {
  createPaperCategory,
  deletePaperCategory,
  listPaperCategories,
  updatePaperCategory,
} from '../api/paperCategories.js'
import { buildPaperChatRoute } from './ragChatState.js'
import { splitPaperRows } from './paperWorkflowState.js'
import ResearchEngineeringLaunchDialog from '../components/ResearchEngineeringLaunchDialog.vue'

const router = useRouter()

const loading = ref(false)
const uploading = ref(false)
const activeAction = ref('')
const paperRows = ref([])
const profileStatuses = ref([])
const profileStatusLoading = ref(false)
const profileStatusPollingTimer = ref(null)
const fileList = ref([])
const selectedFile = ref(null)
const uploadDialogVisible = ref(false)
const paperStatusFilter = ref('all')
const editDialogVisible = ref(false)
const editingPaper = ref(null)
const savingPaperMetadata = ref(false)

const categories = ref([])
const categoryLoading = ref(false)
const selectedCategoryId = ref(null)
const categoryDialogVisible = ref(false)
const savingCategory = ref(false)
const editingCategory = ref(null)
const paperCategoryDialogVisible = ref(false)
const movingPaper = ref(null)
const movingPaperLoading = ref(false)
const selectedMoveCategoryId = ref(null)
const profileDialogVisible = ref(false)
const profilePaper = ref(null)
const profileResult = ref(null)
const profileJob = ref(null)
const profilePollingTimer = ref(null)
const profileLoading = ref(false)
const profileGenerating = ref(false)
const agentLaunchVisible = ref(false)
const agentLaunchPaper = ref(null)
const assetDialogVisible = ref(false)
const assetPreviewVisible = ref(false)
const assetPaper = ref(null)
const paperAssets = ref([])
const selectedAsset = ref(null)
const assetLoading = ref(false)
const visionAnalyzing = ref(false)
const visionJobStatus = ref(null)
const visionStatusType = computed(() => visionJobStatus.value?.status === 'COMPLETED' ? 'success' : visionJobStatus.value?.status === 'FAILED' ? 'danger' : 'warning')
const reproductionFacts = ref([])
const factLoading = ref(false)
const factQuery = ref('')
const includeModelInferredFacts = ref(false)

const uploadForm = reactive({
  title: '',
  authors: '',
  publishYear: undefined,
  journal: '',
  keywords: '',
  remark: '',
  categoryId: null,
})

const editForm = reactive({
  title: '',
  authors: '',
  publishYear: undefined,
  journal: '',
  keywords: '',
  abstractText: '',
  remark: '',
})

const categoryForm = reactive({
  name: '',
  description: '',
})

const paperWorkflowRows = computed(() => splitPaperRows(paperRows.value, profileStatuses.value))

const pendingPaperRows = computed(() => {
  const rows = paperWorkflowRows.value.pendingRows
  if (paperStatusFilter.value === 'pendingParse') {
    return rows.filter((row) => row.workflowState.key === 'pendingParse' || row.workflowState.key === 'parseFailed')
  }
  if (paperStatusFilter.value === 'pendingVector') {
    return rows.filter((row) => row.workflowState.key === 'pendingVector' || row.workflowState.key === 'vectorFailed')
  }
  if (paperStatusFilter.value === 'pendingProfile') {
    return rows.filter((row) => ['pendingProfile', 'profileProcessing', 'profileFailed', 'pendingProfileIndex'].includes(row.workflowState.key))
  }
  if (paperStatusFilter.value === 'processing') {
    return rows.filter((row) => row.workflowState.processing)
  }
  if (paperStatusFilter.value === 'failed') {
    return rows.filter((row) => row.workflowState.failed)
  }
  return rows
})

const readyPaperRows = computed(() => paperWorkflowRows.value.readyRows)

const paperStats = computed(() => ({
  total: paperRows.value.length,
  pending: paperWorkflowRows.value.pendingRows.length,
  ready: paperWorkflowRows.value.readyRows.length,
  pendingParse: paperWorkflowRows.value.pendingRows.filter((paper) => ['pendingParse', 'parseFailed'].includes(paper.workflowState.key)).length,
  pendingVector: paperWorkflowRows.value.pendingRows.filter((paper) => ['pendingVector', 'vectorFailed'].includes(paper.workflowState.key)).length,
  pendingProfile: paperWorkflowRows.value.pendingRows.filter((paper) => ['pendingProfile', 'profileProcessing', 'profileFailed', 'pendingProfileIndex'].includes(paper.workflowState.key)).length,
  processing: paperWorkflowRows.value.pendingRows.filter((paper) => paper.workflowState.processing).length,
  failed: paperWorkflowRows.value.pendingRows.filter((paper) => paper.workflowState.failed).length,
}))

const totalPaperCount = computed(() => categories.value.reduce((total, category) => total + Number(category.paperCount || 0), 0) || paperRows.value.length)

const paperFilterOptions = computed(() => [
  { label: '全部待处理', value: 'all', count: paperStats.value.pending },
  { label: '待解析', value: 'pendingParse', count: paperStats.value.pendingParse },
  { label: '待向量化', value: 'pendingVector', count: paperStats.value.pendingVector },
  { label: '待画像/索引', value: 'pendingProfile', count: paperStats.value.pendingProfile },
  { label: '处理中', value: 'processing', count: paperStats.value.processing },
  { label: '失败', value: 'failed', count: paperStats.value.failed },
])

function appendIfPresent(formData, key, value) {
  if (value !== undefined && value !== null && `${value}`.trim() !== '') {
    formData.append(key, value)
  }
}

function resetUploadForm() {
  selectedFile.value = null
  fileList.value = []
  uploadForm.title = ''
  uploadForm.authors = ''
  uploadForm.publishYear = undefined
  uploadForm.journal = ''
  uploadForm.keywords = ''
  uploadForm.remark = ''
  uploadForm.categoryId = findUncategorizedCategoryId()
}

function handleFileChange(uploadFile) {
  selectedFile.value = uploadFile.raw
}

function handleFileRemove() {
  selectedFile.value = null
}

async function reloadLibrary() {
  await Promise.all([loadCategories(), loadPapers(), loadProfileStatuses()])
}

async function loadCategories() {
  categoryLoading.value = true

  try {
    categories.value = await listPaperCategories()
    if (!uploadForm.categoryId) {
      uploadForm.categoryId = findUncategorizedCategoryId()
    }
  } catch (error) {
    ElMessage.error(`加载文献分类失败：${error.message}`)
  } finally {
    categoryLoading.value = false
  }
}

async function loadPapers() {
  loading.value = true

  try {
    paperRows.value = await listPapers(selectedCategoryId.value ? { categoryId: selectedCategoryId.value } : {})
  } catch (error) {
    ElMessage.error(`加载文献列表失败：${error.message}`)
  } finally {
    loading.value = false
  }
}

async function loadProfileStatuses() {
  profileStatusLoading.value = true

  try {
    profileStatuses.value = await listPaperProfileStatuses()
    syncProfileStatusPolling()
  } catch (error) {
    ElMessage.error(`加载画像任务状态失败：${error.message}`)
  } finally {
    profileStatusLoading.value = false
  }
}

function hasProcessingProfileJob() {
  return profileStatuses.value.some((status) => status.jobStatus === 'PROCESSING')
}

function syncProfileStatusPolling() {
  if (hasProcessingProfileJob()) {
    startProfileStatusPolling()
  } else {
    stopProfileStatusPolling()
  }
}

function startProfileStatusPolling() {
  if (profileStatusPollingTimer.value) {
    return
  }
  profileStatusPollingTimer.value = window.setInterval(loadProfileStatuses, 3000)
}

function stopProfileStatusPolling() {
  if (profileStatusPollingTimer.value) {
    window.clearInterval(profileStatusPollingTimer.value)
    profileStatusPollingTimer.value = null
  }
}

async function selectCategory(categoryId) {
  selectedCategoryId.value = categoryId
  await Promise.all([loadPapers(), loadProfileStatuses()])
}

function openUploadDialog() {
  uploadForm.categoryId = findUncategorizedCategoryId()
  uploadDialogVisible.value = true
}

function openPaperContent(row) {
  // 由浏览器内置 PDF 阅读器在新页面打开，当前文献列表不会丢失。
  window.open(getPaperContentUrl(row.id), '_blank', 'noopener,noreferrer')
}

function openEditPaperDialog(row) {
  editingPaper.value = row
  editForm.title = row.title || row.fileName || ''
  editForm.authors = row.authors || ''
  editForm.publishYear = row.publishYear ?? undefined
  editForm.journal = row.journal || ''
  editForm.keywords = row.keywords || ''
  editForm.abstractText = row.abstractText || ''
  editForm.remark = row.remark || ''
  editDialogVisible.value = true
}

async function handleSavePaperMetadata() {
  if (!editingPaper.value) {
    return
  }
  if (!editForm.title || editForm.title.trim() === '') {
    ElMessage.warning('文献标题不能为空')
    return
  }

  savingPaperMetadata.value = true
  try {
    await updatePaperMetadata(editingPaper.value.id, {
      title: editForm.title.trim(),
      authors: editForm.authors,
      publishYear: editForm.publishYear ?? null,
      journal: editForm.journal,
      keywords: editForm.keywords,
      abstractText: editForm.abstractText,
      remark: editForm.remark,
    })
    ElMessage.success('文献信息已更新')
    editDialogVisible.value = false
    await reloadLibrary()
  } catch (error) {
    ElMessage.error(`保存文献信息失败：${error.message}`)
  } finally {
    savingPaperMetadata.value = false
  }
}

async function handleUpload() {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择一个 PDF 文件')
    return
  }

  const formData = new FormData()
  formData.append('file', selectedFile.value)
  appendIfPresent(formData, 'categoryId', uploadForm.categoryId)
  appendIfPresent(formData, 'title', uploadForm.title)
  appendIfPresent(formData, 'authors', uploadForm.authors)
  appendIfPresent(formData, 'publishYear', uploadForm.publishYear)
  appendIfPresent(formData, 'journal', uploadForm.journal)
  appendIfPresent(formData, 'keywords', uploadForm.keywords)
  appendIfPresent(formData, 'remark', uploadForm.remark)

  uploading.value = true

  try {
    await uploadPaper(formData)
    ElMessage.success('文献上传成功')
    resetUploadForm()
    uploadDialogVisible.value = false
    await reloadLibrary()
  } catch (error) {
    ElMessage.error(`上传失败：${error.message}`)
  } finally {
    uploading.value = false
  }
}

function openCategoryDialog(category = null) {
  editingCategory.value = category
  categoryForm.name = category?.name || ''
  categoryForm.description = category?.description || ''
  categoryDialogVisible.value = true
}

async function handleSaveCategory() {
  if (!categoryForm.name || categoryForm.name.trim() === '') {
    ElMessage.warning('分类名称不能为空')
    return
  }

  savingCategory.value = true

  try {
    const payload = {
      name: categoryForm.name.trim(),
      description: categoryForm.description?.trim() || '',
    }

    if (editingCategory.value) {
      await updatePaperCategory(editingCategory.value.id, payload)
      ElMessage.success('分类已更新')
    } else {
      await createPaperCategory(payload)
      ElMessage.success('分类已创建')
    }

    categoryDialogVisible.value = false
    await reloadLibrary()
  } catch (error) {
    ElMessage.error(`保存分类失败：${error.message}`)
  } finally {
    savingCategory.value = false
  }
}

async function handleDeleteCategory(category) {
  try {
    await ElMessageBox.confirm(
      `确定删除“${category.name}”吗？删除分类不会删除文献，该分类下的文献会自动移动到“未分类”。`,
      '删除分类',
      { type: 'warning', confirmButtonText: '删除分类', cancelButtonText: '取消' },
    )

    await deletePaperCategory(category.id)
    ElMessage.success('分类已删除，文献已移动到未分类')

    if (selectedCategoryId.value === category.id) {
      selectedCategoryId.value = findUncategorizedCategoryId()
    }

    await reloadLibrary()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(`删除分类失败：${error.message || error}`)
    }
  }
}

function openMovePaperDialog(row) {
  movingPaper.value = row
  selectedMoveCategoryId.value = row.categoryId || findUncategorizedCategoryId()
  paperCategoryDialogVisible.value = true
}

async function handleMovePaper() {
  if (!movingPaper.value) {
    return
  }

  if (!selectedMoveCategoryId.value) {
    ElMessage.warning('请选择目标分类')
    return
  }

  movingPaperLoading.value = true

  try {
    await updatePaperCategoryOfPaper(movingPaper.value.id, selectedMoveCategoryId.value)
    ElMessage.success('文献分类已更新')
    paperCategoryDialogVisible.value = false
    await reloadLibrary()
  } catch (error) {
    ElMessage.error(`修改文献分类失败：${error.message}`)
  } finally {
    movingPaperLoading.value = false
  }
}

async function openProfileDialog(row) {
  profilePaper.value = row
  profileResult.value = null
  profileJob.value = null
  profileDialogVisible.value = true
  await Promise.all([loadPaperProfile(row), loadProfileJob(row)])
}

async function loadPaperProfile(row = profilePaper.value) {
  if (!row?.id) {
    return
  }

  profileLoading.value = true

  try {
    profileResult.value = await getPaperProfile(row.id)
  } catch (error) {
    ElMessage.error(`加载文献画像失败：${error.message}`)
    profileResult.value = null
  } finally {
    profileLoading.value = false
  }
}

async function loadProfileJob(row = profilePaper.value) {
  if (!row?.id) {
    return
  }

  try {
    profileJob.value = await getPaperProfileJob(row.id)
    if (isProfileProcessing()) {
      startProfilePolling()
    }
  } catch (error) {
    ElMessage.error(`加载画像任务状态失败：${error.message}`)
  }
}

async function handleGenerateProfile() {
  if (!profilePaper.value?.id || isProfileProcessing()) {
    return
  }

  profileGenerating.value = true

  try {
    profileJob.value = await startPaperProfileJob(profilePaper.value.id)
    ElMessage.success('文献画像已开始生成')
    profileDialogVisible.value = false
    await loadProfileStatuses()
  } catch (error) {
    ElMessage.error(`启动文献画像生成失败：${error.message}`)
  } finally {
    profileGenerating.value = false
  }
}

function startProfilePolling() {
  stopProfilePolling()
  profilePollingTimer.value = window.setInterval(async () => {
    await loadProfileJob(profilePaper.value)
    if (profileJob.value?.status === 'COMPLETED') {
      stopProfilePolling()
      await loadPaperProfile(profilePaper.value)
      ElMessage.success('文献画像已生成')
    } else if (profileJob.value?.status === 'FAILED') {
      stopProfilePolling()
    }
  }, 3000)
}

function stopProfilePolling() {
  if (profilePollingTimer.value) {
    window.clearInterval(profilePollingTimer.value)
    profilePollingTimer.value = null
  }
}

function isProfileProcessing() {
  return profileJob.value?.status === 'PROCESSING'
}

function hasProfile() {
  return Boolean(profileResult.value?.profile)
}

async function handlePrimaryWorkflowAction(row) {
  const actionType = row.workflowState?.actionType
  if (actionType === 'parse') {
    await handleParse(row)
    return
  }
  if (actionType === 'vectorize') {
    await handleVectorize(row)
    return
  }
  if (actionType === 'profile') {
    await startProfileJobFromList(row)
    return
  }
  if (actionType === 'indexProfile') {
    await handleIndexProfile(row)
    return
  }
  if (actionType === 'chat') {
    goToChat(row)
  }
}

async function startProfileJobFromList(row) {
  activeAction.value = `profile-${row.id}`

  try {
    await startPaperProfileJob(row.id)
    profileDialogVisible.value = false
    ElMessage.success('文献画像已开始生成')
    await loadProfileStatuses()
  } catch (error) {
    ElMessage.error(`启动文献画像生成失败：${error.message}`)
  } finally {
    if (activeAction.value === `profile-${row.id}`) {
      activeAction.value = ''
    }
  }
}

async function handlePaperCommand(command, row) {
  if (command === 'assets') { await openAssets(row); return }
  if (command === 'agent') {
    agentLaunchPaper.value = row
    agentLaunchVisible.value = true
    return
  }
  if (command === 'edit') {
    openEditPaperDialog(row)
    return
  }
  if (command === 'profile') {
    await openProfileDialog(row)
    return
  }
  if (command === 'regenerateProfile') {
    await startProfileJobFromList(row)
    return
  }
  if (command === 'indexProfile') {
    await handleIndexProfile(row)
    return
  }
  if (command === 'move') {
    openMovePaperDialog(row)
    return
  }
  if (command === 'chat') {
    goToChat(row)
    return
  }
  if (command === 'delete') {
    await handleDeletePaper(row)
  }
}

async function openAssets(row) { assetPaper.value = row; assetDialogVisible.value = true; assetLoading.value = true; factQuery.value = ''; try { [paperAssets.value, visionJobStatus.value, reproductionFacts.value] = await Promise.all([listPaperAssets(row.id), getPaperAssetAnalysisStatus(row.id), listPaperReproductionFacts(row.id, includeModelInferredFacts.value)]) } catch (error) { ElMessage.error(`加载资产失败：${error.message}`) } finally { assetLoading.value = false } }
async function extractAssets() { if (!assetPaper.value) return; assetLoading.value = true; try { await extractPaperAssets(assetPaper.value.id); paperAssets.value = await listPaperAssets(assetPaper.value.id); ElMessage.success('页面资产已提取') } catch (error) { ElMessage.error(`资产提取失败：${error.message}`) } finally { assetLoading.value = false } }
function openAsset(asset) { selectedAsset.value = asset; assetPreviewVisible.value = true }
async function reviewAsset(asset, accepted) { try { const updated = accepted ? await confirmPaperAsset(assetPaper.value.id, asset.id) : await rejectPaperAsset(assetPaper.value.id, asset.id); Object.assign(asset, updated); ElMessage.success(accepted ? '资产已确认' : '资产已拒绝') } catch (error) { ElMessage.error(`更新资产失败：${error.message}`) } }
async function analyzeAsset(asset) { visionAnalyzing.value = true; try { Object.assign(asset, await analyzePaperAsset(assetPaper.value.id, asset.id)); ElMessage.success('视觉分析完成，请核对后确认') } catch (error) { ElMessage.error(`视觉分析失败：${error.message}`) } finally { visionAnalyzing.value = false } }
async function analyzeEligibleAssets() { visionAnalyzing.value = true; try { visionJobStatus.value = await analyzeEligiblePaperAssets(assetPaper.value.id); while (['RUNNING','WAITING'].includes(visionJobStatus.value.status)) { await new Promise((resolve) => window.setTimeout(resolve, 1500)); visionJobStatus.value = await getPaperAssetAnalysisStatus(assetPaper.value.id) } paperAssets.value = await listPaperAssets(assetPaper.value.id); ElMessage.success(`视觉分析完成：成功 ${visionJobStatus.value.processedItems || 0}，失败 ${visionJobStatus.value.failedItems || 0}`) } catch (error) { ElMessage.error(`批量视觉分析失败：${error.message}`) } finally { visionAnalyzing.value = false } }
async function loadFacts() { if (!assetPaper.value?.id) return; factLoading.value = true; try { reproductionFacts.value = await listPaperReproductionFacts(assetPaper.value.id, includeModelInferredFacts.value) } catch (error) { ElMessage.error(`加载复现事实失败：${error.message}`) } finally { factLoading.value = false } }
async function rebuildFacts() { if (!assetPaper.value?.id) return; factLoading.value = true; try { const result = await rebuildPaperReproductionFacts(assetPaper.value.id); reproductionFacts.value = await listPaperReproductionFacts(assetPaper.value.id, includeModelInferredFacts.value); ElMessage.success(`复现事实已构建：${result.factCount || 0} 条，冲突组 ${result.conflictGroupCount || 0} 个`) } catch (error) { ElMessage.error(`构建复现事实失败：${error.message}`) } finally { factLoading.value = false } }
async function searchFacts() { if (!assetPaper.value?.id || !factQuery.value.trim()) return; factLoading.value = true; try { reproductionFacts.value = await searchPaperReproductionFacts(assetPaper.value.id, factQuery.value.trim(), includeModelInferredFacts.value) } catch (error) { ElMessage.error(`检索复现事实失败：${error.message}`) } finally { factLoading.value = false } }

async function handleParse(row) {
  activeAction.value = `parse-${row.id}`

  try {
    const chunkCount = await parsePaper(row.id)
    ElMessage.success(`解析完成，生成 ${chunkCount} 个 chunk`)
    await reloadLibrary()
  } catch (error) {
    ElMessage.error(`解析失败：${error.message}`)
  } finally {
    activeAction.value = ''
  }
}

async function handleVectorize(row) {
  activeAction.value = `vectorize-${row.id}`

  try {
    await vectorizePaper(row.id)
    ElMessage.success('向量化完成，已写入 Qdrant')
    await reloadLibrary()
  } catch (error) {
    ElMessage.error(`向量化失败：${error.message}`)
  } finally {
    activeAction.value = ''
  }
}

async function handleIndexProfile(row) {
  activeAction.value = `indexProfile-${row.id}`
  try {
    const result = await indexPaperProfile(row.id)
    if (!result?.success) {
      throw new Error((result?.failures || []).join('；') || '部分画像或摘要索引失败')
    }
    ElMessage.success(`画像索引完成：1 个画像，${result.indexedSectionSummaries || 0} 条章节摘要`)
    await loadProfileStatuses()
  } catch (error) {
    ElMessage.error(`画像索引失败：${error.message}`)
  } finally {
    activeAction.value = ''
  }
}

async function handleDeletePaper(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除“${row.title || row.fileName || '未命名文献'}”吗？\n\n系统会同步删除本地 PDF、解析 chunk、Qdrant 向量、文献画像、章节摘要和画像任务；不会删除对话历史和 Research Idea。`,
      '删除文献',
      { type: 'warning', confirmButtonText: '删除文献', cancelButtonText: '取消' },
    )

    activeAction.value = `delete-${row.id}`
    await deletePaper(row.id)
    ElMessage.success('文献及其解析/向量/画像数据已删除')
    await reloadLibrary()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(`删除文献失败：${error.message || error}`)
    }
  } finally {
    if (activeAction.value === `delete-${row.id}`) {
      activeAction.value = ''
    }
  }
}

function goToChat(row) {
  router.push(buildPaperChatRoute(row))
}

function formatPaperMeta(row) {
  const parts = [row.authors, row.publishYear, row.journal, formatFileSize(row.fileSize)].filter(Boolean)
  return parts.length > 0 ? parts.join(' · ') : '暂无作者、年份或文件大小信息'
}

function formatFileSize(size) {
  if (!size) {
    return ''
  }

  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`
  }

  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

function formatDateTime(value) {
  if (!value) {
    return '-'
  }

  return String(value).replace('T', ' ').slice(0, 19)
}

function compactStatusText(status) {
  const normalizedStatus = String(status || '').toUpperCase()
  if (['COMPLETED', 'PARSED', 'VECTORIZED', 'SUCCESS'].includes(normalizedStatus)) {
    return '✓'
  }
  if (normalizedStatus === 'PROCESSING') {
    return '处理中'
  }
  if (['FAILED', 'ERROR'].includes(normalizedStatus)) {
    return '失败'
  }
  return '待处理'
}

function workflowStateTagType(workflowState) {
  if (workflowState?.ready) {
    return 'success'
  }
  if (workflowState?.failed) {
    return 'danger'
  }
  if (workflowState?.processing) {
    return 'warning'
  }
  return 'info'
}

function statusTagType(status) {
  const normalizedStatus = String(status || '').toUpperCase()

  if (['COMPLETED', 'PARSED', 'VECTORIZED', 'SUCCESS'].includes(normalizedStatus)) {
    return 'success'
  }

  if (['FAILED', 'ERROR'].includes(normalizedStatus)) {
    return 'danger'
  }

  if (normalizedStatus === 'PROCESSING') {
    return 'warning'
  }

  return 'info'
}

function isCompletedStatus(status) {
  const normalizedStatus = String(status || '').toUpperCase()
  return ['COMPLETED', 'PARSED', 'VECTORIZED', 'SUCCESS'].includes(normalizedStatus)
}

function findUncategorizedCategoryId() {
  return categories.value.find((category) => category.systemFlag || category.name === '未分类')?.id || null
}

onMounted(reloadLibrary)
onBeforeUnmount(() => {
  stopProfilePolling()
  stopProfileStatusPolling()
})
</script>
