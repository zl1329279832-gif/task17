package com.property.repair.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Purchase request for restocking spare parts.
 * Created automatically when inventory falls below safety stock, or manually by admin.
 */
@Data
@TableName("purchase_request")
public class PurchaseRequest {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** Unique purchase request number */
    private String purchaseNo;

    private Long partId;

    private Long communityId;

    private Integer quantity;

    /** 1=Low, 2=Normal, 3=High, 4=Urgent */
    private Integer urgency;

    /** PENDING, APPROVED, ORDERED, RECEIVED, REJECTED */
    private String status;

    private Long requestedBy;

    private Long approvedBy;

    private LocalDateTime approvedAt;

    /** Linked part request that triggered this purchase (if any) */
    private Long triggerRequestId;

    /** Linked order ID (if triggered by a specific order) */
    private Long triggerOrderId;

    private String remark;

    private LocalDateTime receivedAt;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
