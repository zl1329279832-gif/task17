package com.property.repair.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Dispatch type — how a worker was assigned.
 */
@Getter
@AllArgsConstructor
public enum DispatchType {

    AUTO("AUTO", "System auto-dispatched based on strategy"),
    MANUAL("MANUAL", "Manually assigned by admin/supervisor"),
    TRANSFER("TRANSFER", "Transferred from another worker"),
    ESCALATION("ESCALATION", "Escalated and reassigned");

    private final String code;
    private final String description;
}
