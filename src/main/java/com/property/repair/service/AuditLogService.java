package com.property.repair.service;

import com.property.repair.common.result.PageResult;
import com.property.repair.common.result.Result;
import com.property.repair.entity.AuditLog;

public interface AuditLogService {

    void log(Long userId, String role, String action, String targetType, Long targetId, String detail, String ip);

    Result<PageResult<AuditLog>> getAuditLogs(Integer pageNum, Integer pageSize);
}
