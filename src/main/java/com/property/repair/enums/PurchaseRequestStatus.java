package com.property.repair.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Purchase request status lifecycle.
 *
 * PENDING → APPROVED → ORDERED → RECEIVED
 * PENDING → REJECTED
 */
@Getter
@AllArgsConstructor
public enum PurchaseRequestStatus {

    PENDING("PENDING", "Awaiting approval"),
    APPROVED("APPROVED", "Approved for purchase"),
    ORDERED("ORDERED", "Purchase order placed"),
    RECEIVED("RECEIVED", "Parts received and stocked"),
    REJECTED("REJECTED", "Purchase rejected");

    private final String code;
    private final String description;

    public boolean isTerminal() {
        return this == RECEIVED || this == REJECTED;
    }
}
