package com.property.repair.dto.request;

import lombok.Data;

@Data
public class RepairOrderQueryRequest {

    private Integer pageNum = 1;

    private Integer pageSize = 10;

    private Long communityId;

    private Long buildingId;

    private Long categoryId;

    private String status;

    private String keyword;

    private Long workerId;

    private Long ownerId;

    private Integer urgency;

    private String orderBy;

    private String orderDirection = "DESC";
}
