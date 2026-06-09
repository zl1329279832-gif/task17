package com.property.repair.statemachine;

import com.property.repair.enums.OrderStatus;
import com.property.repair.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class OrderStateMachineTest {

    private OrderStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new OrderStateMachine();
    }

    @ParameterizedTest
    @MethodSource("validTransitions")
    @DisplayName("Valid state transitions should not throw")
    void validTransitions_shouldPass(OrderStatus from, OrderStatus to) {
        assertDoesNotThrow(() -> stateMachine.validateTransition(from.getCode(), to.getCode()));
    }

    static Stream<Arguments> validTransitions() {
        return Stream.of(
            // Normal flow
            Arguments.of(OrderStatus.PENDING, OrderStatus.DISPATCHED),
            Arguments.of(OrderStatus.DISPATCHED, OrderStatus.ACCEPTED),
            Arguments.of(OrderStatus.ACCEPTED, OrderStatus.VISITING),
            Arguments.of(OrderStatus.VISITING, OrderStatus.COMPLETED),
            Arguments.of(OrderStatus.COMPLETED, OrderStatus.REVIEWED),

            // Reject
            Arguments.of(OrderStatus.DISPATCHED, OrderStatus.PENDING),

            // Suspend/Resume
            Arguments.of(OrderStatus.ACCEPTED, OrderStatus.SUSPENDED),
            Arguments.of(OrderStatus.VISITING, OrderStatus.SUSPENDED),
            Arguments.of(OrderStatus.SUSPENDED, OrderStatus.ACCEPTED),
            Arguments.of(OrderStatus.SUSPENDED, OrderStatus.VISITING),

            // Transfer
            Arguments.of(OrderStatus.ACCEPTED, OrderStatus.TRANSFERRED),
            Arguments.of(OrderStatus.VISITING, OrderStatus.TRANSFERRED),
            Arguments.of(OrderStatus.TRANSFERRED, OrderStatus.DISPATCHED),

            // Rework
            Arguments.of(OrderStatus.COMPLETED, OrderStatus.REWORKING),
            Arguments.of(OrderStatus.REWORKING, OrderStatus.VISITING),
            Arguments.of(OrderStatus.REWORKING, OrderStatus.COMPLETED),
            Arguments.of(OrderStatus.REWORKING, OrderStatus.SUSPENDED),

            // Waiting for parts
            Arguments.of(OrderStatus.ACCEPTED, OrderStatus.WAITING_PARTS),
            Arguments.of(OrderStatus.VISITING, OrderStatus.WAITING_PARTS),
            Arguments.of(OrderStatus.REWORKING, OrderStatus.WAITING_PARTS),
            Arguments.of(OrderStatus.WAITING_PARTS, OrderStatus.ACCEPTED),
            Arguments.of(OrderStatus.WAITING_PARTS, OrderStatus.VISITING),
            Arguments.of(OrderStatus.WAITING_PARTS, OrderStatus.REWORKING),
            Arguments.of(OrderStatus.WAITING_PARTS, OrderStatus.CLOSED),

            // Cancel
            Arguments.of(OrderStatus.PENDING, OrderStatus.CANCELLED),

            // Admin close
            Arguments.of(OrderStatus.PENDING, OrderStatus.CLOSED),
            Arguments.of(OrderStatus.DISPATCHED, OrderStatus.CLOSED),
            Arguments.of(OrderStatus.ACCEPTED, OrderStatus.CLOSED),
            Arguments.of(OrderStatus.VISITING, OrderStatus.CLOSED)
        );
    }

    @ParameterizedTest
    @MethodSource("invalidTransitions")
    @DisplayName("Invalid state transitions should throw")
    void invalidTransitions_shouldThrow(OrderStatus from, OrderStatus to) {
        assertThrows(InvalidStateTransitionException.class,
                () -> stateMachine.validateTransition(from.getCode(), to.getCode()));
    }

    static Stream<Arguments> invalidTransitions() {
        return Stream.of(
            // Cannot skip states
            Arguments.of(OrderStatus.PENDING, OrderStatus.ACCEPTED),
            Arguments.of(OrderStatus.PENDING, OrderStatus.COMPLETED),

            // Cannot go backwards
            Arguments.of(OrderStatus.COMPLETED, OrderStatus.PENDING),
            Arguments.of(OrderStatus.REVIEWED, OrderStatus.DISPATCHED),

            // Terminal states have no outgoing transitions
            Arguments.of(OrderStatus.REVIEWED, OrderStatus.PENDING),
            Arguments.of(OrderStatus.CLOSED, OrderStatus.PENDING),
            Arguments.of(OrderStatus.CANCELLED, OrderStatus.PENDING),

            // Cannot review before completion
            Arguments.of(OrderStatus.VISITING, OrderStatus.REVIEWED),

            // Cannot rework from non-completed states
            Arguments.of(OrderStatus.VISITING, OrderStatus.REWORKING),

            // WAITING_PARTS invalid transitions
            Arguments.of(OrderStatus.PENDING, OrderStatus.WAITING_PARTS),
            Arguments.of(OrderStatus.DISPATCHED, OrderStatus.WAITING_PARTS),
            Arguments.of(OrderStatus.WAITING_PARTS, OrderStatus.DISPATCHED),
            Arguments.of(OrderStatus.WAITING_PARTS, OrderStatus.COMPLETED)
        );
    }

    @Test
    @DisplayName("canTransition returns correct boolean")
    void canTransition_returnsCorrectBoolean() {
        assertTrue(stateMachine.canTransition("PENDING", "DISPATCHED"));
        assertFalse(stateMachine.canTransition("REVIEWED", "PENDING"));
    }

    @Test
    @DisplayName("getValidTransitions returns non-empty set for active states")
    void getValidTransitions_returnsNonEmpty() {
        Set<String> transitions = stateMachine.getValidTransitions("PENDING");
        assertFalse(transitions.isEmpty());
        assertTrue(transitions.contains("DISPATCHED"));
        assertTrue(transitions.contains("CANCELLED"));
    }

    @Test
    @DisplayName("Terminal states have no valid transitions (except admin close)")
    void terminalStates_noTransitions() {
        Set<String> reviewedTransitions = stateMachine.getValidTransitions("REVIEWED");
        assertTrue(reviewedTransitions.isEmpty());

        Set<String> closedTransitions = stateMachine.getValidTransitions("CLOSED");
        assertTrue(closedTransitions.isEmpty());

        Set<String> cancelledTransitions = stateMachine.getValidTransitions("CANCELLED");
        assertTrue(cancelledTransitions.isEmpty());
    }
}
