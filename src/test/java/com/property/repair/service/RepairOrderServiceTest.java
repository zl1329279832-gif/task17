package com.property.repair.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.property.repair.dispatch.DefaultDispatchStrategy;
import com.property.repair.dto.RepairOrderSubmitRequest;
import com.property.repair.dto.RepairOrderVO;
import com.property.repair.dto.ManualDispatchRequest;
import com.property.repair.dto.TransferRequest;
import com.property.repair.dto.CompleteRequest;
import com.property.repair.dto.ReworkRequest;
import com.property.repair.dto.SuspendRequest;
import com.property.repair.entity.RepairOrder;
import com.property.repair.entity.Review;
import com.property.repair.entity.User;
import com.property.repair.entity.DispatchRecord;
import com.property.repair.entity.TimeoutEscalation;
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
import org.springframework.test.util.ReflectionTestUtils;

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
    @Mock private TimeoutEscalationMapper timeoutEscalationMapper;
    @Mock private SparePartService sparePartService;
    @Mock private SparePartRequisitionMapper sparePartRequisitionMapper;
    @Mock private SparePartRequisitionItemMapper sparePartRequisitionItemMapper;
    @Mock private SparePartMapper sparePartMapper;

    private RepairOrderServiceImpl orderService;
    private OrderStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new OrderStateMachine();
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.delete(anyString())).thenReturn(true);
        lenient().when(valueOperations.setIfAbsent(any(), any(), any(java.time.Duration.class)))
                .thenReturn(true);

        orderService = new RepairOrderServiceImpl(
                orderMapper, dispatchRecordMapper, progressMapper,
                reviewMapper, reworkOrderMapper, userMapper,
                attachmentMapper, auditService, dispatchStrategy,
                stateMachine, redisTemplate, timeoutEscalationMapper,
                sparePartService, sparePartRequisitionMapper,
                sparePartRequisitionItemMapper, sparePartMapper);

        // ServiceImpl.getById() uses baseMapper field which is normally set by Spring/MyBatis
        ReflectionTestUtils.setField(orderService, "baseMapper", orderMapper);
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

        // getOrderDetail after submit
        when(orderMapper.selectById(100L)).thenAnswer(inv -> {
            RepairOrder o = new RepairOrder();
            o.setId(100L);
            o.setOrderNo("RO20260101000001");
            o.setTitle("Leaking faucet");
            o.setProblemType("PLUMBING");
            o.setOwnerId(8L);
            o.setStatus(OrderStatus.DISPATCHED.getCode());
            o.setAssignedWorkerId(4L);
            return o;
        });

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

    @Test
    @DisplayName("Manual re-dispatch invalidates old timeout state")
    void manualReDispatch_invalidatesOldTimeouts() {
        RepairOrder order = new RepairOrder();
        order.setId(1L);
        order.setStatus(OrderStatus.PENDING.getCode());
        order.setAssignedWorkerId(4L);
        order.setCurrentDispatchId(10L);
        order.setOrderNo("RO001");
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(orderMapper.updateById(any())).thenReturn(1);
        when(progressMapper.insert(any())).thenReturn(1);

        // Old dispatch record
        DispatchRecord oldRecord = new DispatchRecord();
        oldRecord.setId(10L);
        oldRecord.setOrderId(1L);
        oldRecord.setActive(1);
        when(dispatchRecordMapper.selectById(10L)).thenReturn(oldRecord);
        when(dispatchRecordMapper.updateById(any())).thenReturn(1);

        // Old escalation record (unhandled)
        TimeoutEscalation oldEsc = new TimeoutEscalation();
        oldEsc.setId(100L);
        oldEsc.setOrderId(1L);
        oldEsc.setDispatchId(10L);
        oldEsc.setHandled(0);
        when(timeoutEscalationMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(oldEsc));
        when(timeoutEscalationMapper.updateById(any())).thenReturn(1);

        // New dispatch record insert
        when(dispatchRecordMapper.insert(any())).thenAnswer(inv -> {
            DispatchRecord r = inv.getArgument(0);
            r.setId(20L);
            return 1;
        });

        User newWorker = new User();
        newWorker.setId(5L);
        newWorker.setRealName("Li Si");
        newWorker.setRole("WORKER");
        when(userMapper.selectById(5L)).thenReturn(newWorker);
        when(orderMapper.countActiveOrders(5L)).thenReturn(1);

        ManualDispatchRequest request = new ManualDispatchRequest();
        request.setWorkerId(5L);
        request.setReason("Urgent reassignment");

        orderService.manualDispatch(1L, request);

        // Verify: old dispatch record deactivated (active=0)
        ArgumentCaptor<DispatchRecord> dispatchCaptor = ArgumentCaptor.forClass(DispatchRecord.class);
        verify(dispatchRecordMapper).updateById(dispatchCaptor.capture());
        assertEquals(0, dispatchCaptor.getValue().getActive());

        // Verify: old escalation marked as handled
        verify(timeoutEscalationMapper).updateById(any(TimeoutEscalation.class));

        // Verify: Redis cleanup (at least 6 keys deleted: 3 processed + 3 deadline)
        verify(redisTemplate, atLeast(6)).delete(anyString());

        // Verify: new dispatch record has active=1
        ArgumentCaptor<DispatchRecord> newDispatchCaptor = ArgumentCaptor.forClass(DispatchRecord.class);
        verify(dispatchRecordMapper).insert(newDispatchCaptor.capture());
        assertEquals(1, newDispatchCaptor.getValue().getActive());
    }

    @Test
    @DisplayName("Transfer invalidates old timeout state")
    void transferOrder_invalidatesOldTimeouts() {
        RepairOrder order = new RepairOrder();
        order.setId(1L);
        order.setStatus(OrderStatus.ACCEPTED.getCode());
        order.setAssignedWorkerId(4L);
        order.setCurrentDispatchId(10L);
        order.setOrderNo("RO001");
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(orderMapper.updateById(any())).thenReturn(1);
        when(progressMapper.insert(any())).thenReturn(1);

        DispatchRecord oldRecord = new DispatchRecord();
        oldRecord.setId(10L);
        oldRecord.setActive(1);
        when(dispatchRecordMapper.selectById(10L)).thenReturn(oldRecord);
        when(dispatchRecordMapper.updateById(any())).thenReturn(1);
        when(timeoutEscalationMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        when(dispatchRecordMapper.insert(any())).thenAnswer(inv -> {
            DispatchRecord r = inv.getArgument(0);
            r.setId(20L);
            return 1;
        });

        User targetWorker = new User();
        targetWorker.setId(5L);
        targetWorker.setRealName("Li Si");
        targetWorker.setRole("WORKER");
        when(userMapper.selectById(5L)).thenReturn(targetWorker);
        when(orderMapper.countActiveOrders(5L)).thenReturn(2);

        TransferRequest request = new TransferRequest();
        request.setTargetWorkerId(5L);
        request.setReason("Too busy");

        orderService.transferOrder(1L, request);

        verify(dispatchRecordMapper).updateById(any(DispatchRecord.class));
        verify(redisTemplate, atLeast(6)).delete(anyString());
    }

    @Test
    @DisplayName("Rework clears timestamps and soft-deletes old review")
    void rework_clearsOldReviewAndTimestamps() {
        RepairOrder order = new RepairOrder();
        order.setId(1L);
        order.setStatus(OrderStatus.COMPLETED.getCode());
        order.setAssignedWorkerId(4L);
        order.setOwnerId(8L);
        order.setOrderNo("RO001");
        order.setVisitAt(LocalDateTime.of(2026, 1, 1, 10, 0));
        order.setCompletedAt(LocalDateTime.of(2026, 1, 2, 14, 0));
        order.setCurrentDispatchId(10L);
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(orderMapper.updateById(any())).thenReturn(1);
        when(progressMapper.insert(any())).thenReturn(1);
        when(reworkOrderMapper.insert(any())).thenReturn(1);

        Review existingReview = new Review();
        existingReview.setId(50L);
        existingReview.setOrderId(1L);
        existingReview.setRating(2);
        existingReview.setDeleted(0);
        when(reviewMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existingReview);
        when(reviewMapper.updateById(any())).thenReturn(1);

        ReworkRequest request = new ReworkRequest();
        request.setReason("Still leaking");

        orderService.requestRework(1L, request);

        // Verify: order timestamps cleared
        ArgumentCaptor<RepairOrder> orderCaptor = ArgumentCaptor.forClass(RepairOrder.class);
        verify(orderMapper).updateById(orderCaptor.capture());
        RepairOrder updated = orderCaptor.getValue();
        assertNull(updated.getVisitAt());
        assertNull(updated.getCompletedAt());
        assertNull(updated.getCurrentDispatchId());

        // Verify: review soft-deleted
        ArgumentCaptor<Review> reviewCaptor = ArgumentCaptor.forClass(Review.class);
        verify(reviewMapper).updateById(reviewCaptor.capture());
        assertEquals(1, reviewCaptor.getValue().getDeleted());

        // Verify: Redis cleanup
        verify(redisTemplate, atLeast(6)).delete(anyString());
    }

    @Test
    @DisplayName("Suspend records suspendedAt and clears all timeout Redis keys")
    void suspend_clearsTimeoutKeys() {
        RepairOrder order = new RepairOrder();
        order.setId(1L);
        order.setStatus(OrderStatus.VISITING.getCode());
        order.setAssignedWorkerId(4L);
        order.setOrderNo("RO001");
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(orderMapper.updateById(any())).thenReturn(1);
        when(progressMapper.insert(any())).thenReturn(1);

        SuspendRequest suspendReq = new SuspendRequest();
        suspendReq.setReason("Waiting for parts");
        orderService.suspendOrder(1L, suspendReq);

        ArgumentCaptor<RepairOrder> captor = ArgumentCaptor.forClass(RepairOrder.class);
        verify(orderMapper).updateById(captor.capture());
        assertNotNull(captor.getValue().getSuspendedAt());

        // Verify: all 6 Redis keys cleared (3 deadline + 3 processed)
        verify(redisTemplate, atLeast(6)).delete(anyString());
    }

    @Test
    @DisplayName("Resume recalculates SLA timestamps based on pause duration")
    void resume_recalculatesSLA() {
        RepairOrder order = new RepairOrder();
        order.setId(1L);
        order.setStatus(OrderStatus.SUSPENDED.getCode());
        order.setPreviousStatus(OrderStatus.VISITING.getCode());
        order.setAssignedWorkerId(4L);
        order.setOrderNo("RO001");
        order.setVisitAt(LocalDateTime.now().minusHours(2));
        order.setSuspendedAt(LocalDateTime.now().minusHours(1));
        order.setTotalSuspendedSeconds(0);
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(orderMapper.updateById(any())).thenReturn(1);
        when(progressMapper.insert(any())).thenReturn(1);

        orderService.resumeOrder(1L, 4L);

        ArgumentCaptor<RepairOrder> captor = ArgumentCaptor.forClass(RepairOrder.class);
        verify(orderMapper).updateById(captor.capture());
        RepairOrder resumed = captor.getValue();

        // visitAt should be pushed forward by ~1 hour (the pause duration)
        assertTrue(resumed.getVisitAt().isAfter(LocalDateTime.now().minusHours(2)));
        assertNull(resumed.getSuspendedAt());
        assertTrue(resumed.getTotalSuspendedSeconds() > 0);
        assertEquals(OrderStatus.VISITING.getCode(), resumed.getStatus());

        // Verify: Redis timeout key re-established for complete timeout
        verify(valueOperations, atLeastOnce()).set(
                eq("timeout:complete:1"), anyString(), any(java.time.Duration.class));
    }
}
