package com.property.repair.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Repair order status — drives the state machine.
 *
 * Lifecycle:
 *   PENDING → DISPATCHED → ACCEPTED → VISITING → COMPLETED → REVIEWED
 *               ↓            ↓           ↓          ↓
 *           TRANSFERRED   SUSPENDED   SUSPENDED   REWORKING → COMPLETED
 *               ↓            ↓           ↓           ↓
 *           DISPATCHED    ACCEPTED    VISITING   WAITING_PARTS → (resume)
 *                                         ↑            ↓
 *                                    WAITING_PARTS  ACCEPTED/VISITING/REWORKING
 */
@Getter
@AllArgsConstructor
public enum OrderStatus {

    PENDING("PENDING", "Awaiting dispatch"),
    DISPATCHED("DISPATCHED", "Assigned to worker, awaiting acceptance"),
    ACCEPTED("ACCEPTED", "Worker accepted, preparing visit"),
    VISITING("VISITING", "Worker on-site"),
    COMPLETED("COMPLETED", "Repair done, awaiting owner confirmation"),
    REVIEWED("REVIEWED", "Owner reviewed, order closed"),
    TRANSFERRED("TRANSFERRED", "Transferred to another worker"),
    SUSPENDED("SUSPENDED", "Temporarily suspended"),
    REWORKING("REWORKING", "Rework in progress"),
    WAITING_PARTS("WAITING_PARTS", "Waiting for spare parts arrival"),
    CLOSED("CLOSED", "Closed by admin"),
    CANCELLED("CANCELLED", "Cancelled by owner");

    private final String code;
    private final String description;

    /**
     * Whether this status represents a terminal (final) state.
     */
    public boolean isTerminal() {
        return this == REVIEWED || this == CLOSED || this == CANCELLED;
    }

    /**
     * Whether the order is actively being worked on.
     */
    public boolean isActive() {
        return this == DISPATCHED || this == ACCEPTED || this == VISITING || this == REWORKING;
    }

    /**
     * Whether the order is paused waiting for parts (SLA clock stopped).
     */
    public boolean isWaitingParts() {
        return this == WAITING_PARTS;
    }
}
