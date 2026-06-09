package com.property.repair.statemachine;

import com.property.repair.enums.OrderStatus;
import com.property.repair.exception.InvalidStateTransitionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * State machine that defines and validates repair order transitions.
 *
 * Valid transitions:
 *   PENDING        → DISPATCHED, CANCELLED, CLOSED
 *   DISPATCHED     → ACCEPTED, PENDING (reject), CLOSED
 *   ACCEPTED       → VISITING, SUSPENDED, TRANSFERRED, WAITING_PARTS, CLOSED
 *   VISITING       → COMPLETED, SUSPENDED, TRANSFERRED, WAITING_PARTS, CLOSED
 *   SUSPENDED      → ACCEPTED, VISITING (resume), CLOSED
 *   TRANSFERRED    → DISPATCHED (re-dispatch)
 *   COMPLETED      → REVIEWED, REWORKING, CLOSED
 *   REWORKING      → VISITING, COMPLETED, SUSPENDED, WAITING_PARTS, CLOSED
 *   WAITING_PARTS  → ACCEPTED, VISITING, REWORKING (parts arrived, resume), CLOSED
 *   REVIEWED       → (terminal)
 *   CLOSED         → (terminal)
 *   CANCELLED      → (terminal)
 */
@Slf4j
@Component
public class OrderStateMachine {

    private final Map<String, Set<String>> transitions;

    public OrderStateMachine() {
        transitions = new HashMap<>();

        allow(OrderStatus.PENDING,     OrderStatus.DISPATCHED);
        allow(OrderStatus.PENDING,     OrderStatus.CANCELLED);

        allow(OrderStatus.DISPATCHED,  OrderStatus.ACCEPTED);
        allow(OrderStatus.DISPATCHED,  OrderStatus.PENDING);        // reject → back to pending

        allow(OrderStatus.ACCEPTED,    OrderStatus.VISITING);
        allow(OrderStatus.ACCEPTED,    OrderStatus.SUSPENDED);
        allow(OrderStatus.ACCEPTED,    OrderStatus.TRANSFERRED);
        allow(OrderStatus.ACCEPTED,    OrderStatus.WAITING_PARTS);  // critical parts insufficient

        allow(OrderStatus.VISITING,    OrderStatus.COMPLETED);
        allow(OrderStatus.VISITING,    OrderStatus.SUSPENDED);
        allow(OrderStatus.VISITING,    OrderStatus.TRANSFERRED);
        allow(OrderStatus.VISITING,    OrderStatus.WAITING_PARTS);  // discovered parts needed on-site

        allow(OrderStatus.SUSPENDED,   OrderStatus.ACCEPTED);       // resume
        allow(OrderStatus.SUSPENDED,   OrderStatus.VISITING);       // resume

        allow(OrderStatus.TRANSFERRED, OrderStatus.DISPATCHED);     // re-dispatch

        allow(OrderStatus.COMPLETED,   OrderStatus.REVIEWED);
        allow(OrderStatus.COMPLETED,   OrderStatus.REWORKING);

        allow(OrderStatus.REWORKING,   OrderStatus.VISITING);
        allow(OrderStatus.REWORKING,   OrderStatus.COMPLETED);
        allow(OrderStatus.REWORKING,   OrderStatus.SUSPENDED);
        allow(OrderStatus.REWORKING,   OrderStatus.WAITING_PARTS);  // rework needs additional parts

        // Parts arrived → resume to previous active state
        allow(OrderStatus.WAITING_PARTS, OrderStatus.ACCEPTED);
        allow(OrderStatus.WAITING_PARTS, OrderStatus.VISITING);
        allow(OrderStatus.WAITING_PARTS, OrderStatus.REWORKING);

        // Admin can close from any non-terminal state
        allow(OrderStatus.PENDING,         OrderStatus.CLOSED);
        allow(OrderStatus.DISPATCHED,      OrderStatus.CLOSED);
        allow(OrderStatus.ACCEPTED,        OrderStatus.CLOSED);
        allow(OrderStatus.VISITING,        OrderStatus.CLOSED);
        allow(OrderStatus.SUSPENDED,       OrderStatus.CLOSED);
        allow(OrderStatus.REWORKING,       OrderStatus.CLOSED);
        allow(OrderStatus.COMPLETED,       OrderStatus.CLOSED);
        allow(OrderStatus.WAITING_PARTS,   OrderStatus.CLOSED);
    }

    /**
     * Validate that a transition from → to is allowed.
     *
     * @throws InvalidStateTransitionException if the transition is not allowed
     */
    public void validateTransition(String from, String to) {
        Set<String> allowed = transitions.get(from);
        if (allowed == null || !allowed.contains(to)) {
            log.warn("Invalid state transition: {} → {}", from, to);
            throw new InvalidStateTransitionException(from, to);
        }
    }

    /**
     * Check if a transition is allowed without throwing.
     */
    public boolean canTransition(String from, String to) {
        Set<String> allowed = transitions.get(from);
        return allowed != null && allowed.contains(to);
    }

    /**
     * Get all valid target states from a given state.
     */
    public Set<String> getValidTransitions(String from) {
        return transitions.getOrDefault(from, Collections.emptySet());
    }

    private void allow(OrderStatus from, OrderStatus to) {
        transitions.computeIfAbsent(from.getCode(), k -> new HashSet<>()).add(to.getCode());
    }
}
