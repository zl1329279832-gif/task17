package com.property.repair.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.property.repair.entity.RepairOrder;
import com.property.repair.entity.TimeoutEscalation;
import com.property.repair.entity.User;
import com.property.repair.enums.OrderStatus;
import com.property.repair.enums.TimeoutType;
import com.property.repair.mapper.RepairOrderMapper;
import com.property.repair.mapper.TimeoutEscalationMapper;
import com.property.repair.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TimeoutEscalationTaskTest {

    @Mock private RepairOrderMapper orderMapper;
    @Mock private TimeoutEscalationMapper escalationMapper;
    @Mock private UserMapper userMapper;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;

    private TimeoutEscalationTask task;

    @BeforeEach
    void setUp() {
        task = new TimeoutEscalationTask(orderMapper, escalationMapper, userMapper, redisTemplate);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(escalationMapper.insert(any())).thenReturn(1);
    }

    // ---- Accept Timeout Tests ----

    @Test
    @DisplayName("Accept timeout — dispatched order past deadline triggers escalation")
    void acceptTimeout_dispatchedOrder_createsEscalation() {
        RepairOrder order = createOrder(1L, OrderStatus.DISPATCHED, 1, 1);
        order.setAssignedAt(LocalDateTime.now().minusHours(1)); // well past 30+15 min
        order.setAssignedWorkerId(4L);
        order.setCommunityId(1L);

        when(orderMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(order));
        when(redisTemplate.hasKey(any())).thenReturn(false);
        when(escalationMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        // Supervisor lookup
        User supervisor = new User();
        supervisor.setId(3L);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(supervisor);

        task.checkAcceptTimeouts();

        // Verify escalation was created with correct round info
        ArgumentCaptor<TimeoutEscalation> captor = ArgumentCaptor.forClass(TimeoutEscalation.class);
        verify(escalationMapper).insert(captor.capture());
        TimeoutEscalation esc = captor.getValue();
        assertEquals(1L, esc.getOrderId());
        assertEquals(TimeoutType.ACCEPT_TIMEOUT.getCode(), esc.getTimeoutType());
        assertEquals(1, esc.getDispatchRound());
        assertEquals(1, esc.getRepairRound());
        assertEquals(1, esc.getEscalationLevel());
        assertEquals(3L, esc.getEscalatedTo());

        // Verify marked as processed in Redis
        verify(valueOperations).set(
                eq("timeout:processed:ACCEPT_TIMEOUT:1:1:1"),
                eq("1"),
                eq(java.time.Duration.ofHours(24)));
    }

    @Test
    @DisplayName("Accept timeout — already processed order is skipped")
    void acceptTimeout_alreadyProcessed_skips() {
        RepairOrder order = createOrder(1L, OrderStatus.DISPATCHED, 1, 1);
        order.setAssignedAt(LocalDateTime.now().minusHours(1));
        order.setAssignedWorkerId(4L);

        when(orderMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(order));
        // Redis says already processed
        when(redisTemplate.hasKey("timeout:processed:ACCEPT_TIMEOUT:1:1:1")).thenReturn(true);

        task.checkAcceptTimeouts();

        // No escalation created
        verify(escalationMapper, never()).insert(any());
    }

    @Test
    @DisplayName("Accept timeout — stale dispatch round skips (order was reassigned)")
    void acceptTimeout_staleDispatchRound_skips() {
        // Order has been reassigned — dispatch round is now 2
        // But it was dispatched recently so no timeout yet at round 2
        RepairOrder order = createOrder(1L, OrderStatus.DISPATCHED, 2, 1);
        order.setAssignedAt(LocalDateTime.now().minusMinutes(5)); // recent dispatch
        order.setAssignedWorkerId(5L);

        when(orderMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(order));
        // Old round-1 processed marker won't match new round-2
        when(redisTemplate.hasKey("timeout:processed:ACCEPT_TIMEOUT:1:2:1")).thenReturn(false);
        when(escalationMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        task.checkAcceptTimeouts();

        // No escalation — deadline not yet reached
        verify(escalationMapper, never()).insert(any());
    }

    // ---- Suspended Order Tests ----

    @Test
    @DisplayName("Timeout check — suspended orders are not in query results")
    void timeout_suspendedOrder_notProcessed() {
        // Suspended orders have status SUSPENDED, not DISPATCHED/ACCEPTED/VISITING
        // So they won't be returned by the status-specific queries
        when(orderMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        task.checkAcceptTimeouts();
        task.checkVisitTimeouts();
        task.checkCompleteTimeouts();

        verify(escalationMapper, never()).insert(any());
    }

    // ---- Rework / New Round Tests ----

    @Test
    @DisplayName("Complete timeout — reworked order with new round triggers fresh escalation")
    void timeout_reworkedOrder_newRound_triggers() {
        // Order was reworked (repair round 2), worker is visiting but past deadline
        RepairOrder order = createOrder(1L, OrderStatus.VISITING, 1, 2);
        order.setVisitAt(LocalDateTime.now().minusHours(49)); // well past 48h + 15min
        order.setAssignedWorkerId(4L);
        order.setCommunityId(1L);

        when(orderMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(order));
        // No processed marker for the new round
        when(redisTemplate.hasKey("timeout:processed:COMPLETE_TIMEOUT:1:1:2")).thenReturn(false);
        // No DB record for this round
        when(escalationMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        User supervisor = new User();
        supervisor.setId(3L);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(supervisor);

        task.checkCompleteTimeouts();

        // Escalation created with repair round 2
        ArgumentCaptor<TimeoutEscalation> captor = ArgumentCaptor.forClass(TimeoutEscalation.class);
        verify(escalationMapper).insert(captor.capture());
        assertEquals(2, captor.getValue().getRepairRound());
        assertEquals(1, captor.getValue().getDispatchRound());
    }

    // ---- Second-Level Escalation Tests ----

    @Test
    @DisplayName("Second-level escalation — unhandled level-1 with matching round creates level-2")
    void secondLevelEscalation_unhandledLevel1_creates() {
        TimeoutEscalation level1 = new TimeoutEscalation();
        level1.setId(10L);
        level1.setOrderId(1L);
        level1.setTimeoutType(TimeoutType.ACCEPT_TIMEOUT.getCode());
        level1.setDeadline(LocalDateTime.now().minusHours(3));
        level1.setHandled(0);
        level1.setEscalationLevel(1);
        level1.setDispatchRound(1);
        level1.setRepairRound(1);
        level1.setCreatedAt(LocalDateTime.now().minusHours(3));

        when(escalationMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(level1));

        // Order still in DISPATCHED with matching rounds
        RepairOrder order = createOrder(1L, OrderStatus.DISPATCHED, 1, 1);
        when(orderMapper.selectById(1L)).thenReturn(order);

        // Admin for second-level
        User admin = new User();
        admin.setId(1L);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(admin);

        task.checkSecondLevelEscalation();

        ArgumentCaptor<TimeoutEscalation> captor = ArgumentCaptor.forClass(TimeoutEscalation.class);
        verify(escalationMapper).insert(captor.capture());
        assertEquals(2, captor.getValue().getEscalationLevel());
        assertEquals(1, captor.getValue().getDispatchRound());
    }

    @Test
    @DisplayName("Second-level escalation — stale round skips upgrade")
    void secondLevelEscalation_staleRound_skips() {
        TimeoutEscalation level1 = new TimeoutEscalation();
        level1.setId(10L);
        level1.setOrderId(1L);
        level1.setTimeoutType(TimeoutType.ACCEPT_TIMEOUT.getCode());
        level1.setDeadline(LocalDateTime.now().minusHours(3));
        level1.setHandled(0);
        level1.setEscalationLevel(1);
        level1.setDispatchRound(1);  // old round
        level1.setRepairRound(1);
        level1.setCreatedAt(LocalDateTime.now().minusHours(3));

        when(escalationMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(level1));

        // Order was reassigned — dispatch round is now 2
        RepairOrder order = createOrder(1L, OrderStatus.DISPATCHED, 2, 1);
        when(orderMapper.selectById(1L)).thenReturn(order);

        task.checkSecondLevelEscalation();

        // No level-2 created because round mismatch
        verify(escalationMapper, never()).insert(any());
    }

    // ---- Helpers ----

    private RepairOrder createOrder(Long id, OrderStatus status,
                                     int dispatchRound, int repairRound) {
        RepairOrder order = new RepairOrder();
        order.setId(id);
        order.setOrderNo("RO" + id);
        order.setStatus(status.getCode());
        order.setDispatchRound(dispatchRound);
        order.setRepairRound(repairRound);
        return order;
    }
}
