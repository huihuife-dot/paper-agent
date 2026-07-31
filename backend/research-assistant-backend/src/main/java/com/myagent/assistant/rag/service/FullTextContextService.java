package com.myagent.assistant.rag.service;

import com.myagent.assistant.rag.context.FullTextContext;

/**
 * 单篇论文全文解析上下文构造服务。
 */
public interface FullTextContextService {

    /**
     * 根据单篇论文 ID 构造按章节组织的正文上下文。
     */
    FullTextContext buildContext(Long paperId);
}
