package com.property.repair.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("attachment")
public class Attachment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long uploaderId;

    private String fileName;

    private String filePath;

    private Long fileSize;

    private String fileType;

    private String usageType;

    private Long progressId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
