package com.property.repair.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.property.repair.entity.RepairOrder;
import com.property.repair.entity.TimeoutEscalation;
import com.property.repair.enums.OrderStatus;
import com.property.repair.mapper.RepairOrderMapper;
import com.property.repair.mapper.TimeoutEscalationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Periodic cleanup task to:
 * 1. Clean up stale timeout records for already-completed orders
 * 2. Detect orders stuck in intermediate states
 * 3. Clean up expired Redis keys
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CleanupTask {

    private final RepairOrderMapper orderMapper;
    private final TimeoutEscalationMapper escalationMapper;

    /**
     * Run daily at 3:00 AM.
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanStaleEscalations() {
        log.info("Starting stale escalation cleanup...");

        // Find unhandled escalations for orders that are already in terminal state
        List<TimeoutEscalation> unhandled = escalationMapper.selectList(
                new LambdaQueryWrapper<TimeoutEscalation>()
                        .eq(TimeoutEscalation::getHandled, 0)
                        .le(TimeoutEscalation::getCreatedAt,
                                LocalDateTime.now().minusDays(7)));

        int cleaned = 0;
        for (TimeoutEscalation esc : unhandled) {
            RepairOrder order = orderMapper.selectById(esc.getOrderId());
            if (order != null && OrderStatus.valueOf(order.getStatus()).isTerminal()) {
                esc.setHandled(1);
                esc.setHandledAt(LocalDateTime.now());
                esc.setHandleRemark("Auto-cleaned: order already in terminal state");
                escalationMapper.updateById(esc);
                cleaned++;
            }
        }

        log.info("Cleanup completed: {} stale escalations handled", cleaned);
    }

    /**
     * Run every 30 minutes to detect stuck orders.
     */
    @Scheduled(fixedDelay = 1800000)
    public void detectStuckOrders() {
        log.debug("Checking for stuck orders...");

        // Orders stuck in DISPATCHED for more than 2 hours
        List<RepairOrder> stuckDispatched = orderMapper.selectList(
                new LambdaQueryWrapper<RepairOrder>()
                        .eq(RepairOrder::getStatus, OrderStatus.DISPATCHED.getCode())
                        .le(RepairOrder::getAssignedAt, LocalDateTime.now().minusHours(2)));

        for (RepairOrder order : stuckDispatched) {
            log.warn("Stuck order detected: {} in DISPATCHED since {}",
                    order.getOrderNo(), order.getAssignedAt());
        }

        // Orders stuck in TRANSFERRED for more than 1 hour
        List<RepairOrder> stuckTransferred = orderMapper.selectList(
                new LambdaQueryWrapper<RepairOrder>()
                        .eq(RepairOrder::getStatus, OrderStatus.TRANSFERRED.getCode())
                        .le(RepairOrder::getUpdatedAt, LocalDateTime.now().minusHours(1)));

        for (RepairOrder order : stuckTransferred) {
            log.warn("Stuck order detected: {} in TRANSFERRED since {}",
                    order.getOrderNo(), order.getUpdatedAt());
        }
    }
}
