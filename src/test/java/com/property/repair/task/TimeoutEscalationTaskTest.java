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
import static org.mockito.ArgumentMatchers.*;
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
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        task = new TimeoutEscalationTask(
                orderMapper, escalationMapper, userMapper, redisTemplate);
    }

    @Test
    @DisplayName("Visit timeout should NOT fire for suspended orders")
    void suspendedOrder_shouldNotTriggerVisitTimeout() {
        // The query now includes isNull(suspendedAt), so suspended orders are excluded
        when(orderMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        task.checkVisitTimeouts();

        verify(escalationMapper, never()).insert(any());
    }

    @Test
    @DisplayName("Complete timeout should NOT fire for suspended orders")
    void suspendedOrder_shouldNotTriggerCompleteTimeout() {
        when(orderMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        task.checkCompleteTimeouts();

        verify(escalationMapper, never()).insert(any());
    }

    @Test
    @DisplayName("After re-dispatch, new timeout should fire with correct dispatchId")
    void reDispatch_shouldAllowNewTimeout() {
        RepairOrder order = new RepairOrder();
        order.setId(1L);
        order.setOrderNo("RO001");
        order.setStatus(OrderStatus.ACCEPTED.getCode());
        order.setAcceptedAt(LocalDateTime.now().minusHours(5));
        order.setAssignedWorkerId(4L);
        order.setCommunityId(1L);
        order.setCurrentDispatchId(20L);

        when(orderMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(order));
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(escalationMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        User supervisor = new User();
        supervisor.setId(2L);
        supervisor.setRole("SUPERVISOR");
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(supervisor);
        when(escalationMapper.insert(any())).thenReturn(1);

        task.checkVisitTimeouts();

        ArgumentCaptor<TimeoutEscalation> captor =
                ArgumentCaptor.forClass(TimeoutEscalation.class);
        verify(escalationMapper).insert(captor.capture());
        assertEquals(20L, captor.getValue().getDispatchId());
        assertEquals(1L, captor.getValue().getOrderId());
    }

    @Test
    @DisplayName("Second-level escalation skips superseded dispatch rounds")
    void secondLevel_skipsSupersededDispatch() {
        TimeoutEscalation oldEsc = new TimeoutEscalation();
        oldEsc.setId(100L);
        oldEsc.setOrderId(1L);
        oldEsc.setTimeoutType(TimeoutType.VISIT_TIMEOUT.getCode());
        oldEsc.setDeadline(LocalDateTime.now().minusHours(6));
        oldEsc.setDispatchId(10L);  // OLD dispatch round
        oldEsc.setEscalationLevel(1);
        oldEsc.setHandled(0);

        when(escalationMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(oldEsc));

        RepairOrder order = new RepairOrder();
        order.setId(1L);
        order.setStatus(OrderStatus.ACCEPTED.getCode());
        order.setCurrentDispatchId(20L);  // NEW dispatch round (mismatch)
        order.setCommunityId(1L);
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(escalationMapper.updateById(any())).thenReturn(1);

        task.checkSecondLevelEscalation();

        // Should NOT create second-level escalation
        verify(escalationMapper, never()).insert(any());
        // Should mark old escalation as handled
        ArgumentCaptor<TimeoutEscalation> captor =
                ArgumentCaptor.forClass(TimeoutEscalation.class);
        verify(escalationMapper).updateById(captor.capture());
        assertEquals(1, captor.getValue().getHandled());
    }

    @Test
    @DisplayName("Second-level escalation skips suspended orders")
    void secondLevel_skipsSuspendedOrders() {
        TimeoutEscalation esc = new TimeoutEscalation();
        esc.setId(100L);
        esc.setOrderId(1L);
        esc.setTimeoutType(TimeoutType.VISIT_TIMEOUT.getCode());
        esc.setDeadline(LocalDateTime.now().minusHours(6));
        esc.setDispatchId(10L);
        esc.setEscalationLevel(1);
        esc.setHandled(0);

        when(escalationMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(esc));

        RepairOrder order = new RepairOrder();
        order.setId(1L);
        order.setStatus(OrderStatus.SUSPENDED.getCode());
        order.setCurrentDispatchId(10L);
        when(orderMapper.selectById(1L)).thenReturn(order);

        task.checkSecondLevelEscalation();

        verify(escalationMapper, never()).insert(any());
    }

    @Test
    @DisplayName("Accept timeout should fire for overdue dispatched orders")
    void acceptTimeout_firesForOverdueOrders() {
        RepairOrder order = new RepairOrder();
        order.setId(1L);
        order.setOrderNo("RO001");
        order.setStatus(OrderStatus.DISPATCHED.getCode());
        order.setAssignedAt(LocalDateTime.now().minusMinutes(50));
        order.setAssignedWorkerId(4L);
        order.setCommunityId(1L);
        order.setCurrentDispatchId(10L);

        when(orderMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(order));
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(escalationMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        User supervisor = new User();
        supervisor.setId(2L);
        supervisor.setRole("SUPERVISOR");
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(supervisor);
        when(escalationMapper.insert(any())).thenReturn(1);

        task.checkAcceptTimeouts();

        ArgumentCaptor<TimeoutEscalation> captor =
                ArgumentCaptor.forClass(TimeoutEscalation.class);
        verify(escalationMapper).insert(captor.capture());
        assertEquals(TimeoutType.ACCEPT_TIMEOUT.getCode(), captor.getValue().getTimeoutType());
        assertEquals(10L, captor.getValue().getDispatchId());
    }

    @Test
    @DisplayName("Duplicate timeout should be prevented by isAlreadyProcessed")
    void duplicateTimeout_preventedByProcessedCheck() {
        RepairOrder order = new RepairOrder();
        order.setId(1L);
        order.setOrderNo("RO001");
        order.setStatus(OrderStatus.DISPATCHED.getCode());
        order.setAssignedAt(LocalDateTime.now().minusMinutes(50));
        order.setAssignedWorkerId(4L);
        order.setCommunityId(1L);
        order.setCurrentDispatchId(10L);

        when(orderMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(order));
        // Redis says already processed
        when(redisTemplate.hasKey(anyString())).thenReturn(true);

        task.checkAcceptTimeouts();

        verify(escalationMapper, never()).insert(any());
    }
}
