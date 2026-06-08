package com.property.repair.config;

import com.property.repair.exception.BusinessException;
import com.property.repair.security.RequireRole;
import com.property.repair.security.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * AOP aspect that enforces @RequireRole annotations on service methods.
 */
@Slf4j
@Aspect
@Component
public class RequireRoleAspect {

    @Around("@annotation(requireRole)")
    public Object checkRole(ProceedingJoinPoint joinPoint, RequireRole requireRole) throws Throwable {
        String currentRole = SecurityUtils.getCurrentRole();
        boolean allowed = Arrays.asList(requireRole.value()).contains(currentRole);

        if (!allowed) {
            log.warn("Role check failed: user role={}, required={}", currentRole, requireRole.value());
            throw new BusinessException(403, requireRole.message());
        }

        return joinPoint.proceed();
    }
}
