package com.property.repair.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.property.repair.dto.*;
import com.property.repair.entity.*;
import com.property.repair.enums.*;
import com.property.repair.exception.BusinessException;
import com.property.repair.mapper.*;
import com.property.repair.service.AuditService;
import com.property.repair.service.SparePartService;
import com.property.repair.statemachine.OrderStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SparePartServiceImpl implements SparePartService {

    private final SparePartMapper sparePartMapper;
    private final SparePartInventoryMapper inventoryMapper;
    private final PartRequestMapper partRequestMapper;
    private final PartRequestItemMapper partRequestItemMapper;
    private final PurchaseRequestMapper purchaseRequestMapper;
    private final PartAuditLogMapper partAuditLogMapper;
    private final RepairOrderMapper orderMapper;
    private final RepairProgressMapper progressMapper;
    private final UserMapper userMapper;
    private final AuditService auditService;
    private final OrderStateMachine stateMachine;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final AtomicLong SEQ = new AtomicLong(0);

    // ==========================================================================
    // Part Recommendation
    // ==========================================================================

    @Override
    public PartRecommendationVO recommendParts(Long orderId) {
        RepairOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("Order not found");
        }

        PartRecommendationVO vo = new PartRecommendationVO();
        vo.setOrderId(orderId);
        vo.setProblemType(order.getProblemType());
        vo.setBuildingId(order.getBuildingId());

        // 1. Find historically used parts for this problem type + building
        List<SparePart> historicalParts;
        if (order.getBuildingId() != null) {
            historicalParts = sparePartMapper.findHistoricalParts(
                    order.getProblemType(), order.getBuildingId());
        } else {
            historicalParts = sparePartMapper.findHistoricalPartsByProblemType(
                    order.getProblemType());
        }

        // 2. Build recommendation list with stock info
        List<PartRecommendationVO.RecommendedPart> recommendations = new ArrayList<>();
        for (SparePart part : historicalParts) {
            PartRecommendationVO.RecommendedPart rec = new PartRecommendationVO.RecommendedPart();
            rec.setPartId(part.getId());
            rec.setPartCode(part.getPartCode());
            rec.setPartName(part.getPartName());
            rec.setSpecification(part.getSpecification());
            rec.setUnit(part.getUnit());

            // Get available stock at the location
            int availableStock = getAvailableStock(part.getId(),
                    order.getCommunityId(), order.getBuildingId());
            rec.setAvailableStock(availableStock);

            // Default recommendation: 1 unit (historical usage could refine this)
            rec.setHistoricalUsage(1);
            rec.setRecommendedQty(1);
            rec.setStockSufficient(availableStock >= 1);

            recommendations.add(rec);
        }

        vo.setRecommendedParts(recommendations);
        return vo;
    }

    // ==========================================================================
    // Part Request Lifecycle
    // ==========================================================================

    @Override
    @Transactional
    public PartRequestVO createPartRequest(Long orderId, PartRequestCreateRequest request, Long workerId) {
        RepairOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("Order not found");
        }

        // Only assigned worker can request parts
        if (!workerId.equals(order.getAssignedWorkerId())) {
            throw new BusinessException(403, "You are not the assigned worker for this order");
        }

        // Order must be in an active state that allows part requests
        String status = order.getStatus();
        if (!OrderStatus.ACCEPTED.getCode().equals(status)
                && !OrderStatus.VISITING.getCode().equals(status)
                && !OrderStatus.REWORKING.getCode().equals(status)
                && !OrderStatus.WAITING_PARTS.getCode().equals(status)) {
            throw new BusinessException("Cannot request parts in status: " + status);
        }

        // Check for duplicate active request (prevent duplicate requests for same order)
        long activeRequestCount = partRequestMapper.selectCount(
                new LambdaQueryWrapper<PartRequest>()
                        .eq(PartRequest::getOrderId, orderId)
                        .eq(PartRequest::getWorkerId, workerId)
                        .in(PartRequest::getStatus,
                                PartRequestStatus.PENDING.getCode(),
                                PartRequestStatus.APPROVED.getCode(),
                                PartRequestStatus.PARTIALLY_ISSUED.getCode()));
        if (activeRequestCount > 0) {
            throw new BusinessException("Duplicate request: an active part request already exists for this order. " +
                    "Please wait for the current request to be completed or cancelled.");
        }

        // Determine request type
        String requestType = OrderStatus.REWORKING.getCode().equals(status)
                ? PartRequestType.REWORK.getCode()
                : PartRequestType.NORMAL.getCode();

        // Create part request
        PartRequest partRequest = new PartRequest();
        partRequest.setOrderId(orderId);
        partRequest.setWorkerId(workerId);
        partRequest.setRequestNo(generatePartRequestNo());
        partRequest.setRequestType(requestType);
        partRequest.setStatus(PartRequestStatus.PENDING.getCode());
        partRequest.setRemark(request.getRemark());
        partRequestMapper.insert(partRequest);

        // Create request items
        boolean hasCriticalShortage = false;
        for (PartRequestCreateRequest.PartItemRequest itemReq : request.getItems()) {
            SparePart part = sparePartMapper.selectById(itemReq.getPartId());
            if (part == null) {
                throw new BusinessException("Part not found: " + itemReq.getPartId());
            }

            PartRequestItem item = new PartRequestItem();
            item.setRequestId(partRequest.getId());
            item.setPartId(itemReq.getPartId());
            item.setRequestedQty(itemReq.getQuantity());
            item.setIssuedQty(0);
            item.setReturnedQty(0);
            item.setConsumedQty(0);
            item.setStatus(PartRequestStatus.PENDING.getCode());
            item.setCritical(itemReq.isCritical() ? 1 : 0);
            partRequestItemMapper.insert(item);

            // Check stock availability for critical items
            if (itemReq.isCritical()) {
                int available = getAvailableStock(part.getId(),
                        order.getCommunityId(), order.getBuildingId());
                if (available < itemReq.getQuantity()) {
                    hasCriticalShortage = true;
                }
            }
        }

        // If critical parts are insufficient → enter WAITING_PARTS and pause SLA
        // Skip if already in WAITING_PARTS (idempotent — do not overwrite previousStatus)
        if (hasCriticalShortage && !OrderStatus.WAITING_PARTS.getCode().equals(order.getStatus())) {
            enterWaitingPartsStatus(order, workerId);
        }

        // Audit log
        partAuditLog("PART_REQUEST", partRequest.getId(), orderId,
                "CREATE", workerId, UserRole.WORKER.getCode(),
                null, Map.of("requestNo", partRequest.getRequestNo(),
                        "itemCount", request.getItems().size(),
                        "criticalShortage", hasCriticalShortage),
                "Part request created");

        auditService.log(orderId, "CREATE_PART_REQUEST", workerId, UserRole.WORKER.getCode(),
                null, Map.of("requestNo", partRequest.getRequestNo(),
                        "hasCriticalShortage", hasCriticalShortage), null);

        return getPartRequestDetail(partRequest.getId());
    }

    @Override
    public PartRequestVO getPartRequestDetail(Long requestId) {
        PartRequest request = partRequestMapper.selectById(requestId);
        if (request == null) {
            throw new BusinessException("Part request not found");
        }
        return buildPartRequestVO(request);
    }

    @Override
    public List<PartRequestVO> listPartRequests(Long orderId) {
        List<PartRequest> requests = partRequestMapper.selectList(
                new LambdaQueryWrapper<PartRequest>()
                        .eq(PartRequest::getOrderId, orderId)
                        .orderByDesc(PartRequest::getCreatedAt));
        return requests.stream().map(this::buildPartRequestVO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void approvePartRequest(Long requestId, Long approverId) {
        PartRequest request = partRequestMapper.selectById(requestId);
        if (request == null) {
            throw new BusinessException("Part request not found");
        }
        if (!PartRequestStatus.PENDING.getCode().equals(request.getStatus())) {
            throw new BusinessException("Only PENDING requests can be approved");
        }

        RepairOrder order = orderMapper.selectById(request.getOrderId());

        // Reserve stock for each item
        List<PartRequestItem> items = partRequestItemMapper.selectList(
                new LambdaQueryWrapper<PartRequestItem>()
                        .eq(PartRequestItem::getRequestId, requestId));

        for (PartRequestItem item : items) {
            SparePartInventory inventory = findInventory(item.getPartId(),
                    order.getCommunityId(), order.getBuildingId());
            if (inventory != null) {
                // Reserve stock (move from available to reserved)
                int canReserve = Math.min(item.getRequestedQty(), inventory.getAvailableQty());
                if (canReserve < 0) {
                    canReserve = 0;
                }
                if (canReserve > 0) {
                    inventory.setAvailableQty(inventory.getAvailableQty() - canReserve);
                    inventory.setReservedQty(inventory.getReservedQty() + canReserve);
                    inventoryMapper.updateById(inventory);
                }
            }
            item.setStatus(PartRequestStatus.APPROVED.getCode());
            partRequestItemMapper.updateById(item);
        }

        request.setStatus(PartRequestStatus.APPROVED.getCode());
        request.setApprovedBy(approverId);
        request.setApprovedAt(LocalDateTime.now());
        partRequestMapper.updateById(request);

        partAuditLog("PART_REQUEST", requestId, request.getOrderId(),
                "APPROVE", approverId, UserRole.SUPERVISOR.getCode(),
                Map.of("status", PartRequestStatus.PENDING.getCode()),
                Map.of("status", PartRequestStatus.APPROVED.getCode()),
                "Part request approved");
    }

    @Override
    @Transactional
    public void issueParts(Long requestId, IssuePartsRequest request, Long issuerId) {
        PartRequest partRequest = partRequestMapper.selectById(requestId);
        if (partRequest == null) {
            throw new BusinessException("Part request not found");
        }
        if (!PartRequestStatus.APPROVED.getCode().equals(partRequest.getStatus())
                && !PartRequestStatus.PARTIALLY_ISSUED.getCode().equals(partRequest.getStatus())) {
            throw new BusinessException("Request must be APPROVED or PARTIALLY_ISSUED to issue parts");
        }

        RepairOrder order = orderMapper.selectById(partRequest.getOrderId());

        for (IssuePartsRequest.IssueItem issueItem : request.getItems()) {
            PartRequestItem item = partRequestItemMapper.selectById(issueItem.getRequestItemId());
            if (item == null || !item.getRequestId().equals(requestId)) {
                throw new BusinessException("Invalid request item: " + issueItem.getRequestItemId());
            }

            int issuedQty = issueItem.getIssuedQty();
            if (issuedQty <= 0 || issuedQty > item.getRequestedQty() - item.getIssuedQty()) {
                throw new BusinessException("Invalid issue quantity for item: " + item.getId());
            }

            // Deduct from inventory (reserved → issued, then total decreases)
            SparePartInventory inventory = findInventory(item.getPartId(),
                    order.getCommunityId(), order.getBuildingId());
            if (inventory != null) {
                int deductFromReserved = Math.min(issuedQty, inventory.getReservedQty());
                inventory.setReservedQty(inventory.getReservedQty() - deductFromReserved);
                // If issued more than reserved (e.g. partial reservation), deduct remainder from available
                int remainder = issuedQty - deductFromReserved;
                if (remainder > 0) {
                    inventory.setAvailableQty(inventory.getAvailableQty() - remainder);
                }
                // Total always decreases by the full issued quantity
                inventory.setTotalQty(inventory.getTotalQty() - issuedQty);
                inventoryMapper.updateById(inventory);
            }

            item.setIssuedQty(item.getIssuedQty() + issuedQty);
            if (item.getIssuedQty() >= item.getRequestedQty()) {
                item.setStatus(PartRequestStatus.ISSUED.getCode());
            } else {
                item.setStatus("PARTIALLY_ISSUED");
            }
            partRequestItemMapper.updateById(item);
        }

        // Update request status — re-query all items to get accurate state after updates
        List<PartRequestItem> allItems = partRequestItemMapper.selectList(
                new LambdaQueryWrapper<PartRequestItem>()
                        .eq(PartRequestItem::getRequestId, requestId));
        boolean allIssued = allItems.stream()
                .allMatch(i -> i.getIssuedQty() >= i.getRequestedQty());
        boolean anyIssued = allItems.stream().anyMatch(i -> i.getIssuedQty() > 0);

        if (allIssued) {
            partRequest.setStatus(PartRequestStatus.ISSUED.getCode());
        } else if (anyIssued) {
            partRequest.setStatus(PartRequestStatus.PARTIALLY_ISSUED.getCode());
        }
        partRequestMapper.updateById(partRequest);

        // Check if ALL critical items across ALL active requests for this order are fulfilled
        boolean allCriticalIssued = checkAllCriticalItemsIssued(partRequest.getOrderId());

        // If order is in WAITING_PARTS and all critical parts are now issued, auto-resume
        if (order != null && OrderStatus.WAITING_PARTS.getCode().equals(order.getStatus())
                && allCriticalIssued) {
            resumeFromWaitingParts(order);
        }

        partAuditLog("PART_REQUEST", requestId, partRequest.getOrderId(),
                "ISSUE", issuerId, UserRole.ADMIN.getCode(),
                null, Map.of("issuedItems", request.getItems().size()),
                "Parts issued");
    }

    // ==========================================================================
    // Return & Consumption
    // ==========================================================================

    @Override
    @Transactional
    public void returnParts(Long requestId, ReturnPartsRequest request, Long workerId) {
        PartRequest partRequest = partRequestMapper.selectById(requestId);
        if (partRequest == null) {
            throw new BusinessException("Part request not found");
        }

        RepairOrder order = orderMapper.selectById(partRequest.getOrderId());

        for (ReturnPartsRequest.ReturnItem returnItem : request.getItems()) {
            PartRequestItem item = partRequestItemMapper.selectById(returnItem.getRequestItemId());
            if (item == null || !item.getRequestId().equals(requestId)) {
                throw new BusinessException("Invalid request item: " + returnItem.getRequestItemId());
            }

            int returnableQty = item.getIssuedQty() - item.getReturnedQty() - item.getConsumedQty();
            if (returnItem.getReturnedQty() <= 0 || returnItem.getReturnedQty() > returnableQty) {
                throw new BusinessException("Invalid return quantity for item: " + item.getId()
                        + ", returnable: " + returnableQty);
            }

            // Add back to inventory
            SparePartInventory inventory = findOrCreateInventory(item.getPartId(),
                    order.getCommunityId(), order.getBuildingId());
            inventory.setAvailableQty(inventory.getAvailableQty() + returnItem.getReturnedQty());
            inventory.setTotalQty(inventory.getTotalQty() + returnItem.getReturnedQty());
            inventoryMapper.updateById(inventory);

            item.setReturnedQty(item.getReturnedQty() + returnItem.getReturnedQty());
            partRequestItemMapper.updateById(item);
        }

        // Check if all items are returned
        List<PartRequestItem> allItems = partRequestItemMapper.selectList(
                new LambdaQueryWrapper<PartRequestItem>()
                        .eq(PartRequestItem::getRequestId, requestId));
        boolean allReturned = allItems.stream()
                .allMatch(i -> (i.getReturnedQty() + i.getConsumedQty()) >= i.getIssuedQty());
        if (allReturned) {
            partRequest.setStatus(PartRequestStatus.RETURNED.getCode());
            partRequestMapper.updateById(partRequest);
        }

        partAuditLog("PART_REQUEST", requestId, partRequest.getOrderId(),
                "RETURN", workerId, UserRole.WORKER.getCode(),
                null, Map.of("returnedItems", request.getItems().size()),
                "Parts returned");
    }

    @Override
    @Transactional
    public void consumeParts(Long requestId, ConsumePartsRequest request, Long workerId) {
        PartRequest partRequest = partRequestMapper.selectById(requestId);
        if (partRequest == null) {
            throw new BusinessException("Part request not found");
        }

        for (ConsumePartsRequest.ConsumeItem consumeItem : request.getItems()) {
            PartRequestItem item = partRequestItemMapper.selectById(consumeItem.getRequestItemId());
            if (item == null || !item.getRequestId().equals(requestId)) {
                throw new BusinessException("Invalid request item: " + consumeItem.getRequestItemId());
            }

            int consumableQty = item.getIssuedQty() - item.getReturnedQty() - item.getConsumedQty();
            if (consumeItem.getConsumedQty() <= 0 || consumeItem.getConsumedQty() > consumableQty) {
                throw new BusinessException("Invalid consume quantity for item: " + item.getId()
                        + ", consumable: " + consumableQty);
            }

            item.setConsumedQty(item.getConsumedQty() + consumeItem.getConsumedQty());
            partRequestItemMapper.updateById(item);
        }

        partAuditLog("PART_REQUEST", requestId, partRequest.getOrderId(),
                "CONSUME", workerId, UserRole.WORKER.getCode(),
                null, Map.of("consumedItems", request.getItems().size()),
                "Parts consumed");
    }

    // ==========================================================================
    // Purchase Request Lifecycle
    // ==========================================================================

    @Override
    @Transactional
    public PurchaseRequestVO createPurchaseRequest(PurchaseRequestCreateRequest request, Long userId) {
        SparePart part = sparePartMapper.selectById(request.getPartId());
        if (part == null) {
            throw new BusinessException("Part not found");
        }

        PurchaseRequest purchase = new PurchaseRequest();
        purchase.setPurchaseNo(generatePurchaseNo());
        purchase.setPartId(request.getPartId());
        purchase.setCommunityId(request.getCommunityId());
        purchase.setQuantity(request.getQuantity());
        purchase.setUrgency(request.getUrgency());
        purchase.setStatus(PurchaseRequestStatus.PENDING.getCode());
        purchase.setRequestedBy(userId);
        purchase.setRemark(request.getRemark());
        purchaseRequestMapper.insert(purchase);

        partAuditLog("PURCHASE_REQUEST", purchase.getId(), null,
                "CREATE", userId, UserRole.WORKER.getCode(),
                null, Map.of("purchaseNo", purchase.getPurchaseNo(),
                        "partId", request.getPartId(),
                        "quantity", request.getQuantity()),
                "Purchase request created");

        return buildPurchaseRequestVO(purchase);
    }

    @Override
    @Transactional
    public void approvePurchaseRequest(Long purchaseId, Long approverId) {
        PurchaseRequest purchase = purchaseRequestMapper.selectById(purchaseId);
        if (purchase == null) {
            throw new BusinessException("Purchase request not found");
        }
        if (!PurchaseRequestStatus.PENDING.getCode().equals(purchase.getStatus())) {
            throw new BusinessException("Only PENDING purchase requests can be approved");
        }

        purchase.setStatus(PurchaseRequestStatus.APPROVED.getCode());
        purchase.setApprovedBy(approverId);
        purchase.setApprovedAt(LocalDateTime.now());
        purchaseRequestMapper.updateById(purchase);

        partAuditLog("PURCHASE_REQUEST", purchaseId, null,
                "APPROVE", approverId, UserRole.ADMIN.getCode(),
                Map.of("status", PurchaseRequestStatus.PENDING.getCode()),
                Map.of("status", PurchaseRequestStatus.APPROVED.getCode()),
                "Purchase request approved");
    }

    @Override
    @Transactional
    public void markPurchaseOrdered(Long purchaseId, Long operatorId) {
        PurchaseRequest purchase = purchaseRequestMapper.selectById(purchaseId);
        if (purchase == null) {
            throw new BusinessException("Purchase request not found");
        }
        if (!PurchaseRequestStatus.APPROVED.getCode().equals(purchase.getStatus())) {
            throw new BusinessException("Only APPROVED purchase requests can be marked as ordered");
        }

        purchase.setStatus(PurchaseRequestStatus.ORDERED.getCode());
        purchaseRequestMapper.updateById(purchase);

        partAuditLog("PURCHASE_REQUEST", purchaseId, null,
                "ORDER", operatorId, UserRole.ADMIN.getCode(),
                Map.of("status", PurchaseRequestStatus.APPROVED.getCode()),
                Map.of("status", PurchaseRequestStatus.ORDERED.getCode()),
                "Purchase order placed");
    }

    @Override
    @Transactional
    public void receivePurchase(Long purchaseId, Long operatorId) {
        PurchaseRequest purchase = purchaseRequestMapper.selectById(purchaseId);
        if (purchase == null) {
            throw new BusinessException("Purchase request not found");
        }
        if (!PurchaseRequestStatus.ORDERED.getCode().equals(purchase.getStatus())) {
            throw new BusinessException("Only ORDERED purchase requests can receive parts");
        }

        // Increase inventory
        SparePartInventory inventory = findOrCreateInventory(
                purchase.getPartId(), purchase.getCommunityId(), null);
        inventory.setAvailableQty(inventory.getAvailableQty() + purchase.getQuantity());
        inventory.setTotalQty(inventory.getTotalQty() + purchase.getQuantity());
        inventoryMapper.updateById(inventory);

        purchase.setStatus(PurchaseRequestStatus.RECEIVED.getCode());
        purchase.setReceivedAt(LocalDateTime.now());
        purchaseRequestMapper.updateById(purchase);

        partAuditLog("PURCHASE_REQUEST", purchaseId, null,
                "RECEIVE", operatorId, UserRole.ADMIN.getCode(),
                Map.of("status", PurchaseRequestStatus.ORDERED.getCode()),
                Map.of("status", PurchaseRequestStatus.RECEIVED.getCode(),
                        "quantity", purchase.getQuantity()),
                "Parts received and stocked");

        // Check if any WAITING_PARTS orders can now be resumed
        checkAndResumeWaitingOrders(purchase.getCommunityId(), null);
    }

    // ==========================================================================
    // Inventory Query
    // ==========================================================================

    @Override
    public List<SparePartInventoryVO> queryInventory(Long communityId, Long buildingId) {
        LambdaQueryWrapper<SparePartInventory> wrapper = new LambdaQueryWrapper<>();
        if (communityId != null) {
            wrapper.eq(SparePartInventory::getCommunityId, communityId);
        }
        if (buildingId != null) {
            wrapper.eq(SparePartInventory::getBuildingId, buildingId);
        }
        wrapper.orderByAsc(SparePartInventory::getPartId);

        List<SparePartInventory> inventories = inventoryMapper.selectList(wrapper);
        return inventories.stream().map(inv -> {
            SparePartInventoryVO vo = new SparePartInventoryVO();
            vo.setId(inv.getId());
            vo.setPartId(inv.getPartId());
            vo.setCommunityId(inv.getCommunityId());
            vo.setBuildingId(inv.getBuildingId());
            vo.setAvailableQty(inv.getAvailableQty());
            vo.setReservedQty(inv.getReservedQty());
            vo.setTotalQty(inv.getTotalQty());
            vo.setLocationCode(inv.getLocationCode());
            vo.setUpdatedAt(inv.getUpdatedAt());

            SparePart part = sparePartMapper.selectById(inv.getPartId());
            if (part != null) {
                vo.setPartCode(part.getPartCode());
                vo.setPartName(part.getPartName());
                vo.setCategory(part.getCategory());
                vo.setSpecification(part.getSpecification());
                vo.setUnit(part.getUnit());
                vo.setSafetyStock(part.getSafetyStock());
                vo.setBelowSafetyStock(inv.getAvailableQty() < part.getSafetyStock());
            }
            return vo;
        }).collect(Collectors.toList());
    }

    // ==========================================================================
    // Auto-resume WAITING_PARTS orders
    // ==========================================================================

    @Override
    @Transactional
    public void checkAndResumeWaitingOrders(Long communityId, Long buildingId) {
        LambdaQueryWrapper<RepairOrder> wrapper = new LambdaQueryWrapper<RepairOrder>()
                .eq(RepairOrder::getStatus, OrderStatus.WAITING_PARTS.getCode());
        if (communityId != null) {
            wrapper.eq(RepairOrder::getCommunityId, communityId);
        }
        if (buildingId != null) {
            wrapper.eq(RepairOrder::getBuildingId, buildingId);
        }

        List<RepairOrder> waitingOrders = orderMapper.selectList(wrapper);
        for (RepairOrder order : waitingOrders) {
            if (canResumeOrder(order)) {
                resumeFromWaitingParts(order);
            }
        }
    }

    // ==========================================================================
    // Private Helpers — Status Transitions
    // ==========================================================================

    /**
     * Enter WAITING_PARTS status: pause SLA timer, clear timeout Redis keys.
     */
    private void enterWaitingPartsStatus(RepairOrder order, Long workerId) {
        String fromStatus = order.getStatus();

        // Validate state transition
        stateMachine.validateTransition(fromStatus, OrderStatus.WAITING_PARTS.getCode());

        order.setPreviousStatus(fromStatus);
        order.setStatus(OrderStatus.WAITING_PARTS.getCode());
        order.setWaitingPartsAt(LocalDateTime.now());
        orderMapper.updateById(order);

        // Clear SLA deadline keys only (preserve processed keys for duplicate prevention)
        clearSlaTimeoutKeys(order.getId());

        // Record progress
        recordProgress(order.getId(), fromStatus, OrderStatus.WAITING_PARTS.getCode(),
                workerId, UserRole.WORKER.getCode(),
                "Order paused: waiting for critical spare parts");

        log.info("Order {} entered WAITING_PARTS from {} (SLA paused)",
                order.getOrderNo(), fromStatus);
    }

    /**
     * Resume order from WAITING_PARTS when all critical parts are available.
     * Adjusts SLA timestamps by the waiting duration.
     */
    private void resumeFromWaitingParts(RepairOrder order) {
        String resumeTo = order.getPreviousStatus() != null
                ? order.getPreviousStatus() : OrderStatus.ACCEPTED.getCode();

        // Calculate wait duration and adjust SLA timestamps
        LocalDateTime waitingPartsAt = order.getWaitingPartsAt();
        long waitSeconds = 0;
        if (waitingPartsAt != null) {
            waitSeconds = Duration.between(waitingPartsAt, LocalDateTime.now()).getSeconds();
            int totalWaiting = (order.getTotalWaitingPartsSeconds() != null
                    ? order.getTotalWaitingPartsSeconds() : 0) + (int) waitSeconds;
            order.setTotalWaitingPartsSeconds(totalWaiting);

            // Adjust SLA timestamps (same logic as resume from suspend)
            if (OrderStatus.ACCEPTED.getCode().equals(resumeTo)) {
                if (order.getAcceptedAt() != null) {
                    order.setAcceptedAt(order.getAcceptedAt().plusSeconds(waitSeconds));
                }
            } else if (OrderStatus.VISITING.getCode().equals(resumeTo)) {
                if (order.getVisitAt() != null) {
                    order.setVisitAt(order.getVisitAt().plusSeconds(waitSeconds));
                }
            }
        }

        order.setStatus(resumeTo);
        order.setPreviousStatus(null);
        order.setWaitingPartsAt(null);
        orderMapper.updateById(order);

        // Clear processed keys so fresh escalation can fire for the adjusted SLA period
        clearProcessedKeys(order.getId());

        // Re-establish Redis timeout keys with adjusted deadlines
        reestablishTimeoutKeys(order, resumeTo);

        // Record progress
        recordProgress(order.getId(), OrderStatus.WAITING_PARTS.getCode(), resumeTo,
                order.getAssignedWorkerId(), UserRole.WORKER.getCode(),
                String.format("Parts arrived, order resumed (waited %d sec). SLA deadline adjusted.", waitSeconds));

        // Notify worker via Redis pub/sub (notification channel)
        notifyWorkerPartsArrived(order);

        log.info("Order {} resumed from WAITING_PARTS to {} (waited {} sec)",
                order.getOrderNo(), resumeTo, waitSeconds);
    }

    /**
     * Check if all critical part requests for an order have been fulfilled.
     */
    private boolean canResumeOrder(RepairOrder order) {
        List<PartRequest> requests = partRequestMapper.selectList(
                new LambdaQueryWrapper<PartRequest>()
                        .eq(PartRequest::getOrderId, order.getId())
                        .in(PartRequest::getStatus,
                                PartRequestStatus.PENDING.getCode(),
                                PartRequestStatus.APPROVED.getCode(),
                                PartRequestStatus.PARTIALLY_ISSUED.getCode()));

        if (requests.isEmpty()) {
            return true;
        }

        // Check if all critical items across all active requests have been issued
        // OR have sufficient available stock to be fulfilled
        for (PartRequest request : requests) {
            List<PartRequestItem> criticalItems = partRequestItemMapper.selectList(
                    new LambdaQueryWrapper<PartRequestItem>()
                            .eq(PartRequestItem::getRequestId, request.getId())
                            .eq(PartRequestItem::getCritical, 1));

            for (PartRequestItem item : criticalItems) {
                int deficit = item.getRequestedQty() - item.getIssuedQty();
                if (deficit <= 0) {
                    continue; // Already fully issued
                }
                // Check if available stock can cover the deficit
                int available = getAvailableStock(item.getPartId(),
                        order.getCommunityId(), order.getBuildingId());
                if (available < deficit) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Check if ALL critical items across ALL active requests for an order have been issued.
     * Used after issuing parts to determine if auto-resume from WAITING_PARTS is possible.
     */
    private boolean checkAllCriticalItemsIssued(Long orderId) {
        List<PartRequest> activeRequests = partRequestMapper.selectList(
                new LambdaQueryWrapper<PartRequest>()
                        .eq(PartRequest::getOrderId, orderId)
                        .in(PartRequest::getStatus,
                                PartRequestStatus.PENDING.getCode(),
                                PartRequestStatus.APPROVED.getCode(),
                                PartRequestStatus.PARTIALLY_ISSUED.getCode()));

        for (PartRequest request : activeRequests) {
            List<PartRequestItem> criticalItems = partRequestItemMapper.selectList(
                    new LambdaQueryWrapper<PartRequestItem>()
                            .eq(PartRequestItem::getRequestId, request.getId())
                            .eq(PartRequestItem::getCritical, 1));

            for (PartRequestItem item : criticalItems) {
                if (item.getIssuedQty() < item.getRequestedQty()) {
                    return false;
                }
            }
        }
        return true;
    }

    // ==========================================================================
    // Private Helpers — Inventory
    // ==========================================================================

    private int getAvailableStock(Long partId, Long communityId, Long buildingId) {
        SparePartInventory inventory = findInventory(partId, communityId, buildingId);
        return inventory != null ? inventory.getAvailableQty() : 0;
    }

    private SparePartInventory findInventory(Long partId, Long communityId, Long buildingId) {
        LambdaQueryWrapper<SparePartInventory> wrapper = new LambdaQueryWrapper<SparePartInventory>()
                .eq(SparePartInventory::getPartId, partId);
        if (communityId != null) {
            wrapper.eq(SparePartInventory::getCommunityId, communityId);
        }
        if (buildingId != null) {
            wrapper.eq(SparePartInventory::getBuildingId, buildingId);
        } else {
            wrapper.isNull(SparePartInventory::getBuildingId);
        }
        return inventoryMapper.selectOne(wrapper);
    }

    private SparePartInventory findOrCreateInventory(Long partId, Long communityId, Long buildingId) {
        SparePartInventory inventory = findInventory(partId, communityId, buildingId);
        if (inventory == null) {
            inventory = new SparePartInventory();
            inventory.setPartId(partId);
            inventory.setCommunityId(communityId);
            inventory.setBuildingId(buildingId);
            inventory.setAvailableQty(0);
            inventory.setReservedQty(0);
            inventory.setTotalQty(0);
            inventoryMapper.insert(inventory);
        }
        return inventory;
    }

    // ==========================================================================
    // Private Helpers — Redis & SLA
    // ==========================================================================

    private void clearAllTimeoutKeys(Long orderId) {
        redisTemplate.delete("timeout:accept:" + orderId);
        redisTemplate.delete("timeout:visit:" + orderId);
        redisTemplate.delete("timeout:complete:" + orderId);
        redisTemplate.delete("timeout:processed:ACCEPT_TIMEOUT:" + orderId);
        redisTemplate.delete("timeout:processed:VISIT_TIMEOUT:" + orderId);
        redisTemplate.delete("timeout:processed:COMPLETE_TIMEOUT:" + orderId);
    }

    /**
     * Clear only SLA deadline keys (not processed keys).
     * Used when entering WAITING_PARTS — preserves processed keys so that
     * the same dispatch round's escalation history is retained until resume.
     */
    private void clearSlaTimeoutKeys(Long orderId) {
        redisTemplate.delete("timeout:accept:" + orderId);
        redisTemplate.delete("timeout:visit:" + orderId);
        redisTemplate.delete("timeout:complete:" + orderId);
    }

    /**
     * Clear processed keys to allow fresh escalation after SLA adjustment.
     * Called on resume from WAITING_PARTS when deadlines have been shifted.
     */
    private void clearProcessedKeys(Long orderId) {
        redisTemplate.delete("timeout:processed:ACCEPT_TIMEOUT:" + orderId);
        redisTemplate.delete("timeout:processed:VISIT_TIMEOUT:" + orderId);
        redisTemplate.delete("timeout:processed:COMPLETE_TIMEOUT:" + orderId);
    }

    private void reestablishTimeoutKeys(RepairOrder order, String resumeTo) {
        if (OrderStatus.ACCEPTED.getCode().equals(resumeTo) && order.getAcceptedAt() != null) {
            LocalDateTime visitDeadline = order.getAcceptedAt().plusHours(4);
            redisTemplate.opsForValue().set(
                    "timeout:visit:" + order.getId(),
                    visitDeadline.toString(),
                    Duration.ofHours(5));
        } else if (OrderStatus.VISITING.getCode().equals(resumeTo) && order.getVisitAt() != null) {
            LocalDateTime completeDeadline = order.getVisitAt().plusHours(48);
            redisTemplate.opsForValue().set(
                    "timeout:complete:" + order.getId(),
                    completeDeadline.toString(),
                    Duration.ofHours(49));
        } else if (OrderStatus.REWORKING.getCode().equals(resumeTo) && order.getVisitAt() != null) {
            // Reworking shares the completion SLA — re-establish complete timeout
            LocalDateTime completeDeadline = order.getVisitAt().plusHours(48);
            redisTemplate.opsForValue().set(
                    "timeout:complete:" + order.getId(),
                    completeDeadline.toString(),
                    Duration.ofHours(49));
        }
    }

    private void notifyWorkerPartsArrived(RepairOrder order) {
        String notifyKey = "notification:parts_arrived:" + order.getAssignedWorkerId();
        redisTemplate.opsForValue().set(notifyKey,
                Map.of("orderId", order.getId(),
                        "orderNo", order.getOrderNo(),
                        "message", "Parts arrived, order resumed"),
                Duration.ofHours(24));
    }

    // ==========================================================================
    // Private Helpers — VO Builders
    // ==========================================================================

    private PartRequestVO buildPartRequestVO(PartRequest request) {
        PartRequestVO vo = new PartRequestVO();
        vo.setId(request.getId());
        vo.setOrderId(request.getOrderId());
        vo.setWorkerId(request.getWorkerId());
        vo.setRequestNo(request.getRequestNo());
        vo.setRequestType(request.getRequestType());
        vo.setStatus(request.getStatus());
        vo.setRemark(request.getRemark());
        vo.setApprovedBy(request.getApprovedBy());
        vo.setApprovedAt(request.getApprovedAt());
        vo.setCreatedAt(request.getCreatedAt());

        // Enrich with names
        RepairOrder order = orderMapper.selectById(request.getOrderId());
        if (order != null) {
            vo.setOrderNo(order.getOrderNo());
        }
        User worker = userMapper.selectById(request.getWorkerId());
        if (worker != null) {
            vo.setWorkerName(worker.getRealName());
        }
        if (request.getApprovedBy() != null) {
            User approver = userMapper.selectById(request.getApprovedBy());
            if (approver != null) {
                vo.setApprovedByName(approver.getRealName());
            }
        }

        // Items
        List<PartRequestItem> items = partRequestItemMapper.selectList(
                new LambdaQueryWrapper<PartRequestItem>()
                        .eq(PartRequestItem::getRequestId, request.getId()));
        vo.setItems(items.stream().map(item -> {
            PartRequestVO.PartRequestItemVO itemVO = new PartRequestVO.PartRequestItemVO();
            itemVO.setId(item.getId());
            itemVO.setPartId(item.getPartId());
            itemVO.setRequestedQty(item.getRequestedQty());
            itemVO.setIssuedQty(item.getIssuedQty());
            itemVO.setReturnedQty(item.getReturnedQty());
            itemVO.setConsumedQty(item.getConsumedQty());
            itemVO.setStatus(item.getStatus());
            itemVO.setCritical(item.getCritical());

            SparePart part = sparePartMapper.selectById(item.getPartId());
            if (part != null) {
                itemVO.setPartCode(part.getPartCode());
                itemVO.setPartName(part.getPartName());
                itemVO.setSpecification(part.getSpecification());
                itemVO.setUnit(part.getUnit());
            }

            // Current stock info
            if (order != null) {
                itemVO.setAvailableStock(getAvailableStock(item.getPartId(),
                        order.getCommunityId(), order.getBuildingId()));
            }

            return itemVO;
        }).collect(Collectors.toList()));

        return vo;
    }

    private PurchaseRequestVO buildPurchaseRequestVO(PurchaseRequest purchase) {
        PurchaseRequestVO vo = new PurchaseRequestVO();
        vo.setId(purchase.getId());
        vo.setPurchaseNo(purchase.getPurchaseNo());
        vo.setPartId(purchase.getPartId());
        vo.setCommunityId(purchase.getCommunityId());
        vo.setQuantity(purchase.getQuantity());
        vo.setUrgency(purchase.getUrgency());
        vo.setStatus(purchase.getStatus());
        vo.setRequestedBy(purchase.getRequestedBy());
        vo.setApprovedBy(purchase.getApprovedBy());
        vo.setApprovedAt(purchase.getApprovedAt());
        vo.setTriggerRequestId(purchase.getTriggerRequestId());
        vo.setTriggerOrderId(purchase.getTriggerOrderId());
        vo.setRemark(purchase.getRemark());
        vo.setReceivedAt(purchase.getReceivedAt());
        vo.setCreatedAt(purchase.getCreatedAt());

        SparePart part = sparePartMapper.selectById(purchase.getPartId());
        if (part != null) {
            vo.setPartCode(part.getPartCode());
            vo.setPartName(part.getPartName());
        }

        return vo;
    }

    // ==========================================================================
    // Private Helpers — Misc
    // ==========================================================================

    private void recordProgress(Long orderId, String fromStatus, String toStatus,
                                Long operatorId, String operatorRole, String remark) {
        RepairProgress progress = new RepairProgress();
        progress.setOrderId(orderId);
        progress.setFromStatus(fromStatus != null ? fromStatus : "");
        progress.setToStatus(toStatus);
        progress.setOperatorId(operatorId);
        progress.setOperatorRole(operatorRole);
        progress.setRemark(remark);
        progressMapper.insert(progress);
    }

    private void partAuditLog(String entityType, Long entityId, Long orderId,
                               String action, Long userId, String userRole,
                               Object beforeData, Object afterData, String remark) {
        try {
            PartAuditLog log = new PartAuditLog();
            log.setEntityType(entityType);
            log.setEntityId(entityId);
            log.setOrderId(orderId);
            log.setAction(action);
            log.setUserId(userId);
            log.setUserRole(userRole);
            log.setRemark(remark);

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            if (beforeData != null) {
                log.setBeforeData(mapper.writeValueAsString(beforeData));
            }
            if (afterData != null) {
                log.setAfterData(mapper.writeValueAsString(afterData));
            }
            partAuditLogMapper.insert(log);
        } catch (Exception e) {
            SparePartServiceImpl.log.error("Failed to write part audit log: entityType={}, action={}",
                    entityType, action, e);
        }
    }

    private String generatePartRequestNo() {
        String prefix = "PR";
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long seq = SEQ.incrementAndGet();
        return prefix + date + String.format("%06d", seq % 1000000);
    }

    private String generatePurchaseNo() {
        String prefix = "PO";
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long seq = SEQ.incrementAndGet();
        return prefix + date + String.format("%06d", seq % 1000000);
    }
}
