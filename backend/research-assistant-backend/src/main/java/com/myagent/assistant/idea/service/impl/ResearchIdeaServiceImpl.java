package com.myagent.assistant.idea.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myagent.assistant.chat.entity.ChatMessage;
import com.myagent.assistant.chat.mapper.ChatMessageMapper;
import com.myagent.assistant.idea.dto.ResearchIdeaCreateRequest;
import com.myagent.assistant.idea.dto.ResearchIdeaDraftResponse;
import com.myagent.assistant.idea.dto.ResearchIdeaUpdateRequest;
import com.myagent.assistant.idea.dto.ResearchIdeaSaveTypeStatsResponse;
import com.myagent.assistant.idea.entity.ResearchIdea;
import com.myagent.assistant.idea.mapper.ResearchIdeaMapper;
import com.myagent.assistant.idea.service.ResearchIdeaService;
import com.myagent.assistant.llm.LlmService;
import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Research Idea 服务实现。
 */
@Service
public class ResearchIdeaServiceImpl implements ResearchIdeaService {

    private static final Logger log = LoggerFactory.getLogger(ResearchIdeaServiceImpl.class);

    private final ResearchIdeaMapper researchIdeaMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final LlmService llmService;
    private final ObjectMapper objectMapper;

    public ResearchIdeaServiceImpl(ResearchIdeaMapper researchIdeaMapper,
                                   ChatMessageMapper chatMessageMapper,
                                   LlmService llmService,
                                   ObjectMapper objectMapper) {
        this.researchIdeaMapper = researchIdeaMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.llmService = llmService;
        this.objectMapper = objectMapper;
    }




    @Override
    public ResearchIdeaDraftResponse draftFromSession(Long sessionId) {
        if (sessionId == null) {
            throw new RuntimeException("会话 ID 不能为空");
        }

        QueryWrapper<ChatMessage> wrapper = new QueryWrapper<>();
        wrapper.eq("session_id", sessionId);
        wrapper.orderByAsc("create_time");

        List<ChatMessage> messages = chatMessageMapper.selectList(wrapper);
        if (messages == null || messages.isEmpty()) {
            throw new RuntimeException("会话消息为空，无法生成 Research Idea 草稿");
        }

        String prompt = buildIdeaDraftPrompt(messages);

        String llmOutput = llmService.generateAnswer(prompt);

        ResearchIdeaDraftResponse draft = parseIdeaDraft(llmOutput);
        draft.setSourceType("rag_chat");
        draft.setSourceSessionId(sessionId);
        draft.setSourceMessageIds(collectMessageIds(messages));
        // 关联论文属于来源追溯信息，必须以系统保存的 sourcesJson 为准，
        // 不能采信大模型生成的 paperId，避免把来源编号或幻觉 ID 写入数据库。
        draft.setRelatedPaperIds(collectRelatedPaperIds(messages));
        draft.setRawLlmOutput(llmOutput);

        return draft;
    }



    /**
     * 收集本次会话中参与总结的消息 ID。
     */
    private List<Long> collectMessageIds(List<ChatMessage> messages) {
        List<Long> ids = new ArrayList<>();

        for (ChatMessage message : messages) {
            if (message.getId() != null) {
                ids.add(message.getId());
            }
        }

        return ids;
    }

