package com.property.repair.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.property.repair.common.constants.RedisKeyConstants;
import com.property.repair.common.enums.OrderStatus;
import com.property.repair.entity.RepairOrder;
import com.property.repair.mapper.RepairOrderMapper;
import com.property.repair.service.TimeoutEscalationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
public class TimeoutDetectionScheduler {

    private static final Logger log = LoggerFactory.getLogger(TimeoutDetectionScheduler.class);

    private final RepairOrderMapper repairOrderMapper;
    private final TimeoutEscalationService timeoutEscalationService;
    private final DistributedLockHelper distributedLockHelper;

    @Value("${repair.accept-timeout-minutes:30}")
    private int acceptTimeoutMinutes;

    public TimeoutDetectionScheduler(RepairOrderMapper repairOrderMapper,
                                     TimeoutEscalationService timeoutEscalationService,
                                     DistributedLockHelper distributedLockHelper) {
        this.repairOrderMapper = repairOrderMapper;
        this.timeoutEscalationService = timeoutEscalationService;
        this.distributedLockHelper = distributedLockHelper;
    }

    @Scheduled(fixedRate = 60000)
    public void detectAcceptTimeout() {
        String lockKey = RedisKeyConstants.SCHEDULER_LOCK + "accept_timeout";
        String requestId = UUID.randomUUID().toString();

        boolean locked = distributedLockHelper.tryLock(lockKey, requestId, 55);
        if (!locked) {
            return;
        }

        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(acceptTimeoutMinutes);

            LambdaQueryWrapper<RepairOrder> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(RepairOrder::getStatus, OrderStatus.ASSIGNED)
                    .isNotNull(RepairOrder::getAssignedAt)
                    .lt(RepairOrder::getAssignedAt, cutoffTime);

            List<RepairOrder> timeoutOrders = repairOrderMapper.selectList(wrapper);

            for (RepairOrder order : timeoutOrders) {
                try {
                    log.info("Escalating order {} due to accept timeout (assigned at: {})",
                            order.getOrderNo(), order.getAssignedAt());
                    timeoutEscalationService.escalateOrder(order, order.getCurrentWorkerId());
                } catch (Exception e) {
                    log.error("Failed to escalate order {}: {}", order.getOrderNo(), e.getMessage());
                }
            }

            if (!timeoutOrders.isEmpty()) {
                log.info("Accept timeout detection completed. Escalated {} orders.", timeoutOrders.size());
            }
        } finally {
            distributedLockHelper.releaseLock(lockKey, requestId);
        }
    }
}
