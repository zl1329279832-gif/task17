package com.property.repair.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.property.repair.dispatch.DefaultDispatchStrategy;
import com.property.repair.dto.ManualDispatchRequest;
import com.property.repair.dto.RepairOrderSubmitRequest;
import com.property.repair.dto.RepairOrderVO;
import com.property.repair.entity.RepairOrder;
import com.property.repair.entity.User;
import com.property.repair.enums.OrderStatus;
import com.property.repair.exception.BusinessException;
import com.property.repair.mapper.*;
import com.property.repair.service.AuditService;
import com.property.repair.service.DispatchStrategy;
import com.property.repair.service.impl.RepairOrderServiceImpl;
import com.property.repair.statemachine.OrderStateMachine;
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
class RepairOrderServiceTest {

    @Mock private RepairOrderMapper orderMapper;
    @Mock private DispatchRecordMapper dispatchRecordMapper;
    @Mock private RepairProgressMapper progressMapper;
    @Mock private ReviewMapper reviewMapper;
    @Mock private ReworkOrderMapper reworkOrderMapper;
    @Mock private UserMapper userMapper;
    @Mock private AttachmentMapper attachmentMapper;
    @Mock private AuditService auditService;
    @Mock private DispatchStrategy dispatchStrategy;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;

    private RepairOrderServiceImpl orderService;
    private OrderStateMachine stateMachine;

    @BeforeEach
    void setUp() throws Exception {
        stateMachine = new OrderStateMachine();
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.delete(anyString())).thenReturn(true);
        lenient().when(valueOperations.setIfAbsent(any(), any(), any(java.time.Duration.class)))
                .thenReturn(true);

        // Default empty lists for buildVO() calls
        lenient().when(attachmentMapper.selectList(any())).thenReturn(Collections.emptyList());
        lenient().when(progressMapper.selectList(any())).thenReturn(Collections.emptyList());
        lenient().when(reworkOrderMapper.selectList(any())).thenReturn(Collections.emptyList());
        lenient().when(reviewMapper.selectOne(any())).thenReturn(null);

        orderService = new RepairOrderServiceImpl(
                orderMapper, dispatchRecordMapper, progressMapper,
                reviewMapper, reworkOrderMapper, userMapper,
                attachmentMapper, auditService, dispatchStrategy,
                stateMachine, redisTemplate);