    /**
     * 从 assistant 消息保存的真实 RAG sourcesJson 中提取关联论文 ID。
     *
     * sourcesJson 的顶层当前通常是数组，但这里递归遍历 JSON，兼容后续增加包装对象；
     * LinkedHashSet 同时保证去重和首次出现顺序稳定。
     */
    private String collectRelatedPaperIds(List<ChatMessage> messages) {
        Set<Long> paperIds = new LinkedHashSet<>();

        for (ChatMessage message : messages) {
            String sourcesJson = message.getSourcesJson();
            if (sourcesJson == null || sourcesJson.isBlank()) {
                continue;
            }

            try {
                collectPaperIds(objectMapper.readTree(sourcesJson), paperIds);
            } catch (Exception e) {
                log.warn("Research Idea 来源 JSON 解析失败，跳过消息 {} 的关联论文提取", message.getId(), e);
            }
        }

        return paperIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    private void collectPaperIds(JsonNode node, Set<Long> paperIds) {
        if (node == null || node.isNull()) {
            return;
        }

        if (node.isObject()) {
            JsonNode paperIdNode = node.get("paperId");
            if (paperIdNode != null) {
                Long paperId = parsePositiveLong(paperIdNode);
                if (paperId != null) {
                    paperIds.add(paperId);
                }
            }
            node.elements().forEachRemaining(child -> collectPaperIds(child, paperIds));
            return;
        }

        if (node.isArray()) {
            node.elements().forEachRemaining(child -> collectPaperIds(child, paperIds));
        }
    }

    private Long parsePositiveLong(JsonNode node) {
        try {
            long value = node.isNumber() ? node.longValue() : Long.parseLong(node.asText().trim());
            return value > 0 ? value : null;
        } catch (Exception ignored) {
            return null;
        }
    }



    /**
     * 从大模型输出中提取 JSON 对象。
     *
     * 有些模型可能会额外输出说明文字，这里取第一个 { 到最后一个 }。
     */
    private String extractJsonObject(String text) {
        int start = text.indexOf("{");
        int end = text.lastIndexOf("}");

        if (start < 0 || end < 0 || end <= start) {
            throw new RuntimeException("大模型返回内容不是 JSON 对象");
        }

        return text.substring(start, end + 1);
    }





    /**
     * 构造“从聊天会话总结 Research Idea 草稿”的 prompt。
     *
     * 要求大模型只返回 JSON，方便后端解析。
     */
    private String buildIdeaDraftPrompt(List<ChatMessage> messages) {
        StringBuilder conversation = new StringBuilder();

        for (ChatMessage message : messages) {
            conversation.append("消息ID: ").append(message.getId()).append("\n");
            conversation.append("角色: ").append(message.getRole()).append("\n");
            conversation.append("内容:\n").append(defaultIfBlank(message.getContent(), "")).append("\n");

            if (message.getSourcesJson() != null && !message.getSourcesJson().isBlank()) {
                conversation.append("sourcesJson:\n")
                        .append(message.getSourcesJson())
                        .append("\n");
            }

            conversation.append("\n---\n");
        }

        return """
                  你是一个论文/文献 AI 研究助手，任务是从一次 RAG 聊天会话中提炼 Research Idea 草稿。

                  请根据下面的聊天记录，判断其中是否包含值得保留的研究想法。
                  如果包含，请整理成一个结构化 Research Idea 草稿。
                  如果内容不足，也请给出一个尽量保守的草稿，不要编造聊天记录中没有的信息。

                  要求：
                  1. 必须围绕聊天记录和 sourcesJson，不要编造不存在的论文结论。
                  2. title 要简短，适合作为 Idea 列表标题。
                  3. originalContent 用于概括这个想法来自哪段讨论。
                  4. refinedContent 用于整理成较完整的研究想法正文。
                  5. innovationPoints 中用中文分号分隔多个创新点，例如："创新点A；创新点B"。禁止使用 JSON 数组。
                  6. researchQuestion 写可以继续研究的问题。
                  7. possibleMethod 中用中文分号分隔多个方法，例如："方法A；方法B"。禁止使用 JSON 数组。
                  8. tags 用中文逗号或英文逗号分隔，禁止使用 JSON 数组。
                  9. 不需要生成关联论文 ID，系统会直接从 sourcesJson 中提取可靠 paperId。
                  10. 只返回 JSON，不要返回 Markdown，不要用 ``` 包裹。
                  11. 所有字段的值都必须是字符串类型，严禁任何字段使用数组类型。

                  JSON 格式如下：
                  {
                    "title": "标题",
                    "originalContent": "原始讨论摘要",
                    "refinedContent": "整理后的研究想法",
                    "innovationPoints": "创新点A；创新点B",
                    "researchQuestion": "研究问题",
                    "possibleMethod": "方法A；方法B",
                    "tags": "标签1,标签2"
                  }

                  聊天记录如下：
                  %s
                  """.formatted(conversation.toString());
    }





    /**
     * 解析大模型返回的 JSON。
     *
     * 如果模型返回了非标准 JSON，则降级为只填 originalContent，
     * 避免接口直接失败。
     */
    private ResearchIdeaDraftResponse parseIdeaDraft(String llmOutput) {
        if (llmOutput == null || llmOutput.isBlank()) {
            throw new RuntimeException("大模型返回内容为空，无法生成 Research Idea 草稿");
        }

        try {
            String json = extractJsonObject(llmOutput);
            return objectMapper.readValue(json, ResearchIdeaDraftResponse.class);
        } catch (Exception e) {
            log.warn("Research Idea JSON 解析失败，使用兜底逻辑。LLM 输出前 500 字符: {}",
                    llmOutput == null ? "null" : llmOutput.substring(0, Math.min(llmOutput.length(), 500)), e);
            ResearchIdeaDraftResponse fallback = new ResearchIdeaDraftResponse();
            fallback.setTitle(buildTitle(llmOutput));
            fallback.setOriginalContent(llmOutput);
            fallback.setRefinedContent(llmOutput);
            fallback.setSourceType("rag_chat");
            return fallback;
        }
    }



    /**
     * 根据文本生成默认标题。
     *
     * 用于模型 JSON 解析失败时的兜底标题。
     */
    private String buildTitle(String text) {
        if (text == null || text.isBlank()) {
            return "来自 RAG 对话的研究想法";
        }

        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 30) {
            return normalized;
        }

        return normalized.substring(0, 30) + "...";
    }



