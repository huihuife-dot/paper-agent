package com.myagent.assistant.paper.dto;

import lombok.Data;

/**
 * 文献书目信息编辑请求。
 *
 * 这里只允许修改用户维护的描述字段，不接收文件路径、解析状态或向量状态，
 * 避免一次普通编辑意外破坏文献的处理进度。
 */
@Data
public class PaperMetadataUpdateRequest {

    private String title;
    private String authors;
    private Integer publishYear;
    private String journal;
    private String keywords;
    private String abstractText;
    private String remark;
}
