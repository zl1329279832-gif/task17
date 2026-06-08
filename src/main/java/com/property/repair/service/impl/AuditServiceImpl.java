package com.property.repair.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.property.repair.entity.AuditLog;
import com.property.repair.mapper.AuditLogMapper;
import com.property.repair.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final AuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;

    @Override
    public void log(Long orderId, String action, Long userId, String role,
                    Object beforeData, Object afterData, String ipAddress) {
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setOrderId(orderId);
            auditLog.setAction(action);
            auditLog.setUserId(userId);
            auditLog.setUserRole(role);
            auditLog.setIpAddress(ipAddress);

            if (beforeData != null) {
                auditLog.setBeforeData(objectMapper.writeValueAsString(beforeData));
            }
            if (afterData != null) {
                auditLog.setAfterData(objectMapper.writeValueAsString(afterData));
            }

            auditLogMapper.insert(auditLog);
        } catch (Exception e) {
            // Audit logging should not break business logic
            log.error("Failed to write audit log for order={}, action={}", orderId, action, e);
        }
    }
}
