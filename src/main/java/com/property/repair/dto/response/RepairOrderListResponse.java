package com.property.repair.dto.response;

import com.property.repair.common.enums.OrderStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RepairOrderListResponse {

    private Long id;
    private String orderNo;
    private String title;
    private Integer urgency;
    private OrderStatus status;
    private String statusDescription;
    private String communityName;
    private String buildingName;
    private String unitNumber;
    private String categoryName;
    private String ownerName;
    private String workerName;
    private Integer reworkCount;
    private LocalDateTime createdAt;
    private LocalDateTime assignedAt;
    private LocalDateTime completedAt;
}