    @Override
    public ResearchIdea saveDraftFromSession(Long sessionId) {
        if (sessionId == null) {
            throw new RuntimeException("会话 ID 不能为空");
        }

        // 同一个 RAG 会话只保存一条 Research Idea。
        // 如果用户重复点击“保存为 Idea”，直接返回已有记录，避免重复调用大模型和重复入库。
        ResearchIdea existingIdea = findSavedRagIdeaBySessionId(sessionId);
        if (existingIdea != null) {
            return existingIdea;
        }

        ResearchIdeaDraftResponse draft = draftFromSession(sessionId);

        ResearchIdeaCreateRequest request = new ResearchIdeaCreateRequest();
        request.setTitle(defaultIfBlank(draft.getTitle(), "来自 RAG 对话的研究想法"));
        request.setOriginalContent(draft.getOriginalContent());
        request.setRefinedContent(draft.getRefinedContent());
        request.setInnovationPoints(draft.getInnovationPoints());
        request.setResearchQuestion(draft.getResearchQuestion());
        request.setPossibleMethod(draft.getPossibleMethod());
        request.setTags(draft.getTags());
        request.setRelatedPaperIds(draft.getRelatedPaperIds());

        // 一键保存来自 RAG 会话，因此固定来源类型，便于后续按来源筛选。
        request.setSourceType("rag_chat");

        // 自动保存的内容先作为草稿，用户后续仍可编辑、完善或删除。
        request.setSaveType("draft");
        request.setSourceSessionId(sessionId);

        // 当前表结构只有单个 source_message_id，而一次会话草稿可能来自多条消息；
        // 本阶段先保留会话级追溯，不强行写入某一条消息 ID。
        request.setSourceMessageId(null);

        return create(request);
    }



    @Override
    public ResearchIdea getBySourceSessionId(Long sessionId) {
        if (sessionId == null) {
            throw new RuntimeException("会话 ID 不能为空");
        }

        return findSavedRagIdeaBySessionId(sessionId);
    }



    /**
     * 查询某个 RAG 会话是否已经保存过 Research Idea。
     */
    private ResearchIdea findSavedRagIdeaBySessionId(Long sessionId) {
        QueryWrapper<ResearchIdea> wrapper = new QueryWrapper<>();
        wrapper.eq("source_type", "rag_chat");
        wrapper.eq("source_session_id", sessionId);
        wrapper.orderByAsc("id");
        wrapper.last("LIMIT 1");
        return researchIdeaMapper.selectOne(wrapper);
    }



    @Override
    public ResearchIdea create(ResearchIdeaCreateRequest request) {
        if (request == null) {
            throw new RuntimeException("Research Idea 请求不能为空");
        }

        if (request.getTitle() == null || request.getTitle().isBlank()) {
            throw new RuntimeException("Research Idea 标题不能为空");
        }

        ResearchIdea idea = new ResearchIdea();
        idea.setTitle(request.getTitle());
        idea.setOriginalContent(request.getOriginalContent());
        idea.setRefinedContent(request.getRefinedContent());
        idea.setInnovationPoints(request.getInnovationPoints());
        idea.setResearchQuestion(request.getResearchQuestion());
        idea.setPossibleMethod(request.getPossibleMethod());
        idea.setTags(request.getTags());
        idea.setSourceType(defaultIfBlank(request.getSourceType(), "manual"));
        idea.setSaveType(defaultIfBlank(request.getSaveType(), "draft"));
        idea.setSourceSessionId(request.getSourceSessionId());
        idea.setSourceMessageId(request.getSourceMessageId());
        idea.setRelatedPaperIds(request.getRelatedPaperIds());
        idea.setCreateTime(LocalDateTime.now());
        idea.setUpdateTime(LocalDateTime.now());

        researchIdeaMapper.insert(idea);
        return idea;
    }

