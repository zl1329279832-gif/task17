package com.property.repair.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum PurchaseStatus {

    PENDING("PENDING", "Awaiting approval"),
    APPROVED("APPROVED", "Approved for ordering"),
    ORDERED("ORDERED", "Order placed with supplier"),
    RECEIVED("RECEIVED", "Parts received"),
    CANCELLED("CANCELLED", "Purchase cancelled");

    private final String code;
    private final String description;
}
