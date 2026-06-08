package com.property.repair.common.aspect;

import com.property.repair.common.annotation.Idempotent;
import com.property.repair.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class IdempotentAspect {

    private static final String IDEMPOTENT_HEADER = "X-Idempotent-Key";
    private static final String IDEMPOTENT_PREFIX = "idempotent:";

    private final StringRedisTemplate stringRedisTemplate;

    @Around("@annotation(com.property.repair.common.annotation.Idempotent)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return joinPoint.proceed();
        }

        HttpServletRequest request = attributes.getRequest();
        String idempotentKey = request.getHeader(IDEMPOTENT_HEADER);

        if (!StringUtils.hasText(idempotentKey)) {
            throw new BusinessException(400, "Missing idempotent key in request header");
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Idempotent idempotent = method.getAnnotation(Idempotent.class);

        String redisKey = IDEMPOTENT_PREFIX + idempotentKey;
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(redisKey, "1", idempotent.expireSeconds(), TimeUnit.SECONDS);

        if (Boolean.FALSE.equals(success)) {
            log.warn("Duplicate request detected, idempotent key: {}", idempotentKey);
            throw new BusinessException(409, "Duplicate request, please do not submit again");
        }

        try {
            Object result = joinPoint.proceed();
            return result;
        } catch (Exception e) {
            // Remove the key if execution fails so the request can be retried
            stringRedisTemplate.delete(redisKey);
            throw e;
        }
    }
}