    @Override
    public List<ResearchIdea> list(String keyword, String sourceType, String saveType, Long sourceSessionId) {
        QueryWrapper<ResearchIdea> wrapper = new QueryWrapper<>();

        if (keyword != null && !keyword.isBlank()) {
            // keyword 只负责模糊搜索标题和标签，用 and 包住，避免后续精确筛选条件被 or 影响。
            wrapper.and(w -> w.like("title", keyword).or().like("tags", keyword));
        }

        if (sourceType != null && !sourceType.isBlank()) {
            wrapper.eq("source_type", sourceType);
        }

        if (saveType != null && !saveType.isBlank()) {
            wrapper.eq("save_type", saveType);
        }

        if (sourceSessionId != null) {
            wrapper.eq("source_session_id", sourceSessionId);
        }

        wrapper.orderByDesc("update_time");
        return researchIdeaMapper.selectList(wrapper);
    }

    @Override
    public ResearchIdea getById(Long id) {
        if (id == null) {
            throw new RuntimeException("Research Idea ID 不能为空");
        }

        ResearchIdea idea = researchIdeaMapper.selectById(id);
        if (idea == null) {
            throw new RuntimeException("Research Idea 不存在");
        }

        return idea;
    }

    @Override
    public ResearchIdea update(Long id, ResearchIdeaUpdateRequest request) {
        if (request == null) {
            throw new RuntimeException("Research Idea 更新请求不能为空");
        }

        ResearchIdea idea = getById(id);

        idea.setTitle(request.getTitle());
        idea.setOriginalContent(request.getOriginalContent());
        idea.setRefinedContent(request.getRefinedContent());
        idea.setInnovationPoints(request.getInnovationPoints());
        idea.setResearchQuestion(request.getResearchQuestion());
        idea.setPossibleMethod(request.getPossibleMethod());
        idea.setTags(request.getTags());
        idea.setSourceType(request.getSourceType());
        idea.setSaveType(request.getSaveType());
        idea.setSourceSessionId(request.getSourceSessionId());
        idea.setSourceMessageId(request.getSourceMessageId());
        idea.setRelatedPaperIds(request.getRelatedPaperIds());
        idea.setUpdateTime(LocalDateTime.now());

        researchIdeaMapper.updateById(idea);
        return getById(id);
    }

    @Override
    public ResearchIdea updateSaveType(Long id, String saveType) {
        if (id == null) {
            throw new RuntimeException("Research Idea ID 不能为空");
        }
        if (!isValidSaveType(saveType)) {
            throw new RuntimeException("saveType 只能是 draft、idea、todo、implemented");
        }

        ResearchIdea idea = getById(id);
        idea.setSaveType(saveType);
        idea.setUpdateTime(LocalDateTime.now());

        researchIdeaMapper.updateById(idea);
        return idea;
    }

    @Override
    public ResearchIdeaSaveTypeStatsResponse countBySaveType() {
        long draft = countBySaveTypeValue("draft");
        long idea = countBySaveTypeValue("idea");
        long todo = countBySaveTypeValue("todo");
        long implemented = countBySaveTypeValue("implemented");

        ResearchIdeaSaveTypeStatsResponse response = new ResearchIdeaSaveTypeStatsResponse();
        response.setDraft(draft);
        response.setIdea(idea);
        response.setTodo(todo);
        response.setImplemented(implemented);
        response.setTotal(draft + idea + todo + implemented);
        return response;
    }

    /**
     * 统计某个 saveType 下的 Research Idea 数量。
     */
    private long countBySaveTypeValue(String saveType) {
        QueryWrapper<ResearchIdea> wrapper = new QueryWrapper<>();
        wrapper.eq("save_type", saveType);
        Long count = researchIdeaMapper.selectCount(wrapper);
        return count == null ? 0L : count;
    }

    /**
     * 判断 Research Idea 保存类型是否合法。
     */
    private boolean isValidSaveType(String saveType) {
        return "draft".equals(saveType)
                || "idea".equals(saveType)
                || "todo".equals(saveType)
                || "implemented".equals(saveType);
    }

    @Override
    public void delete(Long id) {
        ResearchIdea idea = getById(id);
        researchIdeaMapper.deleteById(idea.getId());
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return value != null && !value.isBlank() ? value : defaultValue;
    }
}
