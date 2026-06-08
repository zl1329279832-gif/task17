package com.property.repair.common.enums;

import lombok.Getter;

@Getter
public enum RoleType {

    OWNER("业主"),
    WORKER("维修人员"),
    SUPERVISOR("主管"),
    ADMIN("管理员");

    private final String description;

    RoleType(String description) {
        this.description = description;
    }
}
