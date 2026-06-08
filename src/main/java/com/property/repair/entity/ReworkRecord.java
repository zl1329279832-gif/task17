package com.property.repair.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rework_record")
public class ReworkRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long initiatorId;

    private String reason;

    private String fromStatus;

    private Long previousWorkerId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
