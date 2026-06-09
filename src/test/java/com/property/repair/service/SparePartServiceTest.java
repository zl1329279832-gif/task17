package com.property.repair.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.property.repair.dto.*;
import com.property.repair.entity.*;
import com.property.repair.enums.*;
import com.property.repair.exception.BusinessException;
import com.property.repair.mapper.*;
import com.property.repair.service.impl.SparePartServiceImpl;
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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SparePartServiceTest {

    @Mock private SparePartMapper sparePartMapper;
    @Mock private SparePartInventoryMapper inventoryMapper;
    @Mock private PartRequestMapper partRequestMapper;
    @Mock private PartRequestItemMapper partRequestItemMapper;
    @Mock private PurchaseRequestMapper purchaseRequestMapper;
    @Mock private PartAuditLogMapper partAuditLogMapper;
    @Mock private RepairOrderMapper orderMapper;
    @Mock private RepairProgressMapper progressMapper;
    @Mock private UserMapper userMapper;
    @Mock private AuditService auditService;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;

    private SparePartServiceImpl sparePartService;
    private OrderStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new OrderStateMachine();
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.delete(anyString())).thenReturn(true);

        sparePartService = new SparePartServiceImpl(
                sparePartMapper, inventoryMapper, partRequestMapper,
                partRequestItemMapper, purchaseRequestMapper, partAuditLogMapper,
                orderMapper, progressMapper, userMapper, auditService,
                stateMachine, redisTemplate);
    }

    // ==========================================================================
    // Helper Methods
    // ==========================================================================

    private RepairOrder buildVisitingOrder() {
        RepairOrder order = new RepairOrder();
        order.setId(1L);
        order.setOrderNo("RO20260609000001");
        order.setStatus(OrderStatus.VISITING.getCode());
        order.setAssignedWorkerId(10L);
        order.setCommunityId(100L);
        order.setBuildingId(200L);
        order.setProblemType("PLUMBING");
        order.setVisitAt(LocalDateTime.now().minusHours(1));
        return order;
    }

    private RepairOrder buildAcceptedOrder() {
        RepairOrder order = new RepairOrder();
        order.setId(2L);
        order.setOrderNo("RO20260609000002");
        order.setStatus(OrderStatus.ACCEPTED.getCode());
        order.setAssignedWorkerId(10L);
        order.setCommunityId(100L);
        order.setBuildingId(200L);
        order.setProblemType("ELECTRICAL");
        order.setAcceptedAt(LocalDateTime.now().minusHours(2));
        return order;
    }

    private RepairOrder buildReworkingOrder() {
        RepairOrder order = new RepairOrder();
        order.setId(3L);
        order.setOrderNo("RO20260609000003");
        order.setStatus(OrderStatus.REWORKING.getCode());
        order.setAssignedWorkerId(10L);
        order.setCommunityId(100L);
        order.setBuildingId(200L);
        order.setProblemType("PLUMBING");
        return order;
    }

    private RepairOrder buildWaitingPartsOrder(String previousStatus) {
        RepairOrder order = new RepairOrder();
        order.setId(1L);
        order.setOrderNo("RO20260609000001");
        order.setStatus(OrderStatus.WAITING_PARTS.getCode());
        order.setPreviousStatus(previousStatus);
        order.setAssignedWorkerId(10L);
        order.setCommunityId(100L);
        order.setBuildingId(200L);
        order.setProblemType("PLUMBING");
        order.setWaitingPartsAt(LocalDateTime.now().minusHours(3));
        order.setTotalWaitingPartsSeconds(0);
        order.setVisitAt(LocalDateTime.now().minusHours(5));
        order.setAcceptedAt(LocalDateTime.now().minusHours(6));
        return order;
    }

    private SparePart buildFaucetPart() {
        SparePart part = new SparePart();
        part.setId(1L);
        part.setPartCode("PLUMB-FAUCET-001");
        part.setPartName("Kitchen Faucet");
        part.setCategory("PLUMBING");
        part.setSpecification("Chrome, single-handle");
        part.setUnit("PCS");
        part.setSafetyStock(5);
        part.setEnabled(1);
        return part;
    }

    private SparePart buildPipePart() {
        SparePart part = new SparePart();
        part.setId(2L);
        part.setPartCode("PLUMB-PIPE-001");
        part.setPartName("Copper Pipe 15mm");
        part.setCategory("PLUMBING");
        part.setUnit("METER");
        part.setSafetyStock(10);
        part.setEnabled(1);
        return part;
    }

    private SparePartInventory buildInventory(Long partId, int availableQty) {
        SparePartInventory inv = new SparePartInventory();
        inv.setId(partId * 10);
        inv.setPartId(partId);
        inv.setCommunityId(100L);
        inv.setBuildingId(200L);
        inv.setAvailableQty(availableQty);
        inv.setReservedQty(0);
        inv.setTotalQty(availableQty);
        inv.setLocationCode("B1-A03");
        return inv;
    }

    private PartRequestCreateRequest buildPartRequest(Long partId, int qty, boolean critical) {
        PartRequestCreateRequest req = new PartRequestCreateRequest();
        PartRequestCreateRequest.PartItemRequest item = new PartRequestCreateRequest.PartItemRequest();
        item.setPartId(partId);
        item.setQuantity(qty);
        item.setCritical(critical);
        req.setItems(List.of(item));
        req.setRemark("Needed for repair");
        return req;
    }

    // ==========================================================================
    // Test 1: Critical Parts Insufficient → Order Enters WAITING_PARTS, SLA Paused
    // ==========================================================================

    @Test
    @DisplayName("Critical parts insufficient — order enters WAITING_PARTS and SLA timer pauses")
    void criticalPartsInsufficient_orderEntersWaitingParts_slaPaused() {
        RepairOrder order = buildVisitingOrder();
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(orderMapper.updateById(any())).thenReturn(1);

        SparePart faucet = buildFaucetPart();
        when(sparePartMapper.selectById(1L)).thenReturn(faucet);

        // Stock is 0 — critical shortage
        when(inventoryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        // No duplicate active requests
        when(partRequestMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        when(partRequestMapper.insert(any())).thenAnswer(inv -> {
            PartRequest pr = inv.getArgument(0);
            pr.setId(50L);
            return 1;
        });
        when(partRequestItemMapper.insert(any())).thenReturn(1);
        when(partRequestItemMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());
        when(partAuditLogMapper.insert(any())).thenReturn(1);
        when(progressMapper.insert(any())).thenReturn(1);

        // Mock getOrderDetail-related queries for the returned VO
        when(partRequestMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        PartRequestCreateRequest request = buildPartRequest(1L, 1, true);
        PartRequestVO result = sparePartService.createPartRequest(1L, request, 10L);

        // Verify: order status changed to WAITING_PARTS
        ArgumentCaptor<RepairOrder> orderCaptor = ArgumentCaptor.forClass(RepairOrder.class);
        verify(orderMapper).updateById(orderCaptor.capture());
        RepairOrder updated = orderCaptor.getValue();
        assertEquals(OrderStatus.WAITING_PARTS.getCode(), updated.getStatus());
        assertEquals(OrderStatus.VISITING.getCode(), updated.getPreviousStatus());
        assertNotNull(updated.getWaitingPartsAt());

        // Verify: SLA timeout Redis keys cleared (6 keys)
        verify(redisTemplate, atLeast(6)).delete(anyString());

        // Verify: progress recorded
        verify(progressMapper).insert(any(RepairProgress.class));

        // Verify: audit logged
        verify(auditService).log(eq(1L), eq("CREATE_PART_REQUEST"), eq(10L),
                eq(UserRole.WORKER.getCode()), isNull(), any(), isNull());
    }

    // ==========================================================================
    // Test 2: Parts Arrived → Auto-resume from WAITING_PARTS, SLA Adjusted
    // ==========================================================================

    @Test
    @DisplayName("Parts arrived — auto-resume from WAITING_PARTS, SLA deadline adjusted")
    void partsArrived_autoResume_slaAdjusted() {
        RepairOrder order = buildWaitingPartsOrder(OrderStatus.VISITING.getCode());
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(orderMapper.updateById(any())).thenReturn(1);
        when(orderMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(order));

        // All critical items have been fully issued
        PartRequest activeRequest = new PartRequest();
        activeRequest.setId(50L);
        activeRequest.setOrderId(1L);
        activeRequest.setStatus(PartRequestStatus.ISSUED.getCode());
        when(partRequestMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList()); // No active pending requests

        when(progressMapper.insert(any())).thenReturn(1);

        // Execute: check and resume
        sparePartService.checkAndResumeWaitingOrders(100L, 200L);

        // Verify: order resumed to VISITING
        ArgumentCaptor<RepairOrder> captor = ArgumentCaptor.forClass(RepairOrder.class);
        verify(orderMapper).updateById(captor.capture());
        RepairOrder resumed = captor.getValue();
        assertEquals(OrderStatus.VISITING.getCode(), resumed.getStatus());
        assertNull(resumed.getWaitingPartsAt());
        assertNull(resumed.getPreviousStatus());
        assertTrue(resumed.getTotalWaitingPartsSeconds() > 0);

        // Verify: visitAt pushed forward (SLA adjusted)
        assertTrue(resumed.getVisitAt().isAfter(order.getVisitAt()));

        // Verify: Redis timeout key re-established for complete timeout
        verify(valueOperations).set(
                eq("timeout:complete:1"), anyString(), any(Duration.class));

        // Verify: worker notification published
        verify(valueOperations).set(
                eq("notification:parts_arrived:10"), any(), any(Duration.class));
    }

    // ==========================================================================
    // Test 3: Duplicate Request Prevention
    // ==========================================================================

    @Test
    @DisplayName("Duplicate request — should reject when active request already exists")
    void duplicateRequest_rejected() {
        RepairOrder order = buildVisitingOrder();
        when(orderMapper.selectById(1L)).thenReturn(order);

        // There's already an active PENDING request
        when(partRequestMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        PartRequestCreateRequest request = buildPartRequest(1L, 1, true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> sparePartService.createPartRequest(1L, request, 10L));
        assertTrue(ex.getMessage().contains("Duplicate request"));
    }

    @Test
    @DisplayName("Duplicate request — should reject when APPROVED request exists")
    void duplicateRequest_approvedExists_rejected() {
        RepairOrder order = buildVisitingOrder();
        when(orderMapper.selectById(1L)).thenReturn(order);

        // There's an active APPROVED request
        when(partRequestMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        PartRequestCreateRequest request = buildPartRequest(1L, 2, false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> sparePartService.createPartRequest(1L, request, 10L));
        assertTrue(ex.getMessage().contains("Duplicate request"));
    }

    // ==========================================================================
    // Test 4: Rework Second Material Request
    // ==========================================================================

    @Test
    @DisplayName("Rework second material request — type should be REWORK")
    void reworkSecondMaterialRequest_typeIsRework() {
        RepairOrder order = buildReworkingOrder();
        when(orderMapper.selectById(3L)).thenReturn(order);
        when(orderMapper.updateById(any())).thenReturn(1);

        SparePart faucet = buildFaucetPart();
        when(sparePartMapper.selectById(1L)).thenReturn(faucet);

        // Stock is sufficient
        SparePartInventory inv = buildInventory(1L, 10);
        when(inventoryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(inv);

        when(partRequestMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(partRequestMapper.insert(any())).thenAnswer(inv -> {
            PartRequest pr = inv.getArgument(0);
            pr.setId(60L);
            return 1;
        });
        when(partRequestItemMapper.insert(any())).thenReturn(1);
        when(partRequestItemMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());
        when(partRequestMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());
        when(partAuditLogMapper.insert(any())).thenReturn(1);

        PartRequestCreateRequest request = buildPartRequest(1L, 2, false);
        PartRequestVO result = sparePartService.createPartRequest(3L, request, 10L);

        // Verify: request type is REWORK
        ArgumentCaptor<PartRequest> captor = ArgumentCaptor.forClass(PartRequest.class);
        verify(partRequestMapper).insert(captor.capture());
        assertEquals(PartRequestType.REWORK.getCode(), captor.getValue().getRequestType());

        // Verify: order did NOT enter WAITING_PARTS (non-critical, stock sufficient)
        verify(orderMapper, never()).updateById(argThat(o ->
                OrderStatus.WAITING_PARTS.getCode().equals(((RepairOrder) o).getStatus())));
    }

    @Test
    @DisplayName("Rework second request with critical shortage — enters WAITING_PARTS from REWORKING")
    void reworkSecondRequest_criticalShortage_entersWaitingParts() {
        RepairOrder order = buildReworkingOrder();
        when(orderMapper.selectById(3L)).thenReturn(order);
        when(orderMapper.updateById(any())).thenReturn(1);

        SparePart faucet = buildFaucetPart();
        when(sparePartMapper.selectById(1L)).thenReturn(faucet);

        // Stock is 0 — critical shortage
        when(inventoryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        when(partRequestMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(partRequestMapper.insert(any())).thenAnswer(inv -> {
            PartRequest pr = inv.getArgument(0);
            pr.setId(61L);
            return 1;
        });
        when(partRequestItemMapper.insert(any())).thenReturn(1);
        when(partRequestItemMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());
        when(partRequestMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());
        when(partAuditLogMapper.insert(any())).thenReturn(1);
        when(progressMapper.insert(any())).thenReturn(1);

        PartRequestCreateRequest request = buildPartRequest(1L, 1, true);
        sparePartService.createPartRequest(3L, request, 10L);

        // Verify: order transitioned REWORKING → WAITING_PARTS
        ArgumentCaptor<RepairOrder> orderCaptor = ArgumentCaptor.forClass(RepairOrder.class);
        verify(orderMapper).updateById(orderCaptor.capture());
        assertEquals(OrderStatus.WAITING_PARTS.getCode(), orderCaptor.getValue().getStatus());
        assertEquals(OrderStatus.REWORKING.getCode(), orderCaptor.getValue().getPreviousStatus());
    }

    // ==========================================================================
    // Test 5: Issue Parts with Partial Fulfillment
    // ==========================================================================

    @Test
    @DisplayName("Issue parts — partial fulfillment keeps request in PARTIALLY_ISSUED")
    void issueParts_partialFulfillment() {
        PartRequest partRequest = new PartRequest();
        partRequest.setId(50L);
        partRequest.setOrderId(1L);
        partRequest.setStatus(PartRequestStatus.APPROVED.getCode());
        when(partRequestMapper.selectById(50L)).thenReturn(partRequest);
        when(partRequestMapper.updateById(any())).thenReturn(1);

        RepairOrder order = buildVisitingOrder();
        when(orderMapper.selectById(1L)).thenReturn(order);

        PartRequestItem item = new PartRequestItem();
        item.setId(100L);
        item.setRequestId(50L);
        item.setPartId(1L);
        item.setRequestedQty(3);
        item.setIssuedQty(0);
        item.setReturnedQty(0);
        item.setConsumedQty(0);
        item.setCritical(1);
        when(partRequestItemMapper.selectById(100L)).thenReturn(item);
        when(partRequestItemMapper.updateById(any())).thenReturn(1);

        SparePartInventory inv = buildInventory(1L, 10);
        when(inventoryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(inv);
        when(inventoryMapper.updateById(any())).thenReturn(1);

        // Only 1 of 3 items issued (partial)
        when(partRequestItemMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(item));
        when(partAuditLogMapper.insert(any())).thenReturn(1);

        IssuePartsRequest request = new IssuePartsRequest();
        IssuePartsRequest.IssueItem issueItem = new IssuePartsRequest.IssueItem();
        issueItem.setRequestItemId(100L);
        issueItem.setIssuedQty(1);
        request.setItems(List.of(issueItem));

        sparePartService.issueParts(50L, request, 99L);

        // Verify: item issued qty = 1 (partial)
        ArgumentCaptor<PartRequestItem> itemCaptor = ArgumentCaptor.forClass(PartRequestItem.class);
        verify(partRequestItemMapper).updateById(itemCaptor.capture());
        assertEquals(1, itemCaptor.getValue().getIssuedQty());

        // Verify: request status is PARTIALLY_ISSUED
        ArgumentCaptor<PartRequest> reqCaptor = ArgumentCaptor.forClass(PartRequest.class);
        verify(partRequestMapper).updateById(reqCaptor.capture());
        assertEquals(PartRequestStatus.PARTIALLY_ISSUED.getCode(), reqCaptor.getValue().getStatus());
    }

    // ==========================================================================
    // Test 6: Issue Parts Fully While WAITING_PARTS → Auto-resume
    // ==========================================================================

    @Test
    @DisplayName("Issue all critical parts while order in WAITING_PARTS → auto-resume")
    void issueAllCriticalParts_whileWaitingParts_autoResume() {
        RepairOrder order = buildWaitingPartsOrder(OrderStatus.VISITING.getCode());

        PartRequest partRequest = new PartRequest();
        partRequest.setId(50L);
        partRequest.setOrderId(1L);
        partRequest.setStatus(PartRequestStatus.APPROVED.getCode());
        when(partRequestMapper.selectById(50L)).thenReturn(partRequest);
        when(partRequestMapper.updateById(any())).thenReturn(1);

        when(orderMapper.selectById(1L)).thenReturn(order);
        when(orderMapper.updateById(any())).thenReturn(1);

        PartRequestItem item = new PartRequestItem();
        item.setId(100L);
        item.setRequestId(50L);
        item.setPartId(1L);
        item.setRequestedQty(2);
        item.setIssuedQty(0);
        item.setReturnedQty(0);
        item.setConsumedQty(0);
        item.setCritical(1);
        when(partRequestItemMapper.selectById(100L)).thenReturn(item);
        when(partRequestItemMapper.updateById(any())).thenReturn(1);

        SparePartInventory inv = buildInventory(1L, 10);
        when(inventoryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(inv);
        when(inventoryMapper.updateById(any())).thenReturn(1);

        // All items fully issued
        PartRequestItem fullyIssued = new PartRequestItem();
        fullyIssued.setId(100L);
        fullyIssued.setRequestId(50L);
        fullyIssued.setPartId(1L);
        fullyIssued.setRequestedQty(2);
        fullyIssued.setIssuedQty(2);
        fullyIssued.setCritical(1);
        when(partRequestItemMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(fullyIssued));

        // No active pending requests for canResumeOrder check
        when(progressMapper.insert(any())).thenReturn(1);
        when(partAuditLogMapper.insert(any())).thenReturn(1);

        IssuePartsRequest request = new IssuePartsRequest();
        IssuePartsRequest.IssueItem issueItem = new IssuePartsRequest.IssueItem();
        issueItem.setRequestItemId(100L);
        issueItem.setIssuedQty(2);
        request.setItems(List.of(issueItem));

        sparePartService.issueParts(50L, request, 99L);

        // Verify: order resumed from WAITING_PARTS to VISITING
        ArgumentCaptor<RepairOrder> orderCaptor = ArgumentCaptor.forClass(RepairOrder.class);
        verify(orderMapper, atLeast(1)).updateById(orderCaptor.capture());
        RepairOrder lastUpdate = orderCaptor.getAllValues().get(orderCaptor.getAllValues().size() - 1);
        assertEquals(OrderStatus.VISITING.getCode(), lastUpdate.getStatus());
        assertNull(lastUpdate.getWaitingPartsAt());
    }

    // ==========================================================================
    // Test 7: Non-critical Parts Shortage → Order Does NOT Enter WAITING_PARTS
    // ==========================================================================

    @Test
    @DisplayName("Non-critical parts shortage — order stays in current status")
    void nonCriticalPartsShortage_orderStaysInCurrentStatus() {
        RepairOrder order = buildVisitingOrder();
        when(orderMapper.selectById(1L)).thenReturn(order);

        SparePart pipe = buildPipePart();
        when(sparePartMapper.selectById(2L)).thenReturn(pipe);

        // Stock is 0 but item is non-critical
        when(inventoryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(partRequestMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(partRequestMapper.insert(any())).thenAnswer(inv -> {
            PartRequest pr = inv.getArgument(0);
            pr.setId(55L);
            return 1;
        });
        when(partRequestItemMapper.insert(any())).thenReturn(1);
        when(partRequestItemMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());
        when(partRequestMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());
        when(partAuditLogMapper.insert(any())).thenReturn(1);

        PartRequestCreateRequest request = buildPartRequest(2L, 5, false);
        sparePartService.createPartRequest(1L, request, 10L);

        // Verify: order status NOT changed to WAITING_PARTS
        verify(orderMapper, never()).updateById(argThat(o ->
                OrderStatus.WAITING_PARTS.getCode().equals(((RepairOrder) o).getStatus())));
    }

    // ==========================================================================
    // Test 8: Return Parts After Repair
    // ==========================================================================

    @Test
    @DisplayName("Return unused parts — inventory restored")
    void returnParts_inventoryRestored() {
        PartRequest partRequest = new PartRequest();
        partRequest.setId(50L);
        partRequest.setOrderId(1L);
        partRequest.setStatus(PartRequestStatus.ISSUED.getCode());
        when(partRequestMapper.selectById(50L)).thenReturn(partRequest);
        when(partRequestMapper.updateById(any())).thenReturn(1);

        RepairOrder order = buildVisitingOrder();
        when(orderMapper.selectById(1L)).thenReturn(order);

        PartRequestItem item = new PartRequestItem();
        item.setId(100L);
        item.setRequestId(50L);
        item.setPartId(1L);
        item.setRequestedQty(3);
        item.setIssuedQty(3);
        item.setReturnedQty(0);
        item.setConsumedQty(2);
        when(partRequestItemMapper.selectById(100L)).thenReturn(item);
        when(partRequestItemMapper.updateById(any())).thenReturn(1);

        SparePartInventory inv = buildInventory(1L, 5);
        when(inventoryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(inv);
        when(inventoryMapper.updateById(any())).thenReturn(1);

        // All items returned
        when(partRequestItemMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(item));
        when(partAuditLogMapper.insert(any())).thenReturn(1);

        ReturnPartsRequest request = new ReturnPartsRequest();
        ReturnPartsRequest.ReturnItem returnItem = new ReturnPartsRequest.ReturnItem();
        returnItem.setRequestItemId(100L);
        returnItem.setReturnedQty(1); // 3 issued - 2 consumed = 1 returnable
        request.setItems(List.of(returnItem));

        sparePartService.returnParts(50L, request, 10L);

        // Verify: inventory restored
        ArgumentCaptor<SparePartInventory> invCaptor = ArgumentCaptor.forClass(SparePartInventory.class);
        verify(inventoryMapper).updateById(invCaptor.capture());
        assertEquals(6, invCaptor.getValue().getAvailableQty()); // 5 + 1 returned

        // Verify: item returned qty updated
        ArgumentCaptor<PartRequestItem> itemCaptor = ArgumentCaptor.forClass(PartRequestItem.class);
        verify(partRequestItemMapper).updateById(itemCaptor.capture());
        assertEquals(1, itemCaptor.getValue().getReturnedQty());
    }

    @Test
    @DisplayName("Return too many parts — should reject")
    void returnParts_tooMany_rejected() {
        PartRequest partRequest = new PartRequest();
        partRequest.setId(50L);
        partRequest.setOrderId(1L);
        when(partRequestMapper.selectById(50L)).thenReturn(partRequest);

        RepairOrder order = buildVisitingOrder();
        when(orderMapper.selectById(1L)).thenReturn(order);

        PartRequestItem item = new PartRequestItem();
        item.setId(100L);
        item.setRequestId(50L);
        item.setPartId(1L);
        item.setRequestedQty(3);
        item.setIssuedQty(3);
        item.setReturnedQty(0);
        item.setConsumedQty(2);
        when(partRequestItemMapper.selectById(100L)).thenReturn(item);

        ReturnPartsRequest request = new ReturnPartsRequest();
        ReturnPartsRequest.ReturnItem returnItem = new ReturnPartsRequest.ReturnItem();
        returnItem.setRequestItemId(100L);
        returnItem.setReturnedQty(2); // Only 1 returnable (3-2=1), trying to return 2
        request.setItems(List.of(returnItem));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> sparePartService.returnParts(50L, request, 10L));
        assertTrue(ex.getMessage().contains("Invalid return quantity"));
    }

    // ==========================================================================
    // Test 9: Consume Parts
    // ==========================================================================

    @Test
    @DisplayName("Consume parts — records consumption correctly")
    void consumeParts_recordsConsumption() {
        PartRequest partRequest = new PartRequest();
        partRequest.setId(50L);
        partRequest.setOrderId(1L);
        when(partRequestMapper.selectById(50L)).thenReturn(partRequest);

        PartRequestItem item = new PartRequestItem();
        item.setId(100L);
        item.setRequestId(50L);
        item.setPartId(1L);
        item.setRequestedQty(3);
        item.setIssuedQty(3);
        item.setReturnedQty(0);
        item.setConsumedQty(0);
        when(partRequestItemMapper.selectById(100L)).thenReturn(item);
        when(partRequestItemMapper.updateById(any())).thenReturn(1);
        when(partAuditLogMapper.insert(any())).thenReturn(1);

        ConsumePartsRequest request = new ConsumePartsRequest();
        ConsumePartsRequest.ConsumeItem consumeItem = new ConsumePartsRequest.ConsumeItem();
        consumeItem.setRequestItemId(100L);
        consumeItem.setConsumedQty(2);
        request.setItems(List.of(consumeItem));

        sparePartService.consumeParts(50L, request, 10L);

        ArgumentCaptor<PartRequestItem> captor = ArgumentCaptor.forClass(PartRequestItem.class);
        verify(partRequestItemMapper).updateById(captor.capture());
        assertEquals(2, captor.getValue().getConsumedQty());
    }

    // ==========================================================================
    // Test 10: Purchase Request → Receive → Stock-in → Resume Orders
    // ==========================================================================

    @Test
    @DisplayName("Purchase receive — increases stock and triggers order resume check")
    void purchaseReceive_increasesStock_triggersResume() {
        PurchaseRequest purchase = new PurchaseRequest();
        purchase.setId(70L);
        purchase.setPurchaseNo("PO20260609000001");
        purchase.setPartId(1L);
        purchase.setCommunityId(100L);
        purchase.setQuantity(10);
        purchase.setStatus(PurchaseRequestStatus.ORDERED.getCode());
        when(purchaseRequestMapper.selectById(70L)).thenReturn(purchase);
        when(purchaseRequestMapper.updateById(any())).thenReturn(1);

        SparePartInventory inv = buildInventory(1L, 0);
        when(inventoryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(inv);
        when(inventoryMapper.updateById(any())).thenReturn(1);

        // No waiting orders to resume
        when(orderMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());
        when(partAuditLogMapper.insert(any())).thenReturn(1);

        sparePartService.receivePurchase(70L, 99L);

        // Verify: stock increased
        ArgumentCaptor<SparePartInventory> invCaptor = ArgumentCaptor.forClass(SparePartInventory.class);
        verify(inventoryMapper).updateById(invCaptor.capture());
        assertEquals(10, invCaptor.getValue().getAvailableQty());

        // Verify: purchase status changed to RECEIVED
        ArgumentCaptor<PurchaseRequest> prCaptor = ArgumentCaptor.forClass(PurchaseRequest.class);
        verify(purchaseRequestMapper).updateById(prCaptor.capture());
        assertEquals(PurchaseRequestStatus.RECEIVED.getCode(), prCaptor.getValue().getStatus());
        assertNotNull(prCaptor.getValue().getReceivedAt());
    }

    // ==========================================================================
    // Test 11: Part Recommendation
    // ==========================================================================

    @Test
    @DisplayName("Recommend parts — returns historical parts with stock info")
    void recommendParts_returnsHistoricalWithStock() {
        RepairOrder order = buildVisitingOrder();
        when(orderMapper.selectById(1L)).thenReturn(order);

        SparePart faucet = buildFaucetPart();
        SparePart pipe = buildPipePart();
        when(sparePartMapper.findHistoricalParts("PLUMBING", 200L))
                .thenReturn(List.of(faucet, pipe));

        SparePartInventory inv1 = buildInventory(1L, 5);
        SparePartInventory inv2 = buildInventory(2L, 0);
        when(inventoryMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(inv1)
                .thenReturn(inv2);

        PartRecommendationVO result = sparePartService.recommendParts(1L);

        assertNotNull(result);
        assertEquals(1L, result.getOrderId());
        assertEquals("PLUMBING", result.getProblemType());
        assertEquals(2, result.getRecommendedParts().size());

        // Faucet has stock
        assertTrue(result.getRecommendedParts().get(0).isStockSufficient());
        assertEquals(5, result.getRecommendedParts().get(0).getAvailableStock());

        // Pipe has no stock
        assertFalse(result.getRecommendedParts().get(1).isStockSufficient());
        assertEquals(0, result.getRecommendedParts().get(1).getAvailableStock());
    }

    // ==========================================================================
    // Test 12: Approve Part Request Reserves Stock
    // ==========================================================================

    @Test
    @DisplayName("Approve part request — reserves stock from inventory")
    void approvePartRequest_reservesStock() {
        PartRequest partRequest = new PartRequest();
        partRequest.setId(50L);
        partRequest.setOrderId(1L);
        partRequest.setStatus(PartRequestStatus.PENDING.getCode());
        when(partRequestMapper.selectById(50L)).thenReturn(partRequest);
        when(partRequestMapper.updateById(any())).thenReturn(1);

        RepairOrder order = buildVisitingOrder();
        when(orderMapper.selectById(1L)).thenReturn(order);

        PartRequestItem item = new PartRequestItem();
        item.setId(100L);
        item.setRequestId(50L);
        item.setPartId(1L);
        item.setRequestedQty(2);
        item.setStatus(PartRequestStatus.PENDING.getCode());
        when(partRequestItemMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(item));
        when(partRequestItemMapper.updateById(any())).thenReturn(1);

        SparePartInventory inv = buildInventory(1L, 10);
        when(inventoryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(inv);
        when(inventoryMapper.updateById(any())).thenReturn(1);
        when(partAuditLogMapper.insert(any())).thenReturn(1);

        sparePartService.approvePartRequest(50L, 99L);

        // Verify: stock reserved
        ArgumentCaptor<SparePartInventory> invCaptor = ArgumentCaptor.forClass(SparePartInventory.class);
        verify(inventoryMapper).updateById(invCaptor.capture());
        assertEquals(8, invCaptor.getValue().getAvailableQty()); // 10 - 2
        assertEquals(2, invCaptor.getValue().getReservedQty()); // 0 + 2

        // Verify: request approved
        ArgumentCaptor<PartRequest> prCaptor = ArgumentCaptor.forClass(PartRequest.class);
        verify(partRequestMapper).updateById(prCaptor.capture());
        assertEquals(PartRequestStatus.APPROVED.getCode(), prCaptor.getValue().getStatus());
        assertNotNull(prCaptor.getValue().getApprovedBy());
    }

    // ==========================================================================
    // Test 13: Cannot Request Parts in Invalid Status
    // ==========================================================================

    @Test
    @DisplayName("Cannot request parts when order is in DISPATCHED status")
    void cannotRequestParts_inDispatchedStatus() {
        RepairOrder order = new RepairOrder();
        order.setId(1L);
        order.setStatus(OrderStatus.DISPATCHED.getCode());
        order.setAssignedWorkerId(10L);
        when(orderMapper.selectById(1L)).thenReturn(order);

        PartRequestCreateRequest request = buildPartRequest(1L, 1, true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> sparePartService.createPartRequest(1L, request, 10L));
        assertTrue(ex.getMessage().contains("Cannot request parts"));
    }

    // ==========================================================================
    // Test 14: Only Assigned Worker Can Request Parts
    // ==========================================================================

    @Test
    @DisplayName("Only assigned worker can request parts")
    void onlyAssignedWorker_canRequestParts() {
        RepairOrder order = buildVisitingOrder();
        when(orderMapper.selectById(1L)).thenReturn(order);

        PartRequestCreateRequest request = buildPartRequest(1L, 1, true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> sparePartService.createPartRequest(1L, request, 99L));
        assertTrue(ex.getMessage().contains("not the assigned worker"));
    }

    // ==========================================================================
    // Test 15: Purchase Request Lifecycle — Reject Invalid Transitions
    // ==========================================================================

    @Test
    @DisplayName("Cannot approve already approved purchase request")
    void cannotApproveAlreadyApprovedPurchase() {
        PurchaseRequest purchase = new PurchaseRequest();
        purchase.setId(70L);
        purchase.setStatus(PurchaseRequestStatus.APPROVED.getCode());
        when(purchaseRequestMapper.selectById(70L)).thenReturn(purchase);

        assertThrows(BusinessException.class,
                () -> sparePartService.approvePurchaseRequest(70L, 99L));
    }

    @Test
    @DisplayName("Cannot receive purchase that is not ORDERED")
    void cannotReceiveNonOrderedPurchase() {
        PurchaseRequest purchase = new PurchaseRequest();
        purchase.setId(70L);
        purchase.setStatus(PurchaseRequestStatus.APPROVED.getCode());
        when(purchaseRequestMapper.selectById(70L)).thenReturn(purchase);

        assertThrows(BusinessException.class,
                () -> sparePartService.receivePurchase(70L, 99L));
    }

    // ==========================================================================
    // Test 16: SLA Adjustment — Waiting Duration Correctly Subtracted
    // ==========================================================================

    @Test
    @DisplayName("SLA adjustment — waiting duration correctly subtracted from deadline")
    void slaAdjustment_waitingDurationCorrectlySubtracted() {
        // Order has been waiting 3 hours, visitAt was 5 hours ago
        RepairOrder order = buildWaitingPartsOrder(OrderStatus.VISITING.getCode());
        LocalDateTime originalVisitAt = order.getVisitAt();

        when(orderMapper.selectById(1L)).thenReturn(order);
        when(orderMapper.updateById(any())).thenReturn(1);
        when(orderMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(order));

        // No active pending requests
        when(partRequestMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());
        when(progressMapper.insert(any())).thenReturn(1);

        sparePartService.checkAndResumeWaitingOrders(100L, 200L);

        ArgumentCaptor<RepairOrder> captor = ArgumentCaptor.forClass(RepairOrder.class);
        verify(orderMapper).updateById(captor.capture());
        RepairOrder resumed = captor.getValue();

        // visitAt should be pushed forward by ~3 hours
        assertTrue(resumed.getVisitAt().isAfter(originalVisitAt));
        long adjustedSeconds = Duration.between(originalVisitAt, resumed.getVisitAt()).getSeconds();
        assertTrue(adjustedSeconds >= 10000); // At least ~2.7 hours (3h minus small execution time)

        // totalWaitingPartsSeconds should be > 0
        assertTrue(resumed.getTotalWaitingPartsSeconds() > 0);
    }

    // ==========================================================================
    // Test 17: Multiple Items Request — Mixed Critical/Non-Critical
    // ==========================================================================

    @Test
    @DisplayName("Mixed critical/non-critical items — only critical shortage triggers WAITING_PARTS")
    void mixedCriticalNonCritical_onlyCriticalTriggersWaitingParts() {
        RepairOrder order = buildVisitingOrder();
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(orderMapper.updateById(any())).thenReturn(1);

        SparePart faucet = buildFaucetPart();
        SparePart pipe = buildPipePart();
        when(sparePartMapper.selectById(1L)).thenReturn(faucet);
        when(sparePartMapper.selectById(2L)).thenReturn(pipe);

        // Faucet (critical) has stock, Pipe (non-critical) has no stock
        SparePartInventory faucetInv = buildInventory(1L, 5);
        when(inventoryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(faucetInv);
        // Second call for pipe returns null
        when(inventoryMapper.selectOne(argThat((LambdaQueryWrapper<?> w) -> true)))
                .thenReturn(faucetInv)
                .thenReturn(null);

        when(partRequestMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(partRequestMapper.insert(any())).thenAnswer(inv -> {
            PartRequest pr = inv.getArgument(0);
            pr.setId(55L);
            return 1;
        });
        when(partRequestItemMapper.insert(any())).thenReturn(1);
        when(partRequestItemMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());
        when(partRequestMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());
        when(partAuditLogMapper.insert(any())).thenReturn(1);

        PartRequestCreateRequest req = new PartRequestCreateRequest();
        PartRequestCreateRequest.PartItemRequest criticalItem = new PartRequestCreateRequest.PartItemRequest();
        criticalItem.setPartId(1L);
        criticalItem.setQuantity(1);
        criticalItem.setCritical(true);

        PartRequestCreateRequest.PartItemRequest nonCriticalItem = new PartRequestCreateRequest.PartItemRequest();
        nonCriticalItem.setPartId(2L);
        nonCriticalItem.setQuantity(5);
        nonCriticalItem.setCritical(false);

        req.setItems(List.of(criticalItem, nonCriticalItem));
        req.setRemark("Mixed items");

        sparePartService.createPartRequest(1L, req, 10L);

        // Verify: order NOT in WAITING_PARTS (critical part has sufficient stock)
        verify(orderMapper, never()).updateById(argThat(o ->
                OrderStatus.WAITING_PARTS.getCode().equals(((RepairOrder) o).getStatus())));
    }
}
