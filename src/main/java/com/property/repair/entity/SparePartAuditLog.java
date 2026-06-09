package com.property.repair.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("spare_part_audit_log")
public class SparePartAuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long partId;

    private Long inventoryId;

    private Long requisitionId;

    private Long orderId;

    private String action;

    private Integer quantityChange;

    private Integer beforeQuantity;

    private Integer afterQuantity;

    private Long operatorId;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
