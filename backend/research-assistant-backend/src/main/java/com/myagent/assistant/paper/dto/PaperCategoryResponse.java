package com.myagent.assistant.paper.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PaperCategoryResponse {

    private Long id;
    private String name;
    private String description;
    private Integer sortOrder;
    private Boolean systemFlag;
    private Long paperCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
