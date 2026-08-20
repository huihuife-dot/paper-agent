package com.myagent.assistant.writing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("writing_revision")
public class WritingRevision {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String revisionType;
    private String instruction;
    private String outlineSnapshot;
    private String contentSnapshot;
    private String citationsJson;
    private String modelProvider;
    private String modelName;
    private LocalDateTime createTime;
}
