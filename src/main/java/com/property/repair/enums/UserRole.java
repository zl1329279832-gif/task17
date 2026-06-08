package com.property.repair.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * System user roles.
 */
@Getter
@AllArgsConstructor
public enum UserRole {

    OWNER("OWNER", "Property owner / reporter"),
    WORKER("WORKER", "Maintenance worker"),
    SUPERVISOR("SUPERVISOR", "Maintenance supervisor"),
    ADMIN("ADMIN", "Property administrator");

    private final String code;
    private final String description;
}
