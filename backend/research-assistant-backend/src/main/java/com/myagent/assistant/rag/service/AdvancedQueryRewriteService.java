package com.myagent.assistant.rag.service;

import com.myagent.assistant.rag.dto.QueryRewriteResult;

/**
 * 高级 Query Rewrite 服务。
 *
 * 将用户的自然语言问题改写为更适合检索学术论文片段的 query，
 * 支持疑问句→陈述句改写、关键词提取和 HyDE 假设性答案生成。
 */
public interface AdvancedQueryRewriteService {

    /**
     * 对用户问题进行高级改写。
     *
     * @param question 用户原始问题（中文或英文）
     * @return 改写结果，包含陈述句 query、关键词 query、HyDE query
     */
    QueryRewriteResult rewrite(String question);
}
