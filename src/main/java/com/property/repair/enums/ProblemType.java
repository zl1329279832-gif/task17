package com.property.repair.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Problem categories for repair orders.
 * Used for auto-dispatch skill matching.
 */
@Getter
@AllArgsConstructor
public enum ProblemType {

    PLUMBING("PLUMBING", "Water & pipe issues"),
    ELECTRICAL("ELECTRICAL", "Electrical & wiring"),
    CIVIL("CIVIL", "Wall, floor, ceiling repairs"),
    FACILITY("FACILITY", "Elevator, gate, shared facilities"),
    OTHER("OTHER", "Other / uncategorized");

    private final String code;
    private final String description;
}
