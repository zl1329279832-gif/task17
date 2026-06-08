package com.property.repair.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Timeout types that trigger escalation.
 */
@Getter
@AllArgsConstructor
public enum TimeoutType {

    ACCEPT_TIMEOUT("ACCEPT_TIMEOUT", "Worker did not accept within deadline"),
    VISIT_TIMEOUT("VISIT_TIMEOUT", "Worker did not visit within deadline"),
    COMPLETE_TIMEOUT("COMPLETE_TIMEOUT", "Repair not completed within deadline");

    private final String code;
    private final String description;
}
