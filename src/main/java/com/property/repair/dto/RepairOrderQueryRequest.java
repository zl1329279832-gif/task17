package com.property.repair.dto;

import lombok.Data;

@Data
public class RepairOrderQueryRequest {

    private String status;
    private String problemType;
    private Long communityId;
    private Long buildingId;
    private Long ownerId;
    private Long workerId;
    private String keyword;

    /** Pagination */
    private Integer page = 1;
    private Integer size = 10;
}
