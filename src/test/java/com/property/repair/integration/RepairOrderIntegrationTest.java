package com.property.repair.integration;

import com.property.repair.common.enums.OrderEvent;
import com.property.repair.common.enums.OrderStatus;
import com.property.repair.common.enums.RoleType;
import com.property.repair.common.exception.BusinessException;
import com.property.repair.entity.RepairOrder;
import com.property.repair.statemachine.OrderStateMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests simulating the full repair order lifecycle using the real OrderStateMachine.
 * No Spring context or database needed -- the state machine is a pure POJO.
 */
class RepairOrderIntegrationTest {

    private OrderStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new OrderStateMachine();
    }

    private RepairOrder createPendingOrder() {
        RepairOrder order = new RepairOrder();
        order.setId(1L);
        order.setOrderNo("RO20260608001");
        order.setOwnerId(10L);
        order.setCommunityId(1L);
        order.setBuildingId(100L);
        order.setCategoryId(5L);
        order.setTitle("Integration test order");
        order.setDescription("Full lifecycle test");
        order.setUrgency(2);
        order.setStatus(OrderStatus.PENDING);
        order.setReworkCount(0);
        order.setVersion(0);
        return order;
    }

    @Test
    @DisplayName("1. Full lifecycle: PENDING -> ASSIGNED -> ACCEPTED -> IN_PROGRESS -> COMPLETED -> CONFIRMED -> EVALUATED")
    void testFullLifecycle() {
        RepairOrder order = createPendingOrder();

        // Step 1: DISPATCH (by ADMIN)
        OrderStatus status = stateMachine.fire(order, OrderEvent.DISPATCH, 1L, RoleType.ADMIN);
        assertEquals(OrderStatus.ASSIGNED, status);
        order.setStatus(status);

        // Step 2: ACCEPT (by WORKER)
        status = stateMachine.fire(order, OrderEvent.ACCEPT, 20L, RoleType.WORKER);
        assertEquals(OrderStatus.ACCEPTED, status);
        order.setStatus(status);

        // Step 3: START_REPAIR (by WORKER)
        status = stateMachine.fire(order, OrderEvent.START_REPAIR, 20L, RoleType.WORKER);
        assertEquals(OrderStatus.IN_PROGRESS, status);
        order.setStatus(status);

        // Step 4: COMPLETE (by WORKER)
        status = stateMachine.fire(order, OrderEvent.COMPLETE, 20L, RoleType.WORKER);
        assertEquals(OrderStatus.COMPLETED, status);
        order.setStatus(status);

        // Step 5: OWNER_CONFIRM (by OWNER)
        status = stateMachine.fire(order, OrderEvent.OWNER_CONFIRM, 10L, RoleType.OWNER);
        assertEquals(OrderStatus.CONFIRMED, status);
        order.setStatus(status);

        // Step 6: EVALUATE (by OWNER)
        status = stateMachine.fire(order, OrderEvent.EVALUATE, 10L, RoleType.OWNER);
        assertEquals(OrderStatus.EVALUATED, status);
        order.setStatus(status);

        // Verify final state
        assertEquals(OrderStatus.EVALUATED, order.getStatus());
    }

    @Test
    @DisplayName("2. Escalation flow: PENDING -> ASSIGNED -> ESCALATED -> REASSIGN -> ASSIGNED -> ACCEPTED -> ...")
    void testEscalationFlow() {
        RepairOrder order = createPendingOrder();

        // Step 1: DISPATCH (by system)
        OrderStatus status = stateMachine.fire(order, OrderEvent.DISPATCH, null, null);
        assertEquals(OrderStatus.ASSIGNED, status);
        order.setStatus(status);

        // Step 2: Timeout -> ESCALATE (by system, null role)
        status = stateMachine.fire(order, OrderEvent.ESCALATE, null, null);
        assertEquals(OrderStatus.ESCALATED, status);
        order.setStatus(status);

        // Step 3: REASSIGN (by SUPERVISOR)
        status = stateMachine.fire(order, OrderEvent.REASSIGN, 30L, RoleType.SUPERVISOR);
        assertEquals(OrderStatus.ASSIGNED, status);
        order.setStatus(status);

        // Step 4: ACCEPT (by a new WORKER)
        status = stateMachine.fire(order, OrderEvent.ACCEPT, 21L, RoleType.WORKER);
        assertEquals(OrderStatus.ACCEPTED, status);
        order.setStatus(status);

        // Step 5: START_REPAIR
        status = stateMachine.fire(order, OrderEvent.START_REPAIR, 21L, RoleType.WORKER);
        assertEquals(OrderStatus.IN_PROGRESS, status);
        order.setStatus(status);

        // Step 6: COMPLETE
        status = stateMachine.fire(order, OrderEvent.COMPLETE, 21L, RoleType.WORKER);
        assertEquals(OrderStatus.COMPLETED, status);
        order.setStatus(status);

        // Step 7: OWNER_CONFIRM
        status = stateMachine.fire(order, OrderEvent.OWNER_CONFIRM, 10L, RoleType.OWNER);
        assertEquals(OrderStatus.CONFIRMED, status);
        order.setStatus(status);

        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
    }

    @Test
    @DisplayName("3. Rework flow: ... -> COMPLETED -> CONFIRMED -> REQUEST_REWORK -> REWORK -> DISPATCH -> ASSIGNED -> ...")
    void testReworkFlow() {
        RepairOrder order = createPendingOrder();

        // Fast-forward to COMPLETED through normal flow
        order.setStatus(stateMachine.fire(order, OrderEvent.DISPATCH, 1L, RoleType.ADMIN));
        order.setStatus(stateMachine.fire(order, OrderEvent.ACCEPT, 20L, RoleType.WORKER));
        order.setStatus(stateMachine.fire(order, OrderEvent.START_REPAIR, 20L, RoleType.WORKER));
        order.setStatus(stateMachine.fire(order, OrderEvent.COMPLETE, 20L, RoleType.WORKER));
        assertEquals(OrderStatus.COMPLETED, order.getStatus());

        // OWNER_CONFIRM
        order.setStatus(stateMachine.fire(order, OrderEvent.OWNER_CONFIRM, 10L, RoleType.OWNER));
        assertEquals(OrderStatus.CONFIRMED, order.getStatus());

        // REQUEST_REWORK (by OWNER)
        order.setStatus(stateMachine.fire(order, OrderEvent.REQUEST_REWORK, 10L, RoleType.OWNER));
        assertEquals(OrderStatus.REWORK, order.getStatus());

        // DISPATCH again (by ADMIN)
        order.setStatus(stateMachine.fire(order, OrderEvent.DISPATCH, 1L, RoleType.ADMIN));
        assertEquals(OrderStatus.ASSIGNED, order.getStatus());

        // Continue normal flow after rework
        order.setStatus(stateMachine.fire(order, OrderEvent.ACCEPT, 20L, RoleType.WORKER));
        assertEquals(OrderStatus.ACCEPTED, order.getStatus());

        order.setStatus(stateMachine.fire(order, OrderEvent.START_REPAIR, 20L, RoleType.WORKER));
        assertEquals(OrderStatus.IN_PROGRESS, order.getStatus());

        order.setStatus(stateMachine.fire(order, OrderEvent.COMPLETE, 20L, RoleType.WORKER));
        assertEquals(OrderStatus.COMPLETED, order.getStatus());

        // This time owner confirms and evaluates
        order.setStatus(stateMachine.fire(order, OrderEvent.OWNER_CONFIRM, 10L, RoleType.OWNER));
        assertEquals(OrderStatus.CONFIRMED, order.getStatus());

        order.setStatus(stateMachine.fire(order, OrderEvent.EVALUATE, 10L, RoleType.OWNER));
        assertEquals(OrderStatus.EVALUATED, order.getStatus());
    }

    @Test
    @DisplayName("4. Rejection flow: COMPLETED -> REJECTED -> REASSIGN -> REWORK -> DISPATCH -> ASSIGNED")
    void testRejectionFlow() {
        RepairOrder order = createPendingOrder();

        // Fast-forward to COMPLETED
        order.setStatus(stateMachine.fire(order, OrderEvent.DISPATCH, 1L, RoleType.ADMIN));
        order.setStatus(stateMachine.fire(order, OrderEvent.ACCEPT, 20L, RoleType.WORKER));
        order.setStatus(stateMachine.fire(order, OrderEvent.START_REPAIR, 20L, RoleType.WORKER));
        order.setStatus(stateMachine.fire(order, OrderEvent.COMPLETE, 20L, RoleType.WORKER));
        assertEquals(OrderStatus.COMPLETED, order.getStatus());

        // OWNER_REJECT
        order.setStatus(stateMachine.fire(order, OrderEvent.OWNER_REJECT, 10L, RoleType.OWNER));
        assertEquals(OrderStatus.REJECTED, order.getStatus());

        // REASSIGN (by ADMIN) -> REWORK
        order.setStatus(stateMachine.fire(order, OrderEvent.REASSIGN, 1L, RoleType.ADMIN));
        assertEquals(OrderStatus.REWORK, order.getStatus());

        // DISPATCH again
        order.setStatus(stateMachine.fire(order, OrderEvent.DISPATCH, 1L, RoleType.ADMIN));
        assertEquals(OrderStatus.ASSIGNED, order.getStatus());
    }

    @Test
    @DisplayName("5. Suspend and resume flow: IN_PROGRESS -> SUSPENDED -> IN_PROGRESS -> COMPLETED")
    void testSuspendResumeFlow() {
        RepairOrder order = createPendingOrder();

        // Fast-forward to IN_PROGRESS
        order.setStatus(stateMachine.fire(order, OrderEvent.DISPATCH, 1L, RoleType.ADMIN));
        order.setStatus(stateMachine.fire(order, OrderEvent.ACCEPT, 20L, RoleType.WORKER));
        order.setStatus(stateMachine.fire(order, OrderEvent.START_REPAIR, 20L, RoleType.WORKER));
        assertEquals(OrderStatus.IN_PROGRESS, order.getStatus());

        // SUSPEND
        order.setStatus(stateMachine.fire(order, OrderEvent.SUSPEND, 20L, RoleType.WORKER));
        assertEquals(OrderStatus.SUSPENDED, order.getStatus());

        // RESUME
        order.setStatus(stateMachine.fire(order, OrderEvent.RESUME, 20L, RoleType.WORKER));
        assertEquals(OrderStatus.IN_PROGRESS, order.getStatus());

        // COMPLETE
        order.setStatus(stateMachine.fire(order, OrderEvent.COMPLETE, 20L, RoleType.WORKER));
        assertEquals(OrderStatus.COMPLETED, order.getStatus());
    }

    @Test
    @DisplayName("6. Evaluated rework flow: EVALUATED -> REQUEST_REWORK -> REWORK")
    void testEvaluatedReworkFlow() {
        RepairOrder order = createPendingOrder();

        // Fast-forward to EVALUATED
        order.setStatus(stateMachine.fire(order, OrderEvent.DISPATCH, 1L, RoleType.ADMIN));
        order.setStatus(stateMachine.fire(order, OrderEvent.ACCEPT, 20L, RoleType.WORKER));
        order.setStatus(stateMachine.fire(order, OrderEvent.START_REPAIR, 20L, RoleType.WORKER));
        order.setStatus(stateMachine.fire(order, OrderEvent.COMPLETE, 20L, RoleType.WORKER));
        order.setStatus(stateMachine.fire(order, OrderEvent.OWNER_CONFIRM, 10L, RoleType.OWNER));
        order.setStatus(stateMachine.fire(order, OrderEvent.EVALUATE, 10L, RoleType.OWNER));
        assertEquals(OrderStatus.EVALUATED, order.getStatus());

        // REQUEST_REWORK after evaluation
        order.setStatus(stateMachine.fire(order, OrderEvent.REQUEST_REWORK, 10L, RoleType.OWNER));
        assertEquals(OrderStatus.REWORK, order.getStatus());

        // Can continue with DISPATCH
        order.setStatus(stateMachine.fire(order, OrderEvent.DISPATCH, 1L, RoleType.ADMIN));
        assertEquals(OrderStatus.ASSIGNED, order.getStatus());
    }

    @Test
    @DisplayName("7. Worker reject accept -> back to PENDING -> DISPATCH again")
    void testRejectAcceptFlow() {
        RepairOrder order = createPendingOrder();

        // DISPATCH
        order.setStatus(stateMachine.fire(order, OrderEvent.DISPATCH, 1L, RoleType.ADMIN));
        assertEquals(OrderStatus.ASSIGNED, order.getStatus());

        // Worker rejects
        order.setStatus(stateMachine.fire(order, OrderEvent.REJECT_ACCEPT, 20L, RoleType.WORKER));
        assertEquals(OrderStatus.PENDING, order.getStatus());

        // DISPATCH again to a different worker
        order.setStatus(stateMachine.fire(order, OrderEvent.DISPATCH, 1L, RoleType.ADMIN));
        assertEquals(OrderStatus.ASSIGNED, order.getStatus());

        // New worker accepts
        order.setStatus(stateMachine.fire(order, OrderEvent.ACCEPT, 21L, RoleType.WORKER));
        assertEquals(OrderStatus.ACCEPTED, order.getStatus());
    }

    @Test
    @DisplayName("8. Invalid transition in lifecycle throws BusinessException")
    void testInvalidTransitionInLifecycle() {
        RepairOrder order = createPendingOrder();

        // Try to COMPLETE a PENDING order - should fail
        assertThrows(BusinessException.class, () ->
                stateMachine.fire(order, OrderEvent.COMPLETE, 20L, RoleType.WORKER));

        // DISPATCH first
        order.setStatus(stateMachine.fire(order, OrderEvent.DISPATCH, 1L, RoleType.ADMIN));

        // Try to COMPLETE an ASSIGNED order - should fail
        assertThrows(BusinessException.class, () ->
                stateMachine.fire(order, OrderEvent.COMPLETE, 20L, RoleType.WORKER));
    }
}
