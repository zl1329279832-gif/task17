package com.property.repair.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Spare part request detail response.
 */
@Data
public class PartRequestVO {

    private Long id;
    private Long orderId;
    private String orderNo;
    private Long workerId;
    private String workerName;
    private String requestNo;
    private String requestType;
    private String status;
    private String remark;
    private Long approvedBy;
    private String approvedByName;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;

    private List<PartRequestItemVO> items;

    @Data
    public static class PartRequestItemVO {
        private Long id;
        private Long partId;
        private String partCode;
        private String partName;
        private String specification;
        private String unit;
        private Integer requestedQty;
        private Integer issuedQty;
        private Integer returnedQty;
        private Integer consumedQty;
        private Integer availableStock;
        private String status;
        private Integer critical;
    }
}