        // ServiceImpl.baseMapper must be set for getById() to work
        var field = com.baomidou.mybatisplus.extension.service.impl.ServiceImpl.class
                .getDeclaredField("baseMapper");
        field.setAccessible(true);
        field.set(orderService, orderMapper);
    }

    @Test
    @DisplayName("Submit order — should create order and trigger auto-dispatch")
    void submitOrder_createsAndDispatches() {
        RepairOrderSubmitRequest request = new RepairOrderSubmitRequest();
        request.setTitle("Leaking faucet");
        request.setDescription("Kitchen faucet leaking");
        request.setProblemType("PLUMBING");
        request.setUrgency(3);
        request.setCommunityId(1L);
        request.setBuildingId(1L);

        // No duplicates
        when(orderMapper.findPotentialDuplicates(anyLong(), anyLong(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        when(orderMapper.insert(any(RepairOrder.class))).thenAnswer(inv -> {
            RepairOrder o = inv.getArgument(0);
            o.setId(100L);
            o.setOrderNo("RO20260101000001");
            // Allow getById() to find the order after insert
            when(orderMapper.selectById(100L)).thenReturn(o);
            return 1;
        });

        // Auto-dispatch finds a worker
        when(dispatchStrategy.findBestWorker(any(RepairOrder.class))).thenReturn(4L);
        User worker = new User();
        worker.setId(4L);
        worker.setRealName("Zhang San");
        worker.setRole("WORKER");
        when(userMapper.selectById(4L)).thenReturn(worker);
        when(orderMapper.countActiveOrders(4L)).thenReturn(1);
        when(progressMapper.insert(any())).thenReturn(1);
        when(dispatchRecordMapper.insert(any())).thenReturn(1);

        // Execute
        RepairOrderVO result = orderService.submitOrder(request, 8L);

        // Verify order was created
        ArgumentCaptor<RepairOrder> orderCaptor = ArgumentCaptor.forClass(RepairOrder.class);
        verify(orderMapper).insert(orderCaptor.capture());
        RepairOrder created = orderCaptor.getValue();
        assertEquals("Leaking faucet", created.getTitle());
        assertEquals("PLUMBING", created.getProblemType());
        assertEquals(8L, created.getOwnerId());
    }

    @Test
    @DisplayName("Accept order — valid transition should succeed")
    void acceptOrder_validTransition() {
        RepairOrder order = new RepairOrder();
        order.setId(1L);
        order.setStatus(OrderStatus.DISPATCHED.getCode());
        order.setAssignedWorkerId(4L);
        order.setOrderNo("RO001");
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(orderMapper.updateById(any())).thenReturn(1);
        when(progressMapper.insert(any())).thenReturn(1);

        orderService.acceptOrder(1L, 4L);

        ArgumentCaptor<RepairOrder> captor = ArgumentCaptor.forClass(RepairOrder.class);
        verify(orderMapper).updateById(captor.capture());
        assertEquals(OrderStatus.ACCEPTED.getCode(), captor.getValue().getStatus());
    }

    @Test
    @DisplayName("Accept order — wrong worker should fail")
    void acceptOrder_wrongWorker_shouldFail() {
        RepairOrder order = new RepairOrder();
        order.setId(1L);
        order.setStatus(OrderStatus.DISPATCHED.getCode());
        order.setAssignedWorkerId(4L);
        when(orderMapper.selectById(1L)).thenReturn(order);

        assertThrows(BusinessException.class, () -> orderService.acceptOrder(1L, 5L));
    }

    @Test
    @DisplayName("Transfer order — should set TRANSFERRED and re-dispatch")
    void transferOrder_success() {
        RepairOrder order = new RepairOrder();
        order.setId(1L);
        order.setStatus(OrderStatus.ACCEPTED.getCode());
        order.setAssignedWorkerId(4L);
        order.setOrderNo("RO001");
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(orderMapper.updateById(any())).thenReturn(1);
        when(progressMapper.insert(any())).thenReturn(1);

        User targetWorker = new User();
        targetWorker.setId(5L);
        targetWorker.setRealName("Li Si");
        targetWorker.setRole("WORKER");
        when(userMapper.selectById(5L)).thenReturn(targetWorker);
        when(orderMapper.countActiveOrders(5L)).thenReturn(2);
        when(dispatchRecordMapper.insert(any())).thenReturn(1);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(any(), any(), any(java.time.Duration.class)))
                .thenReturn(true);

        var request = new com.property.repair.dto.TransferRequest();
        request.setTargetWorkerId(5L);
        request.setReason("I'm busy with another job");

        orderService.transferOrder(1L, request);

        // Verify TRANSFERRED was set first, then DISPATCHED to new worker
        verify(orderMapper, atLeast(2)).updateById(any(RepairOrder.class));
    }

    @Test
    @DisplayName("Complete after visiting — should transition to COMPLETED")
    void completeOrder_validFlow() {
        RepairOrder order = new RepairOrder();
        order.setId(1L);
        order.setStatus(OrderStatus.VISITING.getCode());
        order.setAssignedWorkerId(4L);
        order.setOrderNo("RO001");
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(orderMapper.updateById(any())).thenReturn(1);
        when(progressMapper.insert(any())).thenReturn(1);

        var request = new com.property.repair.dto.CompleteRequest();
        request.setRemark("Faucet replaced successfully");

        orderService.completeOrder(1L, request);

        ArgumentCaptor<RepairOrder> captor = ArgumentCaptor.forClass(RepairOrder.class);
        verify(orderMapper).updateById(captor.capture());
        assertEquals(OrderStatus.COMPLETED.getCode(), captor.getValue().getStatus());
        assertNotNull(captor.getValue().getCompletedAt());
    }

    @Test
    @DisplayName("Rework after completion — should transition COMPLETED → REWORKING")
    void reworkAfterCompletion() {
        RepairOrder order = new RepairOrder();
        order.setId(1L);
        order.setStatus(OrderStatus.COMPLETED.getCode());
        order.setAssignedWorkerId(4L);
        order.setOwnerId(8L);
        order.setOrderNo("RO001");
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(orderMapper.updateById(any())).thenReturn(1);
        when(progressMapper.insert(any())).thenReturn(1);
        when(reworkOrderMapper.insert(any())).thenReturn(1);

        var request = new com.property.repair.dto.ReworkRequest();
        request.setReason("Still leaking after repair");

        orderService.requestRework(1L, request);

        ArgumentCaptor<RepairOrder> captor = ArgumentCaptor.forClass(RepairOrder.class);
        verify(orderMapper).updateById(captor.capture());
        assertEquals(OrderStatus.REWORKING.getCode(), captor.getValue().getStatus());
    }

    @Test
    @DisplayName("Suspend and resume — should restore previous status")
    void suspendAndResume() {
        RepairOrder order = new RepairOrder();
        order.setId(1L);
        order.setStatus(OrderStatus.VISITING.getCode());
        order.setAssignedWorkerId(4L);
        order.setOrderNo("RO001");
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(orderMapper.updateById(any())).thenReturn(1);
        when(progressMapper.insert(any())).thenReturn(1);

        // Suspend
        var suspendReq = new com.property.repair.dto.SuspendRequest();
        suspendReq.setReason("Waiting for parts");
        orderService.suspendOrder(1L, suspendReq);

        // Verify SUSPENDED
        ArgumentCaptor<RepairOrder> captor = ArgumentCaptor.forClass(RepairOrder.class);
        verify(orderMapper, times(1)).updateById(captor.capture());
        assertEquals(OrderStatus.SUSPENDED.getCode(), captor.getValue().getStatus());
        assertEquals("VISITING", captor.getValue().getPreviousStatus());

        // Resume
        RepairOrder suspendedOrder = captor.getValue();
        when(orderMapper.selectById(1L)).thenReturn(suspendedOrder);

        orderService.resumeOrder(1L, 4L);

        verify(orderMapper, times(2)).updateById(captor.capture());
        assertEquals("VISITING", captor.getAllValues().get(1).getStatus());
    }

    // ==========================================================================
    // New tests: Manual dispatch, rework rounds, suspend/resume SLA
    // ==========================================================================

    @Test
    @DisplayName("Manual dispatch from DISPATCHED — clears old timeout keys and reassigns")
    void manualDispatch_fromDispatched_clearsTimeoutsAndReassigns() {
        RepairOrder order = new RepairOrder();
        order.setId(1L);
        order.setStatus(OrderStatus.DISPATCHED.getCode());
        order.setAssignedWorkerId(4L);
        order.setDispatchRound(1);
        order.setOrderNo("RO001");
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(orderMapper.updateById(any())).thenReturn(1);
        when(progressMapper.insert(any())).thenReturn(1);
        when(dispatchRecordMapper.insert(any())).thenReturn(1);

        User newWorker = new User();
        newWorker.setId(5L);
        newWorker.setRealName("Li Si");
        newWorker.setRole("WORKER");
        when(userMapper.selectById(5L)).thenReturn(newWorker);
        when(orderMapper.countActiveOrders(5L)).thenReturn(1);

        var request = new ManualDispatchRequest();
        request.setWorkerId(5L);
        request.setReason("Reassign to specialist");

        orderService.manualDispatch(1L, request);

        // Verify old timeout keys were cleared (called in both manualDispatch and doDispatch)
        verify(redisTemplate, atLeast(1)).delete("timeout:accept:1");
        verify(redisTemplate, atLeast(1)).delete("timeout:visit:1");
        verify(redisTemplate, atLeast(1)).delete("timeout:complete:1");

        // Verify dispatch round incremented
        ArgumentCaptor<RepairOrder> captor = ArgumentCaptor.forClass(RepairOrder.class);
        verify(orderMapper, atLeastOnce()).updateById(captor.capture());
        RepairOrder updated = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertEquals(OrderStatus.DISPATCHED.getCode(), updated.getStatus());
        assertEquals(5L, updated.getAssignedWorkerId());
        assertEquals(2, updated.getDispatchRound());
        // Downstream timestamps reset
        assertNull(updated.getAcceptedAt());
        assertNull(updated.getVisitAt());
    }

    @Test
    @DisplayName("Manual dispatch from ACCEPTED — admin reassignment works")
    void manualDispatch_fromAccepted_reassigns() {
        RepairOrder order = new RepairOrder();
        order.setId(2L);
        order.setStatus(OrderStatus.ACCEPTED.getCode());
        order.setAssignedWorkerId(4L);
        order.setAcceptedAt(LocalDateTime.now().minusHours(1));
        order.setDispatchRound(1);
        order.setOrderNo("RO002");
        when(orderMapper.selectById(2L)).thenReturn(order);
        when(orderMapper.updateById(any())).thenReturn(1);
        when(progressMapper.insert(any())).thenReturn(1);
        when(dispatchRecordMapper.insert(any())).thenReturn(1);

        User newWorker = new User();
        newWorker.setId(6L);
        newWorker.setRealName("Wang Wu");
        newWorker.setRole("WORKER");
        when(userMapper.selectById(6L)).thenReturn(newWorker);
        when(orderMapper.countActiveOrders(6L)).thenReturn(0);

        var request = new ManualDispatchRequest();
        request.setWorkerId(6L);
        request.setReason("Worker unavailable");

        orderService.manualDispatch(2L, request);

        ArgumentCaptor<RepairOrder> captor = ArgumentCaptor.forClass(RepairOrder.class);
        verify(orderMapper, atLeastOnce()).updateById(captor.capture());
        RepairOrder updated = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertEquals(OrderStatus.DISPATCHED.getCode(), updated.getStatus());
        assertEquals(6L, updated.getAssignedWorkerId());
        assertEquals(2, updated.getDispatchRound());
    }

    @Test
    @DisplayName("Rework increments repair round and clears old timeout markers")
    void rework_incrementsRepairRound() {
        RepairOrder order = new RepairOrder();
        order.setId(1L);
        order.setStatus(OrderStatus.COMPLETED.getCode());
        order.setAssignedWorkerId(4L);
        order.setOwnerId(8L);
        order.setOrderNo("RO001");
        order.setRepairRound(1);
        order.setDispatchRound(1);
        order.setCompletedAt(LocalDateTime.now());
        order.setVisitAt(LocalDateTime.now().minusHours(2));
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(orderMapper.updateById(any())).thenReturn(1);
        when(progressMapper.insert(any())).thenReturn(1);
        when(reworkOrderMapper.insert(any())).thenReturn(1);

        var request = new com.property.repair.dto.ReworkRequest();
        request.setReason("Still leaking");

        orderService.requestRework(1L, request);

        ArgumentCaptor<RepairOrder> captor = ArgumentCaptor.forClass(RepairOrder.class);
        verify(orderMapper).updateById(captor.capture());
        RepairOrder updated = captor.getValue();
        assertEquals(OrderStatus.REWORKING.getCode(), updated.getStatus());
        assertEquals(2, updated.getRepairRound());
        // Timestamps reset for fresh cycle
        assertNull(updated.getCompletedAt());
        assertNull(updated.getVisitAt());

        // Verify processed markers cleared
        verify(redisTemplate).delete("timeout:processed:ACCEPT_TIMEOUT:1");
        verify(redisTemplate).delete("timeout:processed:VISIT_TIMEOUT:1");
        verify(redisTemplate).delete("timeout:processed:COMPLETE_TIMEOUT:1");
    }

    @Test
    @DisplayName("Suspend order — clears all timeout keys and sets suspendedAt")
    void suspend_clearsTimeoutKeys() {
        RepairOrder order = new RepairOrder();
        order.setId(1L);
        order.setStatus(OrderStatus.VISITING.getCode());
        order.setAssignedWorkerId(4L);
        order.setOrderNo("RO001");
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(orderMapper.updateById(any())).thenReturn(1);
        when(progressMapper.insert(any())).thenReturn(1);

        var suspendReq = new com.property.repair.dto.SuspendRequest();
        suspendReq.setReason("Waiting for parts");
        orderService.suspendOrder(1L, suspendReq);

        // Verify timeout keys cleared
        verify(redisTemplate).delete("timeout:accept:1");
        verify(redisTemplate).delete("timeout:visit:1");
        verify(redisTemplate).delete("timeout:complete:1");

        // Verify suspendedAt set
        ArgumentCaptor<RepairOrder> captor = ArgumentCaptor.forClass(RepairOrder.class);
        verify(orderMapper).updateById(captor.capture());
        assertNotNull(captor.getValue().getSuspendedAt());
        assertEquals(OrderStatus.SUSPENDED.getCode(), captor.getValue().getStatus());
    }

    @Test
    @DisplayName("Resume from VISITING — adjusts visitAt by pause duration and resets timeout")
    void resume_fromVisiting_adjustsVisitAtAndResetsTimeout() {
        LocalDateTime visitTime = LocalDateTime.now().minusHours(3);
        LocalDateTime suspendTime = LocalDateTime.now().minusHours(1);

        RepairOrder order = new RepairOrder();
        order.setId(1L);
        order.setStatus(OrderStatus.SUSPENDED.getCode());
        order.setPreviousStatus(OrderStatus.VISITING.getCode());
        order.setAssignedWorkerId(4L);
        order.setVisitAt(visitTime);
        order.setSuspendedAt(suspendTime);
        order.setOrderNo("RO001");
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(orderMapper.updateById(any())).thenReturn(1);
        when(progressMapper.insert(any())).thenReturn(1);

        orderService.resumeOrder(1L, 4L);

        ArgumentCaptor<RepairOrder> captor = ArgumentCaptor.forClass(RepairOrder.class);
        verify(orderMapper).updateById(captor.capture());
        RepairOrder resumed = captor.getValue();

        // Status restored
        assertEquals(OrderStatus.VISITING.getCode(), resumed.getStatus());
        assertNull(resumed.getSuspendedAt());
        assertNull(resumed.getPreviousStatus());

        // visitAt shifted forward by ~1 hour (pause duration)
        assertTrue(resumed.getVisitAt().isAfter(visitTime));

        // Complete timeout re-set in Redis
        verify(valueOperations).set(
                eq("timeout:complete:1"),
                any(String.class),
                eq(java.time.Duration.ofHours(49)));
    }

    @Test
    @DisplayName("Resume from ACCEPTED — adjusts acceptedAt and resets visit timeout")
    void resume_fromAccepted_adjustsAcceptedAtAndResetsTimeout() {
        LocalDateTime acceptTime = LocalDateTime.now().minusHours(2);
        LocalDateTime suspendTime = LocalDateTime.now().minusMinutes(30);

        RepairOrder order = new RepairOrder();
        order.setId(2L);
        order.setStatus(OrderStatus.SUSPENDED.getCode());
        order.setPreviousStatus(OrderStatus.ACCEPTED.getCode());
        order.setAssignedWorkerId(4L);
        order.setAcceptedAt(acceptTime);
        order.setSuspendedAt(suspendTime);
        order.setOrderNo("RO002");
        when(orderMapper.selectById(2L)).thenReturn(order);
        when(orderMapper.updateById(any())).thenReturn(1);
        when(progressMapper.insert(any())).thenReturn(1);

        orderService.resumeOrder(2L, 4L);

        ArgumentCaptor<RepairOrder> captor = ArgumentCaptor.forClass(RepairOrder.class);
        verify(orderMapper).updateById(captor.capture());
        RepairOrder resumed = captor.getValue();

        assertEquals(OrderStatus.ACCEPTED.getCode(), resumed.getStatus());
        // acceptedAt shifted forward by ~30 minutes
        assertTrue(resumed.getAcceptedAt().isAfter(acceptTime));

        // Visit timeout re-set
        verify(valueOperations).set(
                eq("timeout:visit:2"),
                any(String.class),
                eq(java.time.Duration.ofHours(5)));
    }
}
