package com.property.repair.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.property.repair.entity.RepairOrder;
import com.property.repair.entity.TimeoutEscalation;
import com.property.repair.entity.User;
import com.property.repair.enums.OrderStatus;
import com.property.repair.enums.TimeoutType;
import com.property.repair.enums.UserRole;
import com.property.repair.mapper.RepairOrderMapper;
import com.property.repair.mapper.TimeoutEscalationMapper;
import com.property.repair.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Periodic task that checks for timeout conditions and triggers escalation.
 *
 * Prevents duplicate triggers by:
 * 1. Checking if escalation record already exists for this order+type
 * 2. Using Redis to track recently processed timeouts
 * 3. Only processing non-terminal orders
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TimeoutEscalationTask {

    private final RepairOrderMapper orderMapper;
    private final TimeoutEscalationMapper escalationMapper;
    private final UserMapper userMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String PROCESSED_KEY_PREFIX = "timeout:processed:";
    private static final int ACCEPT_MINUTES = 30;
    private static final int VISIT_HOURS = 4;
    private static final int COMPLETE_HOURS = 48;
    private static final int ESCALATION_DELAY_MINUTES = 15;

    /**
     * Run every 5 minutes to check for accept timeouts.
     */
    @Scheduled(fixedDelay = 300000)
    public void checkAcceptTimeouts() {
        log.debug("Checking accept timeouts...");

        List<RepairOrder> dispatchedOrders = orderMapper.selectList(
                new LambdaQueryWrapper<RepairOrder>()
                        .eq(RepairOrder::getStatus, OrderStatus.DISPATCHED.getCode())
                        .isNotNull(RepairOrder::getAssignedAt)
                        .isNull(RepairOrder::getSuspendedAt));

        for (RepairOrder order : dispatchedOrders) {
            if (isAlreadyProcessed(order.getId(), TimeoutType.ACCEPT_TIMEOUT)) {
                continue;
            }

            LocalDateTime deadline = order.getAssignedAt().plusMinutes(ACCEPT_MINUTES);
            if (LocalDateTime.now().isAfter(deadline.plusMinutes(ESCALATION_DELAY_MINUTES))) {
                createEscalation(order, TimeoutType.ACCEPT_TIMEOUT, deadline, 1);
                markAsProcessed(order.getId(), TimeoutType.ACCEPT_TIMEOUT);
                log.info("Accept timeout escalated: order={}, worker={}",
                        order.getOrderNo(), order.getAssignedWorkerId());
            }
        }
    }

    /**
     * Run every 5 minutes to check for visit timeouts.
     */
    @Scheduled(fixedDelay = 300000)
    public void checkVisitTimeouts() {
        log.debug("Checking visit timeouts...");

        List<RepairOrder> acceptedOrders = orderMapper.selectList(
                new LambdaQueryWrapper<RepairOrder>()
                        .eq(RepairOrder::getStatus, OrderStatus.ACCEPTED.getCode())
                        .isNotNull(RepairOrder::getAcceptedAt)
                        .isNull(RepairOrder::getSuspendedAt));

        for (RepairOrder order : acceptedOrders) {
            if (isAlreadyProcessed(order.getId(), TimeoutType.VISIT_TIMEOUT)) {
                continue;
            }

            LocalDateTime deadline = order.getAcceptedAt().plusHours(VISIT_HOURS);
            if (LocalDateTime.now().isAfter(deadline.plusMinutes(ESCALATION_DELAY_MINUTES))) {
                createEscalation(order, TimeoutType.VISIT_TIMEOUT, deadline, 1);
                markAsProcessed(order.getId(), TimeoutType.VISIT_TIMEOUT);
                log.info("Visit timeout escalated: order={}, worker={}",
                        order.getOrderNo(), order.getAssignedWorkerId());
            }
        }
    }

    /**
     * Run every 10 minutes to check for completion timeouts.
     */
    @Scheduled(fixedDelay = 600000)
    public void checkCompleteTimeouts() {
        log.debug("Checking completion timeouts...");

        List<RepairOrder> visitingOrders = orderMapper.selectList(
                new LambdaQueryWrapper<RepairOrder>()
                        .eq(RepairOrder::getStatus, OrderStatus.VISITING.getCode())
                        .isNotNull(RepairOrder::getVisitAt)
                        .isNull(RepairOrder::getSuspendedAt));

        for (RepairOrder order : visitingOrders) {
            if (isAlreadyProcessed(order.getId(), TimeoutType.COMPLETE_TIMEOUT)) {
                continue;
            }

            LocalDateTime deadline = order.getVisitAt().plusHours(COMPLETE_HOURS);
            if (LocalDateTime.now().isAfter(deadline.plusMinutes(ESCALATION_DELAY_MINUTES))) {
                createEscalation(order, TimeoutType.COMPLETE_TIMEOUT, deadline, 1);
                markAsProcessed(order.getId(), TimeoutType.COMPLETE_TIMEOUT);
                log.info("Complete timeout escalated: order={}, worker={}",
                        order.getOrderNo(), order.getAssignedWorkerId());
            }
        }
    }

    /**
     * Run every hour to check for unhandled escalations that need second-level escalation.
     */
    @Scheduled(fixedDelay = 3600000)
    public void checkSecondLevelEscalation() {
        log.debug("Checking second-level escalations...");

        List<TimeoutEscalation> unhandled = escalationMapper.selectList(
                new LambdaQueryWrapper<TimeoutEscalation>()
                        .eq(TimeoutEscalation::getHandled, 0)
                        .eq(TimeoutEscalation::getEscalationLevel, 1)
                        .le(TimeoutEscalation::getCreatedAt,
                                LocalDateTime.now().minusHours(2)));

        for (TimeoutEscalation esc : unhandled) {
            RepairOrder order = orderMapper.selectById(esc.getOrderId());
            if (order == null) continue;

            // Skip terminal orders
            if (order.getStatus().equals(OrderStatus.REVIEWED.getCode())
                    || order.getStatus().equals(OrderStatus.CLOSED.getCode())
                    || order.getStatus().equals(OrderStatus.CANCELLED.getCode())) {
                continue;
            }

            // Skip suspended orders
            if (order.getStatus().equals(OrderStatus.SUSPENDED.getCode())) {
                continue;
            }

            // Skip orders waiting for parts (SLA paused)
            if (order.getStatus().equals(OrderStatus.WAITING_PARTS.getCode())) {
                continue;
            }

            // Skip if escalation belongs to a superseded dispatch round
            if (esc.getDispatchId() != null
                    && !esc.getDispatchId().equals(order.getCurrentDispatchId())) {
                esc.setHandled(1);
                esc.setHandledAt(LocalDateTime.now());
                esc.setHandleRemark("Skipped: superseded dispatch round");
                escalationMapper.updateById(esc);
                continue;
            }

            // Create second-level escalation to admin
            createEscalation(order,
                    TimeoutType.valueOf(esc.getTimeoutType()),
                    esc.getDeadline(), 2);
            log.info("Second-level escalation: order={}, type={}",
                    order.getOrderNo(), esc.getTimeoutType());
        }
    }

    // ---- Helpers ----

    private void createEscalation(RepairOrder order, TimeoutType type,
                                   LocalDateTime deadline, int level) {
        // Find supervisor for the community
        Long supervisorId = findSupervisor(order.getCommunityId(), level);

        TimeoutEscalation escalation = new TimeoutEscalation();
        escalation.setOrderId(order.getId());
        escalation.setDispatchId(order.getCurrentDispatchId());
        escalation.setTimeoutType(type.getCode());
        escalation.setDeadline(deadline);
        escalation.setEscalatedTo(supervisorId);
        escalation.setEscalationLevel(level);
        escalation.setHandled(0);
        escalationMapper.insert(escalation);
    }

    private Long findSupervisor(Long communityId, int level) {
        if (level == 1) {
            // First-level: community supervisor
            User supervisor = userMapper.selectOne(
                    new LambdaQueryWrapper<User>()
                            .eq(User::getRole, UserRole.SUPERVISOR.getCode())
                            .eq(User::getCommunityId, communityId)
                            .eq(User::getStatus, 1)
                            .last("LIMIT 1"));
            if (supervisor != null) return supervisor.getId();
        }
        // Second-level or fallback: any admin
        User admin = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getRole, UserRole.ADMIN.getCode())
                        .eq(User::getStatus, 1)
                        .last("LIMIT 1"));
        return admin != null ? admin.getId() : null;
    }

    /**
     * Check if this order+timeoutType has already been processed.
     * Uses both database and Redis to prevent duplicates.
     * Dispatch-round aware: only counts escalations for the current dispatch round.
     */
    private boolean isAlreadyProcessed(Long orderId, TimeoutType type) {
        // Check Redis first (fast) — keys are cleared on re-dispatch/rework/suspend
        String redisKey = PROCESSED_KEY_PREFIX + type.getCode() + ":" + orderId;
        Boolean exists = redisTemplate.hasKey(redisKey);
        if (exists != null && exists) return true;

        // Check database — only count escalations for the CURRENT dispatch round
        RepairOrder order = orderMapper.selectById(orderId);
        if (order == null) return true;

        Long currentDispatchId = order.getCurrentDispatchId();
        LambdaQueryWrapper<TimeoutEscalation> wrapper = new LambdaQueryWrapper<TimeoutEscalation>()
                .eq(TimeoutEscalation::getOrderId, orderId)
                .eq(TimeoutEscalation::getTimeoutType, type.getCode());
        if (currentDispatchId != null) {
            wrapper.eq(TimeoutEscalation::getDispatchId, currentDispatchId);
        }
        long count = escalationMapper.selectCount(wrapper);
        return count > 0;
    }

    /**
     * Mark as processed in Redis with TTL to prevent re-processing within 24h.
     */
    private void markAsProcessed(Long orderId, TimeoutType type) {
        String redisKey = PROCESSED_KEY_PREFIX + type.getCode() + ":" + orderId;
        redisTemplate.opsForValue().set(redisKey, "1", java.time.Duration.ofHours(24));
    }
}
