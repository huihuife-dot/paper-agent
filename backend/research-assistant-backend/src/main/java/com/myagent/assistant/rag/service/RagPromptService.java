package com.myagent.assistant.rag.service;

import com.myagent.assistant.rag.context.FullTextContext;
import com.myagent.assistant.rag.context.HybridRagContext;
import com.myagent.assistant.rag.context.StructuredEvidenceContext;
import com.myagent.assistant.rag.dto.RagSource;

import java.util.List;

/**
 * RAG Prompt 构造服务。
 *
 * 负责把用户问题和检索到的文献片段 sources
 * 组装成后续可以发送给大模型的 prompt。
 */
public interface RagPromptService {

    /**
     * 构造 RAG 问答 prompt。
     *
     * @param question 用户问题
     * @param sources  检索到的文献片段
     * @return 可发送给大模型的 prompt 文本
     */
    String buildPrompt(String question, List<RagSource> sources);

    /**
     * 构造单篇论文全文解析上下文 prompt。
     *
     * @param question 用户问题
     * @param context  按章节组织的单篇论文正文上下文
     * @return 可发送给大模型的 prompt 文本
     */
    String buildFullTextPrompt(String question, FullTextContext context);

    /**
     * 构造多篇论文混合 RAG prompt。
     *
     * @param question 用户问题
     * @param context  按论文组织的文献画像、章节摘要和原文证据上下文
     * @return 可发送给大模型的 prompt 文本
     */
    String buildHybridPrompt(String question, HybridRagContext context);

    /**
     * 构造全库文献发现模式 prompt。
     *
     * 不指定论文范围，从全库检索到的 sources 中回答问题，
     * 并引导模型列出最相关的论文。
     *
     * @param question 用户问题
     * @param sources  全库检索到的文献片段
     * @return 可发送给大模型的 prompt 文本
     */
    String buildLibraryDiscoveryPrompt(String question, List<RagSource> sources);

    /**
     * 构造结构化优先问答 Prompt，证据可能来自目录、画像、知识单元、章节或 RAG 补漏。
     */
    String buildStructuredPrompt(String question, StructuredEvidenceContext context);
}
