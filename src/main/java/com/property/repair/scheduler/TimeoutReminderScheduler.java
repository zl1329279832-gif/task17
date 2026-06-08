package com.property.repair.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.property.repair.common.constants.RedisKeyConstants;
import com.property.repair.common.enums.OrderStatus;
import com.property.repair.entity.RepairOrder;
import com.property.repair.mapper.RepairOrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
public class TimeoutReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(TimeoutReminderScheduler.class);

    private final RepairOrderMapper repairOrderMapper;
    private final DistributedLockHelper distributedLockHelper;

    @Value("${repair.repair-timeout-hours:24}")
    private int repairTimeoutHours;

    public TimeoutReminderScheduler(RepairOrderMapper repairOrderMapper,
                                    DistributedLockHelper distributedLockHelper) {
        this.repairOrderMapper = repairOrderMapper;
        this.distributedLockHelper = distributedLockHelper;
    }

    @Scheduled(fixedRate = 300000)
    public void detectRepairTimeout() {
        String lockKey = RedisKeyConstants.SCHEDULER_LOCK + "repair_timeout";
        String requestId = UUID.randomUUID().toString();

        boolean locked = distributedLockHelper.tryLock(lockKey, requestId, 290);
        if (!locked) {
            return;
        }

        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusHours(repairTimeoutHours);

            LambdaQueryWrapper<RepairOrder> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(RepairOrder::getStatus, OrderStatus.IN_PROGRESS)
                    .isNotNull(RepairOrder::getStartedAt)
                    .lt(RepairOrder::getStartedAt, cutoffTime);

            List<RepairOrder> timeoutOrders = repairOrderMapper.selectList(wrapper);

            for (RepairOrder order : timeoutOrders) {
                log.warn("Repair timeout reminder - Order: {}, Worker: {}, Started at: {}, Duration: {}h",
                        order.getOrderNo(),
                        order.getCurrentWorkerId(),
                        order.getStartedAt(),
                        java.time.Duration.between(order.getStartedAt(), LocalDateTime.now()).toHours());
            }

            if (!timeoutOrders.isEmpty()) {
                log.info("Repair timeout check completed. {} orders exceeded {} hours.",
                        timeoutOrders.size(), repairTimeoutHours);
            }
        } finally {
            distributedLockHelper.releaseLock(lockKey, requestId);
        }
    }
}
