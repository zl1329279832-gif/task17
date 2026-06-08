package com.property.repair.service;

import com.property.repair.entity.RepairOrder;

/**
 * Service for audit logging.
 */
public interface AuditService {

    void log(Long orderId, String action, Long userId, String role,
             Object beforeData, Object afterData, String ipAddress);
}
