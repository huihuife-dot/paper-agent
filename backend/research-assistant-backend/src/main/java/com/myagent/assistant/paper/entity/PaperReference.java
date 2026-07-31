package com.myagent.assistant.paper.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("paper_reference")
public class PaperReference {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long categoryId;
    private String title;
    private String authors;
    private Integer publishYear;
    private String journal;
    private String keywords;
    private String abstractText;
    private String fileName;
    private String filePath;
    private String fileType;
    private Long fileSize;
    private LocalDateTime uploadTime;
    private String parseStatus;
    private String vectorStatus;
    private String remark;

    @TableField(exist = false)
    private String categoryName;
}
