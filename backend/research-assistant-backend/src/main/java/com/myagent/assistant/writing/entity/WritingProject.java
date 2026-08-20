package com.myagent.assistant.writing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("writing_project")
public class WritingProject {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String topic;
    private String documentType;
    private String targetLanguage;
    private Integer targetWordCount;
    private String citationStyle;
    private String selectedPaperIds;
    private String outlineJson;
    private String content;
    private String citationsJson;
    private String citationAuditJson;
    private String status;
    private String modelProvider;
    private String modelName;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
