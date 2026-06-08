package com.property.repair.exception;

/**
 * Thrown when a state machine transition is invalid.
 */
public class InvalidStateTransitionException extends BusinessException {

    public InvalidStateTransitionException(String fromStatus, String toStatus) {
        super(400, "Invalid state transition from [" + fromStatus + "] to [" + toStatus + "]");
    }

    public InvalidStateTransitionException(String message) {
        super(400, message);
    }
}
