package com.myagent.assistant.paper.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("paper_reproduction_spec")
public class PaperReproductionSpec {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long paperId;
    private String specJson;
    private String sourceRevision;
    private String specVersion;
    private Double criticalCoverage;
    private Double provenanceCoverage;
    private Integer unresolvedConflictCount;
    private Integer missingCriticalCount;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
