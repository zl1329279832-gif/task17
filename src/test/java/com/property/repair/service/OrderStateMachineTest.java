package com.property.repair.service;

import com.property.repair.common.enums.OrderEvent;
import com.property.repair.common.enums.OrderStatus;
import com.property.repair.common.enums.RoleType;
import com.property.repair.common.exception.BusinessException;
import com.property.repair.entity.RepairOrder;
import com.property.repair.statemachine.OrderStateMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OrderStateMachine.
 * OrderStateMachine has a no-arg constructor and no injected dependencies,
 * so pure JUnit tests are used (no Spring context or Mockito needed).
 */
class OrderStateMachineTest {

    private OrderStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new OrderStateMachine();
    }

    private RepairOrder createOrderWithStatus(OrderStatus status) {
        RepairOrder order = new RepairOrder();
        order.setId(1L);
        order.setOrderNo("RO20260608001");
        order.setStatus(status);
        return order;
    }

    // ===================== Valid Transitions =====================

    @Test
    @DisplayName("1. PENDING + DISPATCH -> ASSIGNED (ADMIN)")
    void testPendingToAssigned() {
        RepairOrder order = createOrderWithStatus(OrderStatus.PENDING);
        OrderStatus result = stateMachine.fire(order, OrderEvent.DISPATCH, 1L, RoleType.ADMIN);
        assertEquals(OrderStatus.ASSIGNED, result);
    }

    @Test
    @DisplayName("1b. PENDING + DISPATCH -> ASSIGNED (null/system)")
    void testPendingToAssignedBySystem() {
        RepairOrder order = createOrderWithStatus(OrderStatus.PENDING);
        OrderStatus result = stateMachine.fire(order, OrderEvent.DISPATCH, null, null);
        assertEquals(OrderStatus.ASSIGNED, result);
    }

    @Test
    @DisplayName("2. ASSIGNED + ACCEPT -> ACCEPTED (WORKER)")
    void testAssignedToAccepted() {
        RepairOrder order = createOrderWithStatus(OrderStatus.ASSIGNED);
        OrderStatus result = stateMachine.fire(order, OrderEvent.ACCEPT, 2L, RoleType.WORKER);
        assertEquals(OrderStatus.ACCEPTED, result);
    }

    @Test
    @DisplayName("3. ASSIGNED + ESCALATE -> ESCALATED (null/system)")
    void testAssignedToEscalated() {
        RepairOrder order = createOrderWithStatus(OrderStatus.ASSIGNED);
        OrderStatus result = stateMachine.fire(order, OrderEvent.ESCALATE, null, null);
        assertEquals(OrderStatus.ESCALATED, result);
    }

    @Test
    @DisplayName("4. ACCEPTED + START_REPAIR -> IN_PROGRESS (WORKER)")
    void testAcceptedToInProgress() {
        RepairOrder order = createOrderWithStatus(OrderStatus.ACCEPTED);
        OrderStatus result = stateMachine.fire(order, OrderEvent.START_REPAIR, 2L, RoleType.WORKER);
        assertEquals(OrderStatus.IN_PROGRESS, result);
    }

    @Test
    @DisplayName("5. IN_PROGRESS + SUSPEND -> SUSPENDED (WORKER)")
    void testInProgressToSuspended() {
        RepairOrder order = createOrderWithStatus(OrderStatus.IN_PROGRESS);
        OrderStatus result = stateMachine.fire(order, OrderEvent.SUSPEND, 2L, RoleType.WORKER);
        assertEquals(OrderStatus.SUSPENDED, result);
    }

    @Test
    @DisplayName("6. SUSPENDED + RESUME -> IN_PROGRESS (WORKER)")
    void testSuspendedToInProgress() {
        RepairOrder order = createOrderWithStatus(OrderStatus.SUSPENDED);
        OrderStatus result = stateMachine.fire(order, OrderEvent.RESUME, 2L, RoleType.WORKER);
        assertEquals(OrderStatus.IN_PROGRESS, result);
    }

    @Test
    @DisplayName("7. IN_PROGRESS + COMPLETE -> COMPLETED (WORKER)")
    void testInProgressToCompleted() {
        RepairOrder order = createOrderWithStatus(OrderStatus.IN_PROGRESS);
        OrderStatus result = stateMachine.fire(order, OrderEvent.COMPLETE, 2L, RoleType.WORKER);
        assertEquals(OrderStatus.COMPLETED, result);
    }

    @Test
    @DisplayName("8. COMPLETED + OWNER_CONFIRM -> CONFIRMED (OWNER)")
    void testCompletedToConfirmed() {
        RepairOrder order = createOrderWithStatus(OrderStatus.COMPLETED);
        OrderStatus result = stateMachine.fire(order, OrderEvent.OWNER_CONFIRM, 3L, RoleType.OWNER);
        assertEquals(OrderStatus.CONFIRMED, result);
    }

    @Test
    @DisplayName("9. COMPLETED + OWNER_REJECT -> REJECTED (OWNER)")
    void testCompletedToRejected() {
        RepairOrder order = createOrderWithStatus(OrderStatus.COMPLETED);
        OrderStatus result = stateMachine.fire(order, OrderEvent.OWNER_REJECT, 3L, RoleType.OWNER);
        assertEquals(OrderStatus.REJECTED, result);
    }

    @Test
    @DisplayName("10. CONFIRMED + EVALUATE -> EVALUATED (OWNER)")
    void testConfirmedToEvaluated() {
        RepairOrder order = createOrderWithStatus(OrderStatus.CONFIRMED);
        OrderStatus result = stateMachine.fire(order, OrderEvent.EVALUATE, 3L, RoleType.OWNER);
        assertEquals(OrderStatus.EVALUATED, result);
    }

    @Test
    @DisplayName("11. CONFIRMED + REQUEST_REWORK -> REWORK (OWNER)")
    void testConfirmedToRework() {
        RepairOrder order = createOrderWithStatus(OrderStatus.CONFIRMED);
        OrderStatus result = stateMachine.fire(order, OrderEvent.REQUEST_REWORK, 3L, RoleType.OWNER);
        assertEquals(OrderStatus.REWORK, result);
    }

    @Test
    @DisplayName("12. EVALUATED + REQUEST_REWORK -> REWORK (OWNER)")
    void testEvaluatedToRework() {
        RepairOrder order = createOrderWithStatus(OrderStatus.EVALUATED);
        OrderStatus result = stateMachine.fire(order, OrderEvent.REQUEST_REWORK, 3L, RoleType.OWNER);
        assertEquals(OrderStatus.REWORK, result);
    }

    @Test
    @DisplayName("13. REWORK + DISPATCH -> ASSIGNED (ADMIN)")
    void testReworkToAssigned() {
        RepairOrder order = createOrderWithStatus(OrderStatus.REWORK);
        OrderStatus result = stateMachine.fire(order, OrderEvent.DISPATCH, 1L, RoleType.ADMIN);
        assertEquals(OrderStatus.ASSIGNED, result);
    }

    @Test
    @DisplayName("14. ESCALATED + REASSIGN -> ASSIGNED (SUPERVISOR)")
    void testEscalatedToAssigned() {
        RepairOrder order = createOrderWithStatus(OrderStatus.ESCALATED);
        OrderStatus result = stateMachine.fire(order, OrderEvent.REASSIGN, 4L, RoleType.SUPERVISOR);
        assertEquals(OrderStatus.ASSIGNED, result);
    }

    @Test
    @DisplayName("15. REJECTED + REASSIGN -> REWORK (SUPERVISOR)")
    void testRejectedToRework() {
        RepairOrder order = createOrderWithStatus(OrderStatus.REJECTED);
        OrderStatus result = stateMachine.fire(order, OrderEvent.REASSIGN, 4L, RoleType.SUPERVISOR);
        assertEquals(OrderStatus.REWORK, result);
    }

    // ===================== Invalid Transition =====================

    @Test
    @DisplayName("16. Invalid transition: PENDING + ACCEPT should throw BusinessException")
    void testInvalidTransition() {
        RepairOrder order = createOrderWithStatus(OrderStatus.PENDING);
        BusinessException exception = assertThrows(BusinessException.class, () ->
                stateMachine.fire(order, OrderEvent.ACCEPT, 2L, RoleType.WORKER));
        assertNotNull(exception.getMessage());
        assertTrue(exception.getMessage().contains("不支持操作"));
    }

    // ===================== Invalid Role =====================

    @Test
    @DisplayName("17. Invalid role: WORKER trying DISPATCH should throw BusinessException")
    void testInvalidRole() {
        RepairOrder order = createOrderWithStatus(OrderStatus.PENDING);
        BusinessException exception = assertThrows(BusinessException.class, () ->
                stateMachine.fire(order, OrderEvent.DISPATCH, 2L, RoleType.WORKER));
        assertNotNull(exception.getMessage());
        assertTrue(exception.getMessage().contains("无权执行操作"));
    }

    @Test
    @DisplayName("17b. Invalid role: system(null) trying ACCEPT should throw BusinessException")
    void testInvalidRoleSystemTriesAccept() {
        RepairOrder order = createOrderWithStatus(OrderStatus.ASSIGNED);
        BusinessException exception = assertThrows(BusinessException.class, () ->
                stateMachine.fire(order, OrderEvent.ACCEPT, null, null));
        assertNotNull(exception.getMessage());
        assertTrue(exception.getMessage().contains("系统无权"));
    }

    // ===================== canFire =====================

    @Test
    @DisplayName("18. canFire returns correct boolean values")
    void testCanFire() {
        // Valid transitions
        assertTrue(stateMachine.canFire(OrderStatus.PENDING, OrderEvent.DISPATCH));
        assertTrue(stateMachine.canFire(OrderStatus.ASSIGNED, OrderEvent.ACCEPT));
        assertTrue(stateMachine.canFire(OrderStatus.ASSIGNED, OrderEvent.ESCALATE));
        assertTrue(stateMachine.canFire(OrderStatus.ACCEPTED, OrderEvent.START_REPAIR));
        assertTrue(stateMachine.canFire(OrderStatus.IN_PROGRESS, OrderEvent.SUSPEND));
        assertTrue(stateMachine.canFire(OrderStatus.IN_PROGRESS, OrderEvent.COMPLETE));
        assertTrue(stateMachine.canFire(OrderStatus.COMPLETED, OrderEvent.OWNER_CONFIRM));
        assertTrue(stateMachine.canFire(OrderStatus.COMPLETED, OrderEvent.OWNER_REJECT));
        assertTrue(stateMachine.canFire(OrderStatus.CONFIRMED, OrderEvent.EVALUATE));
        assertTrue(stateMachine.canFire(OrderStatus.CONFIRMED, OrderEvent.REQUEST_REWORK));

        // Invalid transitions
        assertFalse(stateMachine.canFire(OrderStatus.PENDING, OrderEvent.ACCEPT));
        assertFalse(stateMachine.canFire(OrderStatus.PENDING, OrderEvent.COMPLETE));
        assertFalse(stateMachine.canFire(OrderStatus.ASSIGNED, OrderEvent.COMPLETE));
        assertFalse(stateMachine.canFire(OrderStatus.COMPLETED, OrderEvent.DISPATCH));
        assertFalse(stateMachine.canFire(OrderStatus.EVALUATED, OrderEvent.DISPATCH));
    }

    // ===================== getAvailableEvents =====================

    @Test
    @DisplayName("19. getAvailableEvents returns correct event lists for each status")
    void testGetAvailableEvents() {
        // PENDING: only DISPATCH
        List<OrderEvent> pendingEvents = stateMachine.getAvailableEvents(OrderStatus.PENDING);
        assertEquals(1, pendingEvents.size());
        assertTrue(pendingEvents.contains(OrderEvent.DISPATCH));

        // ASSIGNED: ACCEPT, REJECT_ACCEPT, ESCALATE
        List<OrderEvent> assignedEvents = stateMachine.getAvailableEvents(OrderStatus.ASSIGNED);
        assertEquals(3, assignedEvents.size());
        assertTrue(assignedEvents.contains(OrderEvent.ACCEPT));
        assertTrue(assignedEvents.contains(OrderEvent.REJECT_ACCEPT));
        assertTrue(assignedEvents.contains(OrderEvent.ESCALATE));

        // ACCEPTED: START_REPAIR
        List<OrderEvent> acceptedEvents = stateMachine.getAvailableEvents(OrderStatus.ACCEPTED);
        assertEquals(1, acceptedEvents.size());
        assertTrue(acceptedEvents.contains(OrderEvent.START_REPAIR));

        // IN_PROGRESS: SUSPEND, COMPLETE
        List<OrderEvent> inProgressEvents = stateMachine.getAvailableEvents(OrderStatus.IN_PROGRESS);
        assertEquals(2, inProgressEvents.size());
        assertTrue(inProgressEvents.contains(OrderEvent.SUSPEND));
        assertTrue(inProgressEvents.contains(OrderEvent.COMPLETE));

        // SUSPENDED: RESUME
        List<OrderEvent> suspendedEvents = stateMachine.getAvailableEvents(OrderStatus.SUSPENDED);
        assertEquals(1, suspendedEvents.size());
        assertTrue(suspendedEvents.contains(OrderEvent.RESUME));

        // COMPLETED: OWNER_CONFIRM, OWNER_REJECT
        List<OrderEvent> completedEvents = stateMachine.getAvailableEvents(OrderStatus.COMPLETED);
        assertEquals(2, completedEvents.size());
        assertTrue(completedEvents.contains(OrderEvent.OWNER_CONFIRM));
        assertTrue(completedEvents.contains(OrderEvent.OWNER_REJECT));

        // CONFIRMED: REQUEST_REWORK, EVALUATE
        List<OrderEvent> confirmedEvents = stateMachine.getAvailableEvents(OrderStatus.CONFIRMED);
        assertEquals(2, confirmedEvents.size());
        assertTrue(confirmedEvents.contains(OrderEvent.REQUEST_REWORK));
        assertTrue(confirmedEvents.contains(OrderEvent.EVALUATE));

        // EVALUATED: REQUEST_REWORK
        List<OrderEvent> evaluatedEvents = stateMachine.getAvailableEvents(OrderStatus.EVALUATED);
        assertEquals(1, evaluatedEvents.size());
        assertTrue(evaluatedEvents.contains(OrderEvent.REQUEST_REWORK));

        // ESCALATED: REASSIGN
        List<OrderEvent> escalatedEvents = stateMachine.getAvailableEvents(OrderStatus.ESCALATED);
        assertEquals(1, escalatedEvents.size());
        assertTrue(escalatedEvents.contains(OrderEvent.REASSIGN));

        // REWORK: DISPATCH
        List<OrderEvent> reworkEvents = stateMachine.getAvailableEvents(OrderStatus.REWORK);
        assertEquals(1, reworkEvents.size());
        assertTrue(reworkEvents.contains(OrderEvent.DISPATCH));

        // REJECTED: REASSIGN
        List<OrderEvent> rejectedEvents = stateMachine.getAvailableEvents(OrderStatus.REJECTED);
        assertEquals(1, rejectedEvents.size());
        assertTrue(rejectedEvents.contains(OrderEvent.REASSIGN));
    }
}
