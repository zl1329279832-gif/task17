package com.property.repair.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.property.repair.common.result.PageResult;
import com.property.repair.common.result.Result;
import com.property.repair.entity.AuditLog;
import com.property.repair.mapper.AuditLogMapper;
import com.property.repair.service.AuditLogService;
import org.springframework.stereotype.Service;

@Service
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogMapper auditLogMapper;

    public AuditLogServiceImpl(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    @Override
    public void log(Long userId, String role, String action, String targetType, Long targetId, String detail, String ip) {
        AuditLog auditLog = new AuditLog();
        auditLog.setUserId(userId);
        auditLog.setUserRole(role);
        auditLog.setAction(action);
        auditLog.setTargetType(targetType);
        auditLog.setTargetId(targetId);
        auditLog.setDetail(detail);
        auditLog.setIpAddress(ip);
        auditLogMapper.insert(auditLog);
    }

    @Override
    public Result<PageResult<AuditLog>> getAuditLogs(Integer pageNum, Integer pageSize) {
        if (pageNum == null || pageNum < 1) {
            pageNum = 1;
        }
        if (pageSize == null || pageSize < 1) {
            pageSize = 20;
        }

        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(AuditLog::getCreatedAt);

        IPage<AuditLog> page = auditLogMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        PageResult<AuditLog> pageResult = PageResult.from(page);
        return Result.success(pageResult);
    }
}
