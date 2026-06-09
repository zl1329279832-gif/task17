package com.property.repair.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("purchase_request")
public class PurchaseRequest {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String requestNo;

    private Long partId;

    private Long communityId;

    private Integer quantity;

    private String status;

    /** The requisition that triggered this purchase */
    private Long triggerRequisitionId;

    /** The repair order that triggered this purchase */
    private Long triggerOrderId;

    private LocalDateTime receivedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
