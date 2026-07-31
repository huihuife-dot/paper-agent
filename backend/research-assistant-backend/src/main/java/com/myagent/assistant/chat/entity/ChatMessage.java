package com.myagent.assistant.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话消息实体。
 *
 * 一次 RAG 问答会写入两条消息：
 * 1. role=user，保存用户问题
 * 2. role=assistant，保存模型回答
 */
@Data
@TableName("chat_message")
public class ChatMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属会话 ID。
     */
    private Long sessionId;

    /**
     * 消息角色：user / assistant。
     */
    private String role;

    /**
     * 消息内容。
     */
    private String content;

    /**
     * 模型供应商。
     * user 消息可为空，assistant 消息填写。
     */
    private String modelProvider;

    /**
     * 模型名称。
     * user 消息可为空，assistant 消息填写。
     */
    private String modelName;

    /**
     * RAG sources JSON 摘要。
     * user 消息可为空，assistant 消息填写。
     */
    private String sourcesJson;

    private LocalDateTime createTime;
}