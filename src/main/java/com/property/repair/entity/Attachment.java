package com.property.repair.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("attachment")
public class Attachment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private String fileName;

    private String fileType;

    private Long fileSize;

    private String fileUrl;

    /** SUBMIT, PROCESS, COMPLETE, REWORK */
    private String stage;

    private Long uploaderId;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
