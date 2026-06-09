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
import java.util.List;

/**
 * Periodic task that checks for timeout conditions and triggers escalation.
 *
 * Prevents duplicate triggers by:
 * 1. Checking if escalation record already exists for this order+type+round
 * 2. Using Redis to track recently processed timeouts (round-scoped)
 * 3. Only processing non-terminal, non-suspended orders
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
                        .isNotNull(RepairOrder::getAssignedAt));

        for (RepairOrder order : dispatchedOrders) {
            int dr = order.getDispatchRound() != null ? order.getDispatchRound() : 1;
            int rr = order.getRepairRound() != null ? order.getRepairRound() : 1;

            if (isAlreadyProcessed(order.getId(), TimeoutType.ACCEPT_TIMEOUT, dr, rr)) {
                continue;
            }

            LocalDateTime deadline = order.getAssignedAt().plusMinutes(ACCEPT_MINUTES);
            if (LocalDateTime.now().isAfter(deadline.plusMinutes(ESCALATION_DELAY_MINUTES))) {
                createEscalation(order, TimeoutType.ACCEPT_TIMEOUT, deadline, 1, dr, rr);
                markAsProcessed(order.getId(), TimeoutType.ACCEPT_TIMEOUT, dr, rr);
                log.info("Accept timeout escalated: order={}, worker={}, dispatchRound={}",
                        order.getOrderNo(), order.getAssignedWorkerId(), dr);
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
                        .isNotNull(RepairOrder::getAcceptedAt));

        for (RepairOrder order : acceptedOrders) {
            int dr = order.getDispatchRound() != null ? order.getDispatchRound() : 1;
            int rr = order.getRepairRound() != null ? order.getRepairRound() : 1;

            if (isAlreadyProcessed(order.getId(), TimeoutType.VISIT_TIMEOUT, dr, rr)) {
                continue;
            }

            LocalDateTime deadline = order.getAcceptedAt().plusHours(VISIT_HOURS);
            if (LocalDateTime.now().isAfter(deadline.plusMinutes(ESCALATION_DELAY_MINUTES))) {
                createEscalation(order, TimeoutType.VISIT_TIMEOUT, deadline, 1, dr, rr);
                markAsProcessed(order.getId(), TimeoutType.VISIT_TIMEOUT, dr, rr);
                log.info("Visit timeout escalated: order={}, worker={}, dispatchRound={}",
                        order.getOrderNo(), order.getAssignedWorkerId(), dr);
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
                        .isNotNull(RepairOrder::getVisitAt));

        for (RepairOrder order : visitingOrders) {
            int dr = order.getDispatchRound() != null ? order.getDispatchRound() : 1;
            int rr = order.getRepairRound() != null ? order.getRepairRound() : 1;

            if (isAlreadyProcessed(order.getId(), TimeoutType.COMPLETE_TIMEOUT, dr, rr)) {
                continue;
            }

            LocalDateTime deadline = order.getVisitAt().plusHours(COMPLETE_HOURS);
            if (LocalDateTime.now().isAfter(deadline.plusMinutes(ESCALATION_DELAY_MINUTES))) {
                createEscalation(order, TimeoutType.COMPLETE_TIMEOUT, deadline, 1, dr, rr);
                markAsProcessed(order.getId(), TimeoutType.COMPLETE_TIMEOUT, dr, rr);
                log.info("Complete timeout escalated: order={}, worker={}, repairRound={}",
                        order.getOrderNo(), order.getAssignedWorkerId(), rr);
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

            // Skip terminal states
            String status = order.getStatus();
            if (OrderStatus.REVIEWED.getCode().equals(status)
                    || OrderStatus.CLOSED.getCode().equals(status)
                    || OrderStatus.CANCELLED.getCode().equals(status)) {
                continue;
            }

            // Skip if the order's round has changed since the escalation was created
            // (means the order was reassigned or reworked, making this escalation stale)
            int currentDr = order.getDispatchRound() != null ? order.getDispatchRound() : 1;
            int currentRr = order.getRepairRound() != null ? order.getRepairRound() : 1;
            int escDr = esc.getDispatchRound() != null ? esc.getDispatchRound() : 1;
            int escRr = esc.getRepairRound() != null ? esc.getRepairRound() : 1;
            if (currentDr != escDr || currentRr != escRr) {
                continue;
            }

            // Create second-level escalation to admin
            createEscalation(order,
                    TimeoutType.valueOf(esc.getTimeoutType()),
                    esc.getDeadline(), 2, currentDr, currentRr);
            log.info("Second-level escalation: order={}, type={}",
                    order.getOrderNo(), esc.getTimeoutType());
        }
    }

    // ---- Helpers ----

    private void createEscalation(RepairOrder order, TimeoutType type,
                                   LocalDateTime deadline, int level,
                                   int dispatchRound, int repairRound) {
        // Find supervisor for the community
        Long supervisorId = findSupervisor(order.getCommunityId(), level);

        TimeoutEscalation escalation = new TimeoutEscalation();
        escalation.setOrderId(order.getId());
        escalation.setTimeoutType(type.getCode());
        escalation.setDeadline(deadline);
        escalation.setEscalatedTo(supervisorId);
        escalation.setEscalationLevel(level);
        escalation.setHandled(0);
        escalation.setDispatchRound(dispatchRound);
        escalation.setRepairRound(repairRound);
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
     * Check if this order+timeoutType+round has already been processed.
     * Uses both database and Redis to prevent duplicates.
     * Round-scoped: a new dispatch/repair round resets processing state.
     */
    private boolean isAlreadyProcessed(Long orderId, TimeoutType type,
                                        int dispatchRound, int repairRound) {
        // Check Redis first (fast)
        String redisKey = buildProcessedKey(orderId, type, dispatchRound, repairRound);
        Boolean exists = redisTemplate.hasKey(redisKey);
        if (exists != null && exists) return true;

        // Check database — only for the current round
        long count = escalationMapper.selectCount(
                new LambdaQueryWrapper<TimeoutEscalation>()
                        .eq(TimeoutEscalation::getOrderId, orderId)
                        .eq(TimeoutEscalation::getTimeoutType, type.getCode())
                        .eq(TimeoutEscalation::getDispatchRound, dispatchRound)
                        .eq(TimeoutEscalation::getRepairRound, repairRound));
        return count > 0;
    }

    /**
     * Mark as processed in Redis with TTL to prevent re-processing within 24h.
     */
    private void markAsProcessed(Long orderId, TimeoutType type,
                                  int dispatchRound, int repairRound) {
        String redisKey = buildProcessedKey(orderId, type, dispatchRound, repairRound);
        redisTemplate.opsForValue().set(redisKey, "1", java.time.Duration.ofHours(24));
    }

    private String buildProcessedKey(Long orderId, TimeoutType type,
                                      int dispatchRound, int repairRound) {
        return PROCESSED_KEY_PREFIX + type.getCode() + ":" + orderId
                + ":" + dispatchRound + ":" + repairRound;
    }
}
