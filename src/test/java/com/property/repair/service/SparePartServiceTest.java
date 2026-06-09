package com.property.repair.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.property.repair.dto.PurchaseReceiveRequest;
import com.property.repair.dto.SparePartRequisitionRequest;
import com.property.repair.dto.SparePartReturnRequest;
import com.property.repair.entity.*;
import com.property.repair.enums.OrderStatus;
import com.property.repair.enums.PurchaseStatus;
import com.property.repair.enums.RequisitionStatus;
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

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SparePartServiceTest {

    @Mock private SparePartMapper sparePartMapper;
    @Mock private SparePartInventoryMapper inventoryMapper;
    @Mock private SparePartRequisitionMapper requisitionMapper;
    @Mock private SparePartRequisitionItemMapper requisitionItemMapper;
    @Mock private PurchaseRequestMapper purchaseRequestMapper;
    @Mock private SparePartAuditLogMapper auditLogMapper;
    @Mock private RepairOrderMapper orderMapper;
    @Mock private RepairProgressMapper progressMapper;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;

    private SparePartServiceImpl sparePartService;
    private OrderStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new OrderStateMachine();
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.delete(anyString())).thenReturn(true);
        lenient().when(progressMapper.insert(any())).thenReturn(1);
        lenient().when(auditLogMapper.insert(any())).thenReturn(1);

        sparePartService = new SparePartServiceImpl(
                sparePartMapper, inventoryMapper, requisitionMapper,
                requisitionItemMapper, purchaseRequestMapper, auditLogMapper,
                orderMapper, progressMapper, stateMachine, redisTemplate);
    }

    // ==========================================================================
    // Helper factories
    // ==========================================================================

    private RepairOrder makeOrder(Long id, String status, Long communityId, Long workerId) {
        RepairOrder order = new RepairOrder();
        order.setId(id);
        order.setOrderNo("RO001");
        order.setStatus(status);
        order.setCommunityId(communityId);
        order.setAssignedWorkerId(workerId);
        order.setProblemType("PLUMBING");
        order.setTotalSuspendedSeconds(0);
        return order;
    }

    private SparePart makePart(Long id, String partNo, String name, boolean critical) {
        SparePart part = new SparePart();
        part.setId(id);
        part.setPartNo(partNo);
        part.setName(name);
        part.setProblemType("PLUMBING");
        part.setIsCritical(critical ? 1 : 0);
        part.setUnit("pcs");
        part.setMinStock(5);
        return part;
    }

    private SparePartInventory makeInventory(Long id, Long partId, Long communityId, int quantity) {
        SparePartInventory inv = new SparePartInventory();
        inv.setId(id);
        inv.setPartId(partId);
        inv.setCommunityId(communityId);
        inv.setQuantity(quantity);
        inv.setReservedQuantity(0);
        return inv;
    }

    private SparePartRequisitionRequest makeRequisitionRequest(Long orderId, Long partId, int qty) {
        SparePartRequisitionRequest request = new SparePartRequisitionRequest();
        request.setOrderId(orderId);
        SparePartRequisitionRequest.Item item = new SparePartRequisitionRequest.Item();
        item.setPartId(partId);
        item.setQuantity(qty);
        request.setItems(List.of(item));
        return request;
    }

    // ==========================================================================
    // Test: Sufficient stock — immediate issue
    // ==========================================================================

    @Test
    @DisplayName("Requisition with sufficient stock should issue immediately")
    void requisition_sufficientStock_issuesImmediately() {
        RepairOrder order = makeOrder(1L, OrderStatus.ACCEPTED.getCode(), 1L, 4L);
        SparePart part = makePart(10L, "SP-PLB-001", "Faucet Cartridge", true);
        SparePartInventory inventory = makeInventory(100L, 10L, 1L, 20);

        when(orderMapper.selectById(1L)).thenReturn(order);
        when(requisitionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        when(requisitionMapper.insert(any())).thenAnswer(inv -> {
            SparePartRequisition r = inv.getArgument(0);
            r.setId(50L);
            return 1;
        });
        when(sparePartMapper.selectById(10L)).thenReturn(part);
        when(inventoryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(inventory);
        when(requisitionItemMapper.insert(any())).thenReturn(1);
        when(inventoryMapper.updateById(any())).thenReturn(1);

        // After insert, return items with issued qty set
        SparePartRequisitionItem issuedItem = new SparePartRequisitionItem();
        issuedItem.setRequisitionId(50L);
        issuedItem.setPartId(10L);
        issuedItem.setRequestedQuantity(3);
        issuedItem.setIssuedQuantity(3);
        when(requisitionItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(issuedItem));
        when(requisitionMapper.updateById(any())).thenReturn(1);

        SparePartRequisitionRequest request = makeRequisitionRequest(1L, 10L, 3);
        sparePartService.submitRequisition(request, 4L);

        // Verify inventory was deducted
        ArgumentCaptor<SparePartInventory> invCaptor = ArgumentCaptor.forClass(SparePartInventory.class);
        verify(inventoryMapper).updateById(invCaptor.capture());
        assertEquals(17, invCaptor.getValue().getQuantity()); // 20 - 3

        // Verify requisition status set to ISSUED
        ArgumentCaptor<SparePartRequisition> reqCaptor = ArgumentCaptor.forClass(SparePartRequisition.class);
        verify(requisitionMapper).updateById(reqCaptor.capture());
        assertEquals(RequisitionStatus.ISSUED.getCode(), reqCaptor.getValue().getStatus());

        // Verify order status NOT changed (still ACCEPTED)
        verify(orderMapper, never()).updateById(any());
    }

    // ==========================================================================
    // Test: Critical part shortage — enters WAITING_PARTS
    // ==========================================================================

    @Test
    @DisplayName("Critical part shortage should transition order to WAITING_PARTS")
    void requisition_criticalPartShortage_entersWaitingParts() {
        RepairOrder order = makeOrder(1L, OrderStatus.VISITING.getCode(), 1L, 4L);
        order.setVisitAt(LocalDateTime.now().minusHours(1));
        SparePart criticalPart = makePart(10L, "SP-PLB-001", "Faucet Cartridge", true);
        SparePartInventory inventory = makeInventory(100L, 10L, 1L, 0); // NO STOCK

        when(orderMapper.selectById(1L)).thenReturn(order);
        when(requisitionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        when(requisitionMapper.insert(any())).thenAnswer(inv -> {
            SparePartRequisition r = inv.getArgument(0);
            r.setId(50L);
            return 1;
        });
        when(sparePartMapper.selectById(10L)).thenReturn(criticalPart);
        when(inventoryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(inventory);
        when(requisitionItemMapper.insert(any())).thenReturn(1);
        when(purchaseRequestMapper.insert(any())).thenReturn(1);
        when(orderMapper.updateById(any())).thenReturn(1);
        when(requisitionMapper.updateById(any())).thenReturn(1);

        SparePartRequisitionRequest request = makeRequisitionRequest(1L, 10L, 5);
        sparePartService.submitRequisition(request, 4L);

        // Verify order transitioned to WAITING_PARTS
        ArgumentCaptor<RepairOrder> orderCaptor = ArgumentCaptor.forClass(RepairOrder.class);
        verify(orderMapper).updateById(orderCaptor.capture());
        RepairOrder updated = orderCaptor.getValue();
        assertEquals(OrderStatus.WAITING_PARTS.getCode(), updated.getStatus());
        assertEquals(OrderStatus.VISITING.getCode(), updated.getPreviousStatus());
        assertNotNull(updated.getSuspendedAt());
        assertEquals("Waiting for critical spare parts", updated.getSuspendReason());

        // Verify purchase request was created
        verify(purchaseRequestMapper).insert(any(PurchaseRequest.class));

        // Verify Redis timeout keys cleared (6 keys)
        verify(redisTemplate, atLeast(6)).delete(anyString());
    }

    // ==========================================================================
    // Test: Non-critical shortage — order not affected
    // ==========================================================================

    @Test
    @DisplayName("Non-critical part shortage should not affect order status")
    void requisition_nonCriticalShortage_orderNotAffected() {
        RepairOrder order = makeOrder(1L, OrderStatus.ACCEPTED.getCode(), 1L, 4L);
        SparePart nonCriticalPart = makePart(10L, "SP-PLB-003", "Pipe Sealant", false);
        SparePartInventory inventory = makeInventory(100L, 10L, 1L, 0); // NO STOCK

        when(orderMapper.selectById(1L)).thenReturn(order);
        when(requisitionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        when(requisitionMapper.insert(any())).thenAnswer(inv -> {
            SparePartRequisition r = inv.getArgument(0);
            r.setId(50L);
            return 1;
        });
        when(sparePartMapper.selectById(10L)).thenReturn(nonCriticalPart);
        when(inventoryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(inventory);
        when(requisitionItemMapper.insert(any())).thenReturn(1);
        when(purchaseRequestMapper.insert(any())).thenReturn(1);

        SparePartRequisitionItem pendingItem = new SparePartRequisitionItem();
        pendingItem.setRequisitionId(50L);
        pendingItem.setPartId(10L);
        pendingItem.setRequestedQuantity(5);
        pendingItem.setIssuedQuantity(0);
        when(requisitionItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(pendingItem));
        when(requisitionMapper.updateById(any())).thenReturn(1);

        SparePartRequisitionRequest request = makeRequisitionRequest(1L, 10L, 5);
        sparePartService.submitRequisition(request, 4L);

        // Verify order status NOT changed
        verify(orderMapper, never()).updateById(any());

        // Verify purchase request was still created
        verify(purchaseRequestMapper).insert(any(PurchaseRequest.class));

        // Verify requisition stays PENDING
        ArgumentCaptor<SparePartRequisition> reqCaptor = ArgumentCaptor.forClass(SparePartRequisition.class);
        verify(requisitionMapper).updateById(reqCaptor.capture());
        assertEquals(RequisitionStatus.PENDING.getCode(), reqCaptor.getValue().getStatus());
    }

    // ==========================================================================
    // Test: Purchase receive — resumes WAITING_PARTS order
    // ==========================================================================

    @Test
    @DisplayName("Purchase receive should resume WAITING_PARTS order when parts fulfilled")
    void purchaseReceive_resumesWaitingOrder() {
        PurchaseRequest purchase = new PurchaseRequest();
        purchase.setId(200L);
        purchase.setRequestNo("PUR001");
        purchase.setPartId(10L);
        purchase.setCommunityId(1L);
        purchase.setQuantity(10);
        purchase.setStatus(PurchaseStatus.ORDERED.getCode());
        purchase.setTriggerRequisitionId(50L);
        purchase.setTriggerOrderId(1L);

        SparePartInventory inventory = makeInventory(100L, 10L, 1L, 0);

        when(purchaseRequestMapper.selectById(200L)).thenReturn(purchase);
        when(purchaseRequestMapper.updateById(any())).thenReturn(1);
        when(inventoryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(inventory);
        when(inventoryMapper.updateById(any())).thenReturn(1);

        // Pending requisition item that needs fulfillment
        SparePartRequisitionItem pendingItem = new SparePartRequisitionItem();
        pendingItem.setId(300L);
        pendingItem.setRequisitionId(50L);
        pendingItem.setPartId(10L);
        pendingItem.setRequestedQuantity(5);
        pendingItem.setIssuedQuantity(0);
        pendingItem.setConsumedQuantity(0);
        pendingItem.setReturnedQuantity(0);
        when(requisitionItemMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(pendingItem))  // for tryFulfill
                .thenReturn(List.of(pendingItem));  // for canResumeOrder
        when(requisitionItemMapper.updateById(any())).thenReturn(1);

        SparePartRequisition pendingReq = new SparePartRequisition();
        pendingReq.setId(50L);
        pendingReq.setOrderId(1L);
        pendingReq.setStatus(RequisitionStatus.PENDING.getCode());
        when(requisitionMapper.selectById(50L)).thenReturn(pendingReq);

        // WAITING_PARTS order
        RepairOrder waitingOrder = makeOrder(1L, OrderStatus.WAITING_PARTS.getCode(), 1L, 4L);
        waitingOrder.setPreviousStatus(OrderStatus.VISITING.getCode());
        waitingOrder.setSuspendedAt(LocalDateTime.now().minusHours(2));
        waitingOrder.setVisitAt(LocalDateTime.now().minusHours(5));
        when(orderMapper.selectById(1L)).thenReturn(waitingOrder);
        when(orderMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(waitingOrder));
        when(orderMapper.updateById(any())).thenReturn(1);

        when(requisitionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(pendingReq));
        when(requisitionMapper.updateById(any())).thenReturn(1);

        // sparePartMapper.selectById is only called inside canResumeOrder when
        // issuedQuantity < requestedQuantity. After fulfillment the item is already
        // fully issued, so the call is skipped — mark lenient.
        SparePart part = makePart(10L, "SP-PLB-001", "Faucet", true);
        lenient().when(sparePartMapper.selectById(10L)).thenReturn(part);

        PurchaseReceiveRequest request = new PurchaseReceiveRequest();
        request.setReceivedQuantity(10);

        sparePartService.receivePurchase(200L, request, 1L);

        // Verify: purchase marked as RECEIVED
        ArgumentCaptor<PurchaseRequest> purchaseCaptor = ArgumentCaptor.forClass(PurchaseRequest.class);
        verify(purchaseRequestMapper).updateById(purchaseCaptor.capture());
        assertEquals(PurchaseStatus.RECEIVED.getCode(), purchaseCaptor.getValue().getStatus());

        // Verify: order resumed from WAITING_PARTS
        ArgumentCaptor<RepairOrder> orderCaptor = ArgumentCaptor.forClass(RepairOrder.class);
        verify(orderMapper).updateById(orderCaptor.capture());
        RepairOrder resumed = orderCaptor.getValue();
        assertEquals(OrderStatus.VISITING.getCode(), resumed.getStatus());
        assertNull(resumed.getSuspendedAt());
        assertNull(resumed.getPreviousStatus());
        assertTrue(resumed.getTotalSuspendedSeconds() > 0);
    }

    // ==========================================================================
    // Test: Partial fulfill — order stays WAITING_PARTS
    // ==========================================================================

    @Test
    @DisplayName("Partial purchase receive should keep order in WAITING_PARTS")
    void purchaseReceive_partialFulfill_orderStillWaiting() {
        PurchaseRequest purchase = new PurchaseRequest();
        purchase.setId(200L);
        purchase.setRequestNo("PUR001");
        purchase.setPartId(10L);
        purchase.setCommunityId(1L);
        purchase.setQuantity(10);
        purchase.setStatus(PurchaseStatus.ORDERED.getCode());
        purchase.setTriggerRequisitionId(50L);
        purchase.setTriggerOrderId(1L);

        SparePartInventory inventory = makeInventory(100L, 10L, 1L, 0);

        when(purchaseRequestMapper.selectById(200L)).thenReturn(purchase);
        when(purchaseRequestMapper.updateById(any())).thenReturn(1);
        when(inventoryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(inventory);
        when(inventoryMapper.updateById(any())).thenReturn(1);

        // Pending item needs 10, but only 2 arrived (inventory after receive = 2, deficit still 8)
        SparePartRequisitionItem pendingItem = new SparePartRequisitionItem();
        pendingItem.setId(300L);
        pendingItem.setRequisitionId(50L);
        pendingItem.setPartId(10L);
        pendingItem.setRequestedQuantity(10);
        pendingItem.setIssuedQuantity(0);
        pendingItem.setConsumedQuantity(0);
        pendingItem.setReturnedQuantity(0);
        when(requisitionItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(pendingItem));

        SparePartRequisition pendingReq = new SparePartRequisition();
        pendingReq.setId(50L);
        pendingReq.setOrderId(1L);
        pendingReq.setStatus(RequisitionStatus.PENDING.getCode());
        when(requisitionMapper.selectById(50L)).thenReturn(pendingReq);

        RepairOrder waitingOrder = makeOrder(1L, OrderStatus.WAITING_PARTS.getCode(), 1L, 4L);
        waitingOrder.setPreviousStatus(OrderStatus.VISITING.getCode());
        waitingOrder.setSuspendedAt(LocalDateTime.now().minusHours(1));
        when(orderMapper.selectById(1L)).thenReturn(waitingOrder);
        when(orderMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(waitingOrder));

        when(requisitionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(pendingReq));

        SparePart part = makePart(10L, "SP-PLB-001", "Faucet", true);
        when(sparePartMapper.selectById(10L)).thenReturn(part);

        PurchaseReceiveRequest request = new PurchaseReceiveRequest();
        request.setReceivedQuantity(2); // Only 2 arrived, need 10

        sparePartService.receivePurchase(200L, request, 1L);

        // Verify: order NOT resumed (still WAITING_PARTS)
        // orderMapper.updateById should not be called for the order (only for inventory via inventoryMapper)
        verify(orderMapper, never()).updateById(any());
    }

    // ==========================================================================
    // Test: Duplicate requisition rejected
    // ==========================================================================

    @Test
    @DisplayName("Duplicate requisition for same order and part should be rejected")
    void duplicateRequisition_rejected() {
        RepairOrder order = makeOrder(1L, OrderStatus.ACCEPTED.getCode(), 1L, 4L);
        when(orderMapper.selectById(1L)).thenReturn(order);

        // Existing active requisition for same order
        SparePartRequisition existingReq = new SparePartRequisition();
        existingReq.setId(50L);
        existingReq.setOrderId(1L);
        existingReq.setStatus(RequisitionStatus.ISSUED.getCode());
        existingReq.setReworkOrderId(null);
        when(requisitionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(existingReq));

        // Existing item for same part
        when(requisitionItemMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        SparePart part = makePart(10L, "SP-PLB-001", "Faucet", true);
        when(sparePartMapper.selectById(10L)).thenReturn(part);

        SparePartRequisitionRequest request = makeRequisitionRequest(1L, 10L, 3);

        assertThrows(BusinessException.class, () ->
                sparePartService.submitRequisition(request, 4L));
    }

    // ==========================================================================
    // Test: Rework second requisition allowed
    // ==========================================================================

    @Test
    @DisplayName("Second requisition during rework should be allowed with different reworkOrderId")
    void reworkSecondRequisition_allowed() {
        RepairOrder order = makeOrder(1L, OrderStatus.REWORKING.getCode(), 1L, 4L);
        SparePart part = makePart(10L, "SP-PLB-001", "Faucet", true);
        SparePartInventory inventory = makeInventory(100L, 10L, 1L, 20);

        when(orderMapper.selectById(1L)).thenReturn(order);

        // Existing requisition from first repair (no reworkOrderId)
        SparePartRequisition firstReq = new SparePartRequisition();
        firstReq.setId(50L);
        firstReq.setOrderId(1L);
        firstReq.setStatus(RequisitionStatus.COMPLETED.getCode()); // completed first time
        firstReq.setReworkOrderId(null);
        // Return empty because COMPLETED is not in the active status filter
        when(requisitionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        when(requisitionMapper.insert(any())).thenAnswer(inv -> {
            SparePartRequisition r = inv.getArgument(0);
            r.setId(60L);
            return 1;
        });
        when(sparePartMapper.selectById(10L)).thenReturn(part);
        when(inventoryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(inventory);
        when(requisitionItemMapper.insert(any())).thenReturn(1);
        when(inventoryMapper.updateById(any())).thenReturn(1);

        SparePartRequisitionItem issuedItem = new SparePartRequisitionItem();
        issuedItem.setRequisitionId(60L);
        issuedItem.setPartId(10L);
        issuedItem.setRequestedQuantity(2);
        issuedItem.setIssuedQuantity(2);
        when(requisitionItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(issuedItem));
        when(requisitionMapper.updateById(any())).thenReturn(1);

        SparePartRequisitionRequest request = makeRequisitionRequest(1L, 10L, 2);
        request.setReworkOrderId(99L); // rework requisition

        // Should NOT throw
        assertDoesNotThrow(() -> sparePartService.submitRequisition(request, 4L));

        // Verify requisition was created with reworkOrderId
        ArgumentCaptor<SparePartRequisition> reqCaptor = ArgumentCaptor.forClass(SparePartRequisition.class);
        verify(requisitionMapper).insert(reqCaptor.capture());
        assertEquals(99L, reqCaptor.getValue().getReworkOrderId());
    }

    // ==========================================================================
    // Test: Return parts restores inventory
    // ==========================================================================

    @Test
    @DisplayName("Returning parts should restore inventory quantity")
    void returnParts_restoresInventory() {
        SparePartRequisition requisition = new SparePartRequisition();
        requisition.setId(50L);
        requisition.setOrderId(1L);
        requisition.setWorkerId(4L);
        requisition.setStatus(RequisitionStatus.ISSUED.getCode());
        when(requisitionMapper.selectById(50L)).thenReturn(requisition);

        RepairOrder order = makeOrder(1L, OrderStatus.VISITING.getCode(), 1L, 4L);
        when(orderMapper.selectById(1L)).thenReturn(order);

        SparePartRequisitionItem item = new SparePartRequisitionItem();
        item.setId(300L);
        item.setRequisitionId(50L);
        item.setPartId(10L);
        item.setRequestedQuantity(5);
        item.setIssuedQuantity(5);
        item.setConsumedQuantity(0);
        item.setReturnedQuantity(0);
        when(requisitionItemMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(item);
        when(requisitionItemMapper.updateById(any())).thenReturn(1);

        SparePartInventory inventory = makeInventory(100L, 10L, 1L, 15);
        when(inventoryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(inventory);
        when(inventoryMapper.updateById(any())).thenReturn(1);

        SparePartReturnRequest request = new SparePartReturnRequest();
        SparePartReturnRequest.Item returnItem = new SparePartReturnRequest.Item();
        returnItem.setPartId(10L);
        returnItem.setQuantity(3);
        request.setItems(List.of(returnItem));

        sparePartService.returnParts(50L, request, 4L);

        // Verify inventory restored
        ArgumentCaptor<SparePartInventory> invCaptor = ArgumentCaptor.forClass(SparePartInventory.class);
        verify(inventoryMapper).updateById(invCaptor.capture());
        assertEquals(18, invCaptor.getValue().getQuantity()); // 15 + 3

        // Verify return quantity updated
        ArgumentCaptor<SparePartRequisitionItem> itemCaptor = ArgumentCaptor.forClass(SparePartRequisitionItem.class);
        verify(requisitionItemMapper).updateById(itemCaptor.capture());
        assertEquals(3, itemCaptor.getValue().getReturnedQuantity());
    }

    // ==========================================================================
    // Test: Complete order consumes parts
    // ==========================================================================

    @Test
    @DisplayName("Consuming parts should mark issued items as consumed")
    void completeOrder_consumesParts() {
        SparePartRequisition req = new SparePartRequisition();
        req.setId(50L);
        req.setOrderId(1L);
        req.setWorkerId(4L);
        req.setStatus(RequisitionStatus.ISSUED.getCode());
        when(requisitionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(req));

        SparePartRequisitionItem item = new SparePartRequisitionItem();
        item.setId(300L);
        item.setRequisitionId(50L);
        item.setPartId(10L);
        item.setRequestedQuantity(5);
        item.setIssuedQuantity(5);
        item.setConsumedQuantity(0);
        item.setReturnedQuantity(2); // returned 2, so consumed should be 3
        when(requisitionItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(item));
        when(requisitionItemMapper.updateById(any())).thenReturn(1);
        when(requisitionMapper.updateById(any())).thenReturn(1);

        sparePartService.consumePartsForOrder(1L);

        // Verify consumed = issued - returned = 5 - 2 = 3
        ArgumentCaptor<SparePartRequisitionItem> itemCaptor = ArgumentCaptor.forClass(SparePartRequisitionItem.class);
        verify(requisitionItemMapper).updateById(itemCaptor.capture());
        assertEquals(3, itemCaptor.getValue().getConsumedQuantity());

        // Verify requisition status -> COMPLETED
        ArgumentCaptor<SparePartRequisition> reqCaptor = ArgumentCaptor.forClass(SparePartRequisition.class);
        verify(requisitionMapper).updateById(reqCaptor.capture());
        assertEquals(RequisitionStatus.COMPLETED.getCode(), reqCaptor.getValue().getStatus());
    }

    // ==========================================================================
    // Test: Recommend parts matches problem type and history
    // ==========================================================================

    @Test
    @DisplayName("Recommend parts should return parts matching problem type sorted by history")
    void recommendParts_matchesProblemTypeAndHistory() {
        RepairOrder order = makeOrder(1L, OrderStatus.ACCEPTED.getCode(), 1L, 4L);
        when(orderMapper.selectById(1L)).thenReturn(order);

        SparePart part1 = makePart(10L, "SP-PLB-001", "Faucet Cartridge", true);
        SparePart part2 = makePart(11L, "SP-PLB-002", "PVC Pipe", true);
        when(sparePartMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(part1, part2));

        // part2 has more historical consumption
        when(sparePartMapper.countHistoricalConsumption("PLUMBING", 1L))
                .thenReturn(List.of(
                        Map.of("part_id", 11L, "total_consumed", 15),
                        Map.of("part_id", 10L, "total_consumed", 5)
                ));

        SparePartInventory inv1 = makeInventory(100L, 10L, 1L, 10);
        SparePartInventory inv2 = makeInventory(101L, 11L, 1L, 0); // out of stock
        when(inventoryMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(inv1, inv2));

        var result = sparePartService.recommendParts(1L);

        assertEquals(2, result.size());
        // Both are critical, so sorted by historical consumption desc
        assertEquals("PVC Pipe", result.get(0).getName());
        assertEquals(15, result.get(0).getHistoricalConsumption());
        assertFalse(result.get(0).getStockSufficient()); // out of stock

        assertEquals("Faucet Cartridge", result.get(1).getName());
        assertEquals(5, result.get(1).getHistoricalConsumption());
        assertTrue(result.get(1).getStockSufficient());
    }

    // ==========================================================================
    // Test: WAITING_PARTS SLA timer paused
    // ==========================================================================

    @Test
    @DisplayName("Entering WAITING_PARTS should pause SLA and clear Redis timeout keys")
    void waitingParts_slaTimerPaused() {
        RepairOrder order = makeOrder(1L, OrderStatus.ACCEPTED.getCode(), 1L, 4L);
        order.setAcceptedAt(LocalDateTime.now().minusHours(1));
        SparePart criticalPart = makePart(10L, "SP-PLB-001", "Faucet", true);
        SparePartInventory inventory = makeInventory(100L, 10L, 1L, 0);

        when(orderMapper.selectById(1L)).thenReturn(order);
        when(requisitionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        when(requisitionMapper.insert(any())).thenAnswer(inv -> {
            SparePartRequisition r = inv.getArgument(0);
            r.setId(50L);
            return 1;
        });
        when(sparePartMapper.selectById(10L)).thenReturn(criticalPart);
        when(inventoryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(inventory);
        when(requisitionItemMapper.insert(any())).thenReturn(1);
        when(purchaseRequestMapper.insert(any())).thenReturn(1);
        when(orderMapper.updateById(any())).thenReturn(1);
        when(requisitionMapper.updateById(any())).thenReturn(1);

        SparePartRequisitionRequest request = makeRequisitionRequest(1L, 10L, 5);
        sparePartService.submitRequisition(request, 4L);

        // Verify suspendedAt is set
        ArgumentCaptor<RepairOrder> orderCaptor = ArgumentCaptor.forClass(RepairOrder.class);
        verify(orderMapper).updateById(orderCaptor.capture());
        assertNotNull(orderCaptor.getValue().getSuspendedAt());

        // Verify all 6 Redis timeout keys cleared
        verify(redisTemplate, times(6)).delete(anyString());
    }

    // ==========================================================================
    // Test: Resume from WAITING_PARTS adjusts SLA
    // ==========================================================================

    @Test
    @DisplayName("Resuming from WAITING_PARTS should adjust SLA timestamps")
    void waitingParts_resumeAdjustsSla() {
        PurchaseRequest purchase = new PurchaseRequest();
        purchase.setId(200L);
        purchase.setRequestNo("PUR001");
        purchase.setPartId(10L);
        purchase.setCommunityId(1L);
        purchase.setQuantity(10);
        purchase.setStatus(PurchaseStatus.ORDERED.getCode());
        purchase.setTriggerRequisitionId(50L);
        purchase.setTriggerOrderId(1L);

        SparePartInventory inventory = makeInventory(100L, 10L, 1L, 0);

        when(purchaseRequestMapper.selectById(200L)).thenReturn(purchase);
        when(purchaseRequestMapper.updateById(any())).thenReturn(1);
        when(inventoryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(inventory);
        when(inventoryMapper.updateById(any())).thenReturn(1);

        SparePartRequisitionItem pendingItem = new SparePartRequisitionItem();
        pendingItem.setId(300L);
        pendingItem.setRequisitionId(50L);
        pendingItem.setPartId(10L);
        pendingItem.setRequestedQuantity(5);
        pendingItem.setIssuedQuantity(0);
        pendingItem.setConsumedQuantity(0);
        pendingItem.setReturnedQuantity(0);
        when(requisitionItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(pendingItem));
        when(requisitionItemMapper.updateById(any())).thenReturn(1);

        SparePartRequisition pendingReq = new SparePartRequisition();
        pendingReq.setId(50L);
        pendingReq.setOrderId(1L);
        pendingReq.setStatus(RequisitionStatus.PENDING.getCode());
        when(requisitionMapper.selectById(50L)).thenReturn(pendingReq);

        // Order was in ACCEPTED before WAITING_PARTS, suspended 1 hour ago
        LocalDateTime acceptedAt = LocalDateTime.now().minusHours(3);
        RepairOrder waitingOrder = makeOrder(1L, OrderStatus.WAITING_PARTS.getCode(), 1L, 4L);
        waitingOrder.setPreviousStatus(OrderStatus.ACCEPTED.getCode());
        waitingOrder.setSuspendedAt(LocalDateTime.now().minusHours(1));
        waitingOrder.setAcceptedAt(acceptedAt);
        when(orderMapper.selectById(1L)).thenReturn(waitingOrder);
        when(orderMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(waitingOrder));
        when(orderMapper.updateById(any())).thenReturn(1);

        when(requisitionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(pendingReq));
        when(requisitionMapper.updateById(any())).thenReturn(1);

        SparePart part = makePart(10L, "SP-PLB-001", "Faucet", true);
        lenient().when(sparePartMapper.selectById(10L)).thenReturn(part);

        PurchaseReceiveRequest request = new PurchaseReceiveRequest();
        request.setReceivedQuantity(10);

        sparePartService.receivePurchase(200L, request, 1L);

        // Verify SLA adjusted
        ArgumentCaptor<RepairOrder> orderCaptor = ArgumentCaptor.forClass(RepairOrder.class);
        verify(orderMapper).updateById(orderCaptor.capture());
        RepairOrder resumed = orderCaptor.getValue();

        // acceptedAt should be pushed forward by ~1 hour
        assertTrue(resumed.getAcceptedAt().isAfter(acceptedAt));
        assertTrue(resumed.getTotalSuspendedSeconds() > 0);

        // Verify Redis timeout key re-established for visit timeout
        verify(valueOperations, atLeastOnce()).set(
                eq("timeout:visit:1"), anyString(), any(java.time.Duration.class));
    }
}
