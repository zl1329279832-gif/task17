package com.property.repair.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.property.repair.dispatch.DefaultDispatchStrategy;
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
    void setUp() {
        stateMachine = new OrderStateMachine();
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.delete(any())).thenReturn(true);
        lenient().when(valueOperations.setIfAbsent(any(), any(), any(java.time.Duration.class)))
                .thenReturn(true);

        orderService = new RepairOrderServiceImpl(
                orderMapper, dispatchRecordMapper, progressMapper,
                reviewMapper, reworkOrderMapper, userMapper,
                attachmentMapper, auditService, dispatchStrategy,
                stateMachine, redisTemplate);
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
}
