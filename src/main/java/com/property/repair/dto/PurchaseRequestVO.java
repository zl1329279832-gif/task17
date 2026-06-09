package com.property.repair.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Purchase request detail response.
 */
@Data
public class PurchaseRequestVO {

    private Long id;
    private String purchaseNo;
    private Long partId;
    private String partCode;
    private String partName;
    private Long communityId;
    private String communityName;
    private Integer quantity;
    private Integer urgency;
    private String status;
    private Long requestedBy;
    private String requestedByName;
    private Long approvedBy;
    private String approvedByName;
    private LocalDateTime approvedAt;
    private Long triggerRequestId;
    private Long triggerOrderId;
    private String remark;
    private LocalDateTime receivedAt;
    private LocalDateTime createdAt;
}
