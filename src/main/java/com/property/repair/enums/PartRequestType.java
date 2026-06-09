package com.property.repair.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Type of spare part request.
 */
@Getter
@AllArgsConstructor
public enum PartRequestType {

    NORMAL("NORMAL", "Normal repair request"),
    REWORK("REWORK", "Rework second material request");

    private final String code;
    private final String description;
}
