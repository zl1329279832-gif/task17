package com.property.repair.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.property.repair.entity.User;
import com.property.repair.enums.UserRole;
import com.property.repair.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Periodic task that updates worker online/offline status.
 * A worker is considered offline if no heartbeat for more than 10 minutes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkerStatusTask {

    private final UserMapper userMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String HEARTBEAT_PREFIX = "worker:heartbeat:";
    private static final int OFFLINE_THRESHOLD_MINUTES = 10;

    /**
     * Run every 2 minutes to check worker heartbeats.
     */
    @Scheduled(fixedDelay = 120000)
    public void checkWorkerOnlineStatus() {
        log.debug("Checking worker online status...");

        List<User> workers = userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .eq(User::getRole, UserRole.WORKER.getCode())
                        .eq(User::getStatus, 1));

        for (User worker : workers) {
            String heartbeatKey = HEARTBEAT_PREFIX + worker.getId();
            Boolean hasHeartbeat = redisTemplate.hasKey(heartbeatKey);

            if (hasHeartbeat != null && hasHeartbeat) {
                // Worker has recent heartbeat — mark online
                if (worker.getOnlineStatus() != 1) {
                    worker.setOnlineStatus(1);
                    worker.setLastOnlineAt(LocalDateTime.now());
                    userMapper.updateById(worker);
                    log.debug("Worker {} marked online", worker.getRealName());
                }
            } else {
                // No heartbeat — check if should mark offline
                if (worker.getOnlineStatus() == 1) {
                    if (worker.getLastOnlineAt() == null
                            || Duration.between(worker.getLastOnlineAt(), LocalDateTime.now())
                                    .toMinutes() > OFFLINE_THRESHOLD_MINUTES) {
                        worker.setOnlineStatus(0);
                        userMapper.updateById(worker);
                        log.info("Worker {} marked offline (no heartbeat)", worker.getRealName());
                    }
                }
            }
        }
    }
}
