package com.property.repair.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RequisitionStatus {

    PENDING("PENDING", "Awaiting stock availability"),
    APPROVED("APPROVED", "Approved, ready to issue"),
    ISSUED("ISSUED", "Parts issued to worker"),
    COMPLETED("COMPLETED", "Parts consumed or returned"),
    REJECTED("REJECTED", "Requisition rejected");

    private final String code;
    private final String description;
}
