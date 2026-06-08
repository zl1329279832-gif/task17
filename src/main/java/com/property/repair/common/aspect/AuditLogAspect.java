package com.property.repair.common.aspect;

import com.property.repair.common.annotation.AuditAction;
import com.property.repair.entity.AuditLog;
import com.property.repair.mapper.AuditLogMapper;
import com.property.repair.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private final AuditLogMapper auditLogMapper;

    @AfterReturning(pointcut = "@annotation(com.property.repair.common.annotation.AuditAction)", returning = "result")
    public void afterReturning(JoinPoint joinPoint, Object result) {
        try {
            recordAuditLog(joinPoint, result);
        } catch (Exception e) {
            log.error("Failed to record audit log", e);
        }
    }

    @Async
    protected void recordAuditLog(JoinPoint joinPoint, Object result) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        AuditAction auditAction = method.getAnnotation(AuditAction.class);

        if (auditAction == null) {
            return;
        }

        AuditLog auditLog = new AuditLog();
        auditLog.setAction(auditAction.action());
        auditLog.setTargetType(auditAction.targetType());

        // Extract target ID from method arguments if available
        Object[] args = joinPoint.getArgs();
        if (args != null && args.length > 0 && args[0] instanceof Long) {
            auditLog.setTargetId((Long) args[0]);
        }

        // Set user information
        try {
            auditLog.setUserId(SecurityUtils.getCurrentUserId());
            auditLog.setUserRole(SecurityUtils.getCurrentRole().name());
        } catch (Exception e) {
            log.debug("Could not get current user for audit log");
            auditLog.setUserId(0L);
            auditLog.setUserRole("SYSTEM");
        }

        // Set request IP
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            auditLog.setIpAddress(getClientIp(request));
        }

        // Build detail
        String detail = String.format("%s.%s", signature.getDeclaringTypeName(), method.getName());
        auditLog.setDetail(detail);

        auditLogMapper.insert(auditLog);
        log.debug("Audit log recorded: action={}, targetType={}, targetId={}",
                auditLog.getAction(), auditLog.getTargetType(), auditLog.getTargetId());
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
