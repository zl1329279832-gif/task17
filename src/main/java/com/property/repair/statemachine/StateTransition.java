package com.property.repair.statemachine;

import com.property.repair.common.enums.OrderEvent;
import com.property.repair.common.enums.OrderStatus;
import com.property.repair.common.enums.RoleType;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Set;

@Data
@AllArgsConstructor
public class StateTransition {

    private OrderStatus fromStatus;

    private OrderEvent event;

    private OrderStatus toStatus;

    private Set<RoleType> allowedRoles;
}
