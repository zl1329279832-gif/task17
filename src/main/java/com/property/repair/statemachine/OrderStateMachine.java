package com.property.repair.statemachine;

import com.property.repair.common.enums.OrderEvent;
import com.property.repair.common.enums.OrderStatus;
import com.property.repair.common.enums.RoleType;
import com.property.repair.common.exception.BusinessException;
import com.property.repair.entity.RepairOrder;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class OrderStateMachine {

    private final Map<OrderStatus, Map<OrderEvent, StateTransition>> transitionTable = new EnumMap<>(OrderStatus.class);

    public OrderStateMachine() {
        initTransitions();
    }

    private void initTransitions() {
        // PENDING + DISPATCH -> ASSIGNED (ADMIN, null/system)
        addTransition(OrderStatus.PENDING, OrderEvent.DISPATCH, OrderStatus.ASSIGNED,
                new HashSet<>(Arrays.asList(RoleType.ADMIN, null)));

        // ASSIGNED + ACCEPT -> ACCEPTED (WORKER)
        addTransition(OrderStatus.ASSIGNED, OrderEvent.ACCEPT, OrderStatus.ACCEPTED,
                new HashSet<>(Collections.singletonList(RoleType.WORKER)));

        // ASSIGNED + REJECT_ACCEPT -> PENDING (WORKER)
        addTransition(OrderStatus.ASSIGNED, OrderEvent.REJECT_ACCEPT, OrderStatus.PENDING,
                new HashSet<>(Collections.singletonList(RoleType.WORKER)));

        // ASSIGNED + ESCALATE -> ESCALATED (null/system)
        addTransition(OrderStatus.ASSIGNED, OrderEvent.ESCALATE, OrderStatus.ESCALATED,
                new HashSet<>(Collections.singletonList(null)));

        // ESCALATED + REASSIGN -> ASSIGNED (SUPERVISOR, ADMIN)
        addTransition(OrderStatus.ESCALATED, OrderEvent.REASSIGN, OrderStatus.ASSIGNED,
                new HashSet<>(Arrays.asList(RoleType.SUPERVISOR, RoleType.ADMIN)));

        // ACCEPTED + START_REPAIR -> IN_PROGRESS (WORKER)
        addTransition(OrderStatus.ACCEPTED, OrderEvent.START_REPAIR, OrderStatus.IN_PROGRESS,
                new HashSet<>(Collections.singletonList(RoleType.WORKER)));

        // IN_PROGRESS + SUSPEND -> SUSPENDED (WORKER)
        addTransition(OrderStatus.IN_PROGRESS, OrderEvent.SUSPEND, OrderStatus.SUSPENDED,
                new HashSet<>(Collections.singletonList(RoleType.WORKER)));

        // SUSPENDED + RESUME -> IN_PROGRESS (WORKER)
        addTransition(OrderStatus.SUSPENDED, OrderEvent.RESUME, OrderStatus.IN_PROGRESS,
                new HashSet<>(Collections.singletonList(RoleType.WORKER)));

        // IN_PROGRESS + COMPLETE -> COMPLETED (WORKER)
        addTransition(OrderStatus.IN_PROGRESS, OrderEvent.COMPLETE, OrderStatus.COMPLETED,
                new HashSet<>(Collections.singletonList(RoleType.WORKER)));

        // COMPLETED + OWNER_CONFIRM -> CONFIRMED (OWNER)
        addTransition(OrderStatus.COMPLETED, OrderEvent.OWNER_CONFIRM, OrderStatus.CONFIRMED,
                new HashSet<>(Collections.singletonList(RoleType.OWNER)));

        // COMPLETED + OWNER_REJECT -> REJECTED (OWNER)
        addTransition(OrderStatus.COMPLETED, OrderEvent.OWNER_REJECT, OrderStatus.REJECTED,
                new HashSet<>(Collections.singletonList(RoleType.OWNER)));

        // REJECTED + REASSIGN -> REWORK (SUPERVISOR, ADMIN)
        addTransition(OrderStatus.REJECTED, OrderEvent.REASSIGN, OrderStatus.REWORK,
                new HashSet<>(Arrays.asList(RoleType.SUPERVISOR, RoleType.ADMIN)));

        // CONFIRMED + REQUEST_REWORK -> REWORK (OWNER)
        addTransition(OrderStatus.CONFIRMED, OrderEvent.REQUEST_REWORK, OrderStatus.REWORK,
                new HashSet<>(Collections.singletonList(RoleType.OWNER)));

        // EVALUATED + REQUEST_REWORK -> REWORK (OWNER)
        addTransition(OrderStatus.EVALUATED, OrderEvent.REQUEST_REWORK, OrderStatus.REWORK,
                new HashSet<>(Collections.singletonList(RoleType.OWNER)));

        // REWORK + DISPATCH -> ASSIGNED (ADMIN, null/system)
        addTransition(OrderStatus.REWORK, OrderEvent.DISPATCH, OrderStatus.ASSIGNED,
                new HashSet<>(Arrays.asList(RoleType.ADMIN, null)));

        // CONFIRMED + EVALUATE -> EVALUATED (OWNER)
        addTransition(OrderStatus.CONFIRMED, OrderEvent.EVALUATE, OrderStatus.EVALUATED,
                new HashSet<>(Collections.singletonList(RoleType.OWNER)));
    }

    private void addTransition(OrderStatus from, OrderEvent event, OrderStatus to, Set<RoleType> allowedRoles) {
        transitionTable
                .computeIfAbsent(from, k -> new EnumMap<>(OrderEvent.class))
                .put(event, new StateTransition(from, event, to, allowedRoles));
    }

    /**
     * Fire a state transition event on the given order.
     *
     * @param order      the repair order
     * @param event      the event to fire
     * @param operatorId the operator's user ID
     * @param role       the operator's role (null for system-triggered events)
     * @return the target status after transition
     * @throws BusinessException if the transition is not valid
     */
    public OrderStatus fire(RepairOrder order, OrderEvent event, Long operatorId, RoleType role) {
        OrderStatus currentStatus = order.getStatus();

        Map<OrderEvent, StateTransition> eventMap = transitionTable.get(currentStatus);
        if (eventMap == null) {
            throw new BusinessException("当前状态[" + currentStatus.getDescription() + "]不支持任何操作");
        }

        StateTransition transition = eventMap.get(event);
        if (transition == null) {
            throw new BusinessException("当前状态[" + currentStatus.getDescription()
                    + "]不支持操作[" + event.getDescription() + "]");
        }

        Set<RoleType> allowedRoles = transition.getAllowedRoles();
        if (!allowedRoles.contains(role)) {
            if (role == null) {
                throw new BusinessException("系统无权在当前状态执行操作[" + event.getDescription() + "]");
            }
            throw new BusinessException("角色[" + role.getDescription()
                    + "]无权执行操作[" + event.getDescription() + "]");
        }

        return transition.getToStatus();
    }

    /**
     * Check if an event can be fired from the given status.
     */
    public boolean canFire(OrderStatus currentStatus, OrderEvent event) {
        Map<OrderEvent, StateTransition> eventMap = transitionTable.get(currentStatus);
        return eventMap != null && eventMap.containsKey(event);
    }

    /**
     * Get the list of available events for the given status.
     */
    public List<OrderEvent> getAvailableEvents(OrderStatus status) {
        Map<OrderEvent, StateTransition> eventMap = transitionTable.get(status);
        if (eventMap == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(eventMap.keySet());
    }
}
