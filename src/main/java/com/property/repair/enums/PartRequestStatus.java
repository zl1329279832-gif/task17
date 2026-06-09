package com.property.repair.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Spare part request status lifecycle.
 *
 * PENDING → APPROVED → PARTIALLY_ISSUED → ISSUED
 *                       ↓
 *                    RETURNED
 * PENDING → CANCELLED
 */
@Getter
@AllArgsConstructor
public enum PartRequestStatus {

    PENDING("PENDING", "Awaiting approval"),
    APPROVED("APPROVED", "Approved, ready to issue"),
    PARTIALLY_ISSUED("PARTIALLY_ISSUED", "Some items issued"),
    ISSUED("ISSUED", "All items issued"),
    RETURNED("RETURNED", "Parts returned"),
    CANCELLED("CANCELLED", "Request cancelled");

    private final String code;
    private final String description;

    public boolean isTerminal() {
        return this == ISSUED || this == RETURNED || this == CANCELLED;
    }
}
