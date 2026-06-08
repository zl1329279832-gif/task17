package com.property.repair.exception;

/**
 * Thrown when a duplicate repair order is detected.
 */
public class DuplicateOrderException extends BusinessException {

    private final Long existingOrderId;

    public DuplicateOrderException(Long existingOrderId, String message) {
        super(409, message);
        this.existingOrderId = existingOrderId;
    }

    public Long getExistingOrderId() {
        return existingOrderId;
    }
}
