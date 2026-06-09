package com.property.repair.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("spare_part_requisition")
public class SparePartRequisition {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String requisitionNo;

    private Long orderId;

    private Long workerId;

    /** Non-null when this is a second requisition during rework */
    private Long reworkOrderId;

    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
