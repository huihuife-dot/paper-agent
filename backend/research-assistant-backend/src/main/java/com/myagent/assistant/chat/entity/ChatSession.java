package com.myagent.assistant.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话会话实体。
 *
 * 一个 session 对应前端左侧的一条历史会话。
 */
@Data
@TableName("chat_session")
public class ChatSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 会话标题。
     * 默认可取用户问题的前若干个字。
     */
    private String title;

    /**
     * 关联文献 ID。
     * 当前可为空，后续用于“围绕某篇论文问答”。
     */
    private Long paperId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}