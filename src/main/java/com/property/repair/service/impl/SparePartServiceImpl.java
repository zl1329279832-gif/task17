package com.property.repair.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.property.repair.dto.*;
import com.property.repair.entity.*;
import com.property.repair.enums.OrderStatus;
import com.property.repair.enums.PurchaseStatus;
import com.property.repair.enums.RequisitionStatus;
import com.property.repair.enums.UserRole;
import com.property.repair.exception.BusinessException;
import com.property.repair.mapper.*;
import com.property.repair.service.SparePartService;
import com.property.repair.statemachine.OrderStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final SparePartRequisitionMapper requisitionMapper;
    private final SparePartRequisitionItemMapper requisitionItemMapper;
    private final PurchaseRequestMapper purchaseRequestMapper;
    private final SparePartAuditLogMapper auditLogMapper;
    private final RepairOrderMapper orderMapper;
    private final RepairProgressMapper progressMapper;
    private final OrderStateMachine stateMachine;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final AtomicLong SEQ = new AtomicLong(0);

    // ==========================================================================
    // Spare Part Recommendation
    // ==========================================================================

    @Override
    public List<SparePartRecommendVO> recommendParts(Long orderId) {
        RepairOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("Order not found");
        }

        // 1. Find parts matching the problem type
        List<SparePart> matchingParts = sparePartMapper.selectList(
                new LambdaQueryWrapper<SparePart>()
                        .eq(SparePart::getProblemType, order.getProblemType()));

        if (matchingParts.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. Get historical consumption stats
        List<Map<String, Object>> history = sparePartMapper.countHistoricalConsumption(
                order.getProblemType(), order.getCommunityId());
        Map<Long, Integer> consumptionMap = new HashMap<>();
        for (Map<String, Object> row : history) {
            Long partId = ((Number) row.get("part_id")).longValue();
            Integer consumed = ((Number) row.get("total_consumed")).intValue();
            consumptionMap.put(partId, consumed);
        }

        // 3. Get inventory for this community
        List<SparePartInventory> inventories = inventoryMapper.selectList(
                new LambdaQueryWrapper<SparePartInventory>()
                        .eq(SparePartInventory::getCommunityId, order.getCommunityId()));
        Map<Long, Integer> stockMap = inventories.stream()
                .collect(Collectors.toMap(
                        SparePartInventory::getPartId,
                        inv -> inv.getQuantity() - inv.getReservedQuantity()));

        // 4. Build recommendation list sorted by historical consumption desc
        List<SparePartRecommendVO> result = new ArrayList<>();
        for (SparePart part : matchingParts) {
            SparePartRecommendVO vo = new SparePartRecommendVO();
            vo.setPartId(part.getId());
            vo.setPartNo(part.getPartNo());
            vo.setName(part.getName());
            vo.setSpecification(part.getSpecification());
            vo.setUnit(part.getUnit());
            vo.setIsCritical(part.getIsCritical());

            int available = stockMap.getOrDefault(part.getId(), 0);
            vo.setAvailableStock(Math.max(available, 0));
            vo.setStockSufficient(available > 0);

            int consumed = consumptionMap.getOrDefault(part.getId(), 0);
            vo.setHistoricalConsumption(consumed);

            // Build recommendation reason
            List<String> reasons = new ArrayList<>();
            reasons.add("Matches repair category: " + order.getProblemType());
            if (consumed > 0) {
                reasons.add("Used " + consumed + " times in similar repairs nearby");
            }
            if (part.getIsCritical() != null && part.getIsCritical() == 1) {
                reasons.add("Critical part");
            }
            vo.setRecommendReason(String.join("; ", reasons));

            result.add(vo);
        }

        // Sort: critical parts first, then by historical consumption desc, then by stock desc
        result.sort(Comparator
                .comparing(SparePartRecommendVO::getIsCritical, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(SparePartRecommendVO::getHistoricalConsumption, Comparator.reverseOrder())
                .thenComparing(SparePartRecommendVO::getAvailableStock, Comparator.reverseOrder()));

        return result;
    }

    // ==========================================================================
    // Spare Part Requisition
    // ==========================================================================

    @Override
    @Transactional
    public void submitRequisition(SparePartRequisitionRequest request, Long workerId) {
        RepairOrder order = orderMapper.selectById(request.getOrderId());
        if (order == null) {
            throw new BusinessException("Order not found");
        }

        // Only the assigned worker can request parts
        if (!workerId.equals(order.getAssignedWorkerId())) {
            throw new BusinessException(403, "You are not the assigned worker for this order");
        }

        // Order must be in an active working state
        String status = order.getStatus();
        if (!OrderStatus.ACCEPTED.getCode().equals(status)
                && !OrderStatus.VISITING.getCode().equals(status)
                && !OrderStatus.REWORKING.getCode().equals(status)) {
            throw new BusinessException("Parts can only be requested in ACCEPTED/VISITING/REWORKING status");
        }

        // Check for duplicate requisition (same order + same part + active requisition)
        checkDuplicateRequisition(request);

        // Create requisition record
        SparePartRequisition requisition = new SparePartRequisition();
        requisition.setRequisitionNo(generateRequisitionNo());
        requisition.setOrderId(request.getOrderId());
        requisition.setWorkerId(workerId);
        requisition.setReworkOrderId(request.getReworkOrderId());
        requisition.setStatus(RequisitionStatus.PENDING.getCode());
        requisitionMapper.insert(requisition);

        // Process each item
        boolean hasCriticalShortage = false;

        for (SparePartRequisitionRequest.Item item : request.getItems()) {
            SparePart part = sparePartMapper.selectById(item.getPartId());
            if (part == null) {
                throw new BusinessException("Spare part not found: " + item.getPartId());
            }

            SparePartRequisitionItem reqItem = new SparePartRequisitionItem();
            reqItem.setRequisitionId(requisition.getId());
            reqItem.setPartId(item.getPartId());
            reqItem.setRequestedQuantity(item.getQuantity());
            reqItem.setIssuedQuantity(0);
            reqItem.setConsumedQuantity(0);
            reqItem.setReturnedQuantity(0);

            // Check inventory
            SparePartInventory inventory = getInventory(item.getPartId(), order.getCommunityId());
            int available = inventory != null ? inventory.getQuantity() - inventory.getReservedQuantity() : 0;

            if (available >= item.getQuantity()) {
                // Sufficient stock — issue immediately
                reqItem.setIssuedQuantity(item.getQuantity());
                requisitionItemMapper.insert(reqItem);

                // Deduct inventory
                inventory.setQuantity(inventory.getQuantity() - item.getQuantity());
                inventoryMapper.updateById(inventory);

                logAudit(item.getPartId(), inventory.getId(), requisition.getId(),
                        order.getId(), "ISSUE", -item.getQuantity(),
                        available, available - item.getQuantity(),
                        workerId, "Issued to worker");
            } else {
                // Insufficient stock
                requisitionItemMapper.insert(reqItem);

                if (part.getIsCritical() != null && part.getIsCritical() == 1) {
                    hasCriticalShortage = true;
                }

                // Auto-create purchase request
                int deficit = item.getQuantity() - Math.max(available, 0);
                createPurchaseRequest(part, order.getCommunityId(), deficit,
                        requisition.getId(), order.getId());

                log.warn("Insufficient stock for part={}, available={}, requested={}, orderId={}",
                        part.getPartNo(), available, item.getQuantity(), order.getId());
            }
        }

        if (hasCriticalShortage) {
            // Critical parts unavailable — transition order to WAITING_PARTS
            requisition.setStatus(RequisitionStatus.PENDING.getCode());
            requisitionMapper.updateById(requisition);
            enterWaitingParts(order);
        } else {
            // All parts available or only non-critical parts missing
            boolean allIssued = true;
            List<SparePartRequisitionItem> items = requisitionItemMapper.selectList(
                    new LambdaQueryWrapper<SparePartRequisitionItem>()
                            .eq(SparePartRequisitionItem::getRequisitionId, requisition.getId()));
            for (SparePartRequisitionItem ri : items) {
                if (ri.getIssuedQuantity() < ri.getRequestedQuantity()) {
                    allIssued = false;
                    break;
                }
            }
            requisition.setStatus(allIssued
                    ? RequisitionStatus.ISSUED.getCode()
                    : RequisitionStatus.PENDING.getCode());
            requisitionMapper.updateById(requisition);
        }

        log.info("Requisition {} created for order {}, criticalShortage={}",
                requisition.getRequisitionNo(), order.getOrderNo(), hasCriticalShortage);
    }

    // ==========================================================================
    // Return Parts
    // ==========================================================================

    @Override
    @Transactional
    public void returnParts(Long requisitionId, SparePartReturnRequest request, Long workerId) {
        SparePartRequisition requisition = requisitionMapper.selectById(requisitionId);
        if (requisition == null) {
            throw new BusinessException("Requisition not found");
        }
        if (!workerId.equals(requisition.getWorkerId())) {
            throw new BusinessException(403, "You are not the owner of this requisition");
        }
        if (!RequisitionStatus.ISSUED.getCode().equals(requisition.getStatus())) {
            throw new BusinessException("Can only return parts from ISSUED requisitions");
        }

        RepairOrder order = orderMapper.selectById(requisition.getOrderId());

        for (SparePartReturnRequest.Item item : request.getItems()) {
            SparePartRequisitionItem reqItem = requisitionItemMapper.selectOne(
                    new LambdaQueryWrapper<SparePartRequisitionItem>()
                            .eq(SparePartRequisitionItem::getRequisitionId, requisitionId)
                            .eq(SparePartRequisitionItem::getPartId, item.getPartId()));
            if (reqItem == null) {
                throw new BusinessException("Part not found in this requisition: " + item.getPartId());
            }

            int maxReturnable = reqItem.getIssuedQuantity() - reqItem.getConsumedQuantity() - reqItem.getReturnedQuantity();
            if (item.getQuantity() > maxReturnable) {
                throw new BusinessException("Return quantity exceeds available: max=" + maxReturnable);
            }

            reqItem.setReturnedQuantity(reqItem.getReturnedQuantity() + item.getQuantity());
            requisitionItemMapper.updateById(reqItem);

            // Restore inventory
            SparePartInventory inventory = getInventory(item.getPartId(), order.getCommunityId());
            if (inventory != null) {
                int before = inventory.getQuantity();
                inventory.setQuantity(before + item.getQuantity());
                inventoryMapper.updateById(inventory);

                logAudit(item.getPartId(), inventory.getId(), requisitionId,
                        order.getId(), "RETURN", item.getQuantity(),
                        before, before + item.getQuantity(),
                        workerId, "Returned by worker");
            }
        }
    }

    // ==========================================================================
    // Consume Parts (on order completion)
    // ==========================================================================

    @Override
    @Transactional
    public void consumePartsForOrder(Long orderId) {
        List<SparePartRequisition> requisitions = requisitionMapper.selectList(
                new LambdaQueryWrapper<SparePartRequisition>()
                        .eq(SparePartRequisition::getOrderId, orderId)
                        .eq(SparePartRequisition::getStatus, RequisitionStatus.ISSUED.getCode()));

        for (SparePartRequisition req : requisitions) {
            List<SparePartRequisitionItem> items = requisitionItemMapper.selectList(
                    new LambdaQueryWrapper<SparePartRequisitionItem>()
                            .eq(SparePartRequisitionItem::getRequisitionId, req.getId()));

            for (SparePartRequisitionItem item : items) {
                int consumed = item.getIssuedQuantity() - item.getReturnedQuantity();
                if (consumed > 0) {
                    item.setConsumedQuantity(consumed);
                    requisitionItemMapper.updateById(item);

                    logAudit(item.getPartId(), null, req.getId(),
                            orderId, "CONSUME", -consumed,
                            null, null,
                            req.getWorkerId(), "Consumed on order completion");
                }
            }

            req.setStatus(RequisitionStatus.COMPLETED.getCode());
            requisitionMapper.updateById(req);
        }
    }

    // ==========================================================================
    // Purchase Receive — auto-resume WAITING_PARTS orders
    // ==========================================================================

    @Override
    @Transactional
    public void receivePurchase(Long purchaseId, PurchaseReceiveRequest request, Long operatorId) {
        PurchaseRequest purchase = purchaseRequestMapper.selectById(purchaseId);
        if (purchase == null) {
            throw new BusinessException("Purchase request not found");
        }
        if (PurchaseStatus.RECEIVED.getCode().equals(purchase.getStatus())) {
            throw new BusinessException("Purchase already received");
        }

        // 1. Update purchase status
        purchase.setStatus(PurchaseStatus.RECEIVED.getCode());
        purchase.setReceivedAt(LocalDateTime.now());
        purchaseRequestMapper.updateById(purchase);

        // 2. Increase inventory
        SparePartInventory inventory = getOrCreateInventory(purchase.getPartId(), purchase.getCommunityId());
        int before = inventory.getQuantity();
        inventory.setQuantity(before + request.getReceivedQuantity());
        inventoryMapper.updateById(inventory);

        logAudit(purchase.getPartId(), inventory.getId(), purchase.getTriggerRequisitionId(),
                purchase.getTriggerOrderId(), "PURCHASE_IN", request.getReceivedQuantity(),
                before, before + request.getReceivedQuantity(),
                operatorId, "Purchase received: " + purchase.getRequestNo());

        // 3. Try to fulfill pending requisitions and resume WAITING_PARTS orders
        tryFulfillPendingRequisitions(purchase.getPartId(), purchase.getCommunityId());
    }

    // ==========================================================================
    // Private: Enter WAITING_PARTS state
    // ==========================================================================

    private void enterWaitingParts(RepairOrder order) {
        String fromStatus = order.getStatus();

        // Validate transition
        stateMachine.validateTransition(fromStatus, OrderStatus.WAITING_PARTS.getCode());

        order.setPreviousStatus(fromStatus);
        order.setStatus(OrderStatus.WAITING_PARTS.getCode());
        order.setSuspendedAt(LocalDateTime.now());
        order.setSuspendReason("Waiting for critical spare parts");
        orderMapper.updateById(order);

        // Clear all timeout Redis keys (same as SUSPENDED)
        clearTimeoutKeys(order.getId());

        recordProgress(order.getId(), fromStatus, OrderStatus.WAITING_PARTS.getCode(),
                order.getAssignedWorkerId(), UserRole.WORKER.getCode(),
                "Order paused: waiting for critical spare parts");

        log.info("Order {} entered WAITING_PARTS from {}", order.getOrderNo(), fromStatus);
    }

    // ==========================================================================
    // Private: Resume from WAITING_PARTS
    // ==========================================================================

    private void resumeFromWaitingParts(RepairOrder order) {
        String resumeTo = order.getPreviousStatus() != null
                ? order.getPreviousStatus() : OrderStatus.ACCEPTED.getCode();

        stateMachine.validateTransition(order.getStatus(), resumeTo);

        // Calculate pause duration and adjust SLA timestamps
        LocalDateTime suspendedAt = order.getSuspendedAt();
        long pauseSeconds = 0;
        if (suspendedAt != null) {
            pauseSeconds = java.time.Duration.between(suspendedAt, LocalDateTime.now()).getSeconds();
            int totalSuspended = (order.getTotalSuspendedSeconds() != null
                    ? order.getTotalSuspendedSeconds() : 0) + (int) pauseSeconds;
            order.setTotalSuspendedSeconds(totalSuspended);

            // Adjust the relevant SLA timestamp
            if (OrderStatus.ACCEPTED.getCode().equals(resumeTo)) {
                if (order.getAcceptedAt() != null) {
                    order.setAcceptedAt(order.getAcceptedAt().plusSeconds(pauseSeconds));
                }
            } else if (OrderStatus.VISITING.getCode().equals(resumeTo)
                    || OrderStatus.REWORKING.getCode().equals(resumeTo)) {
                if (order.getVisitAt() != null) {
                    order.setVisitAt(order.getVisitAt().plusSeconds(pauseSeconds));
                }
            }
        }

        order.setStatus(resumeTo);
        order.setPreviousStatus(null);
        order.setSuspendReason(null);
        order.setSuspendedAt(null);
        orderMapper.updateById(order);

        // Re-establish Redis timeout keys
        if (OrderStatus.ACCEPTED.getCode().equals(resumeTo) && order.getAcceptedAt() != null) {
            LocalDateTime visitDeadline = order.getAcceptedAt().plusHours(4);
            redisTemplate.opsForValue().set(
                    "timeout:visit:" + order.getId(),
                    visitDeadline.toString(),
                    java.time.Duration.ofHours(5));
        } else if ((OrderStatus.VISITING.getCode().equals(resumeTo)
                || OrderStatus.REWORKING.getCode().equals(resumeTo))
                && order.getVisitAt() != null) {
            LocalDateTime completeDeadline = order.getVisitAt().plusHours(48);
            redisTemplate.opsForValue().set(
                    "timeout:complete:" + order.getId(),
                    completeDeadline.toString(),
                    java.time.Duration.ofHours(49));
        }

        recordProgress(order.getId(), OrderStatus.WAITING_PARTS.getCode(), resumeTo,
                order.getAssignedWorkerId(), UserRole.WORKER.getCode(),
                String.format("Parts arrived — order resumed (paused %d sec). SLA adjusted.", pauseSeconds));

        log.info("Order {} resumed from WAITING_PARTS to {}, pauseSeconds={}",
                order.getOrderNo(), resumeTo, pauseSeconds);
    }

    // ==========================================================================
    // Private: Try to fulfill pending requisitions after stock increase
    // ==========================================================================

    private void tryFulfillPendingRequisitions(Long partId, Long communityId) {
        // Find all PENDING requisition items for this part
        List<SparePartRequisitionItem> pendingItems = requisitionItemMapper.selectList(
                new LambdaQueryWrapper<SparePartRequisitionItem>()
                        .eq(SparePartRequisitionItem::getPartId, partId)
                        .apply("issued_quantity < requested_quantity"));

        for (SparePartRequisitionItem item : pendingItems) {
            SparePartRequisition requisition = requisitionMapper.selectById(item.getRequisitionId());
            if (requisition == null || !RequisitionStatus.PENDING.getCode().equals(requisition.getStatus())) {
                continue;
            }

            RepairOrder order = orderMapper.selectById(requisition.getOrderId());
            if (order == null || !order.getCommunityId().equals(communityId)) {
                continue;
            }

            // Check current inventory
            SparePartInventory inventory = getInventory(partId, communityId);
            if (inventory == null) continue;

            int available = inventory.getQuantity() - inventory.getReservedQuantity();
            int deficit = item.getRequestedQuantity() - item.getIssuedQuantity();

            if (available >= deficit) {
                // Fulfill this item
                item.setIssuedQuantity(item.getRequestedQuantity());
                requisitionItemMapper.updateById(item);

                int before = inventory.getQuantity();
                inventory.setQuantity(before - deficit);
                inventoryMapper.updateById(inventory);

                logAudit(partId, inventory.getId(), requisition.getId(),
                        order.getId(), "ISSUE", -deficit,
                        before, before - deficit,
                        null, "Auto-issued after purchase arrival");
            }
        }

        // Check if any WAITING_PARTS orders can now be fully resumed
        List<RepairOrder> waitingOrders = orderMapper.selectList(
                new LambdaQueryWrapper<RepairOrder>()
                        .eq(RepairOrder::getStatus, OrderStatus.WAITING_PARTS.getCode())
                        .eq(RepairOrder::getCommunityId, communityId));

        for (RepairOrder order : waitingOrders) {
            if (canResumeOrder(order)) {
                // Update all pending requisitions for this order to ISSUED
                List<SparePartRequisition> orderReqs = requisitionMapper.selectList(
                        new LambdaQueryWrapper<SparePartRequisition>()
                                .eq(SparePartRequisition::getOrderId, order.getId())
                                .eq(SparePartRequisition::getStatus, RequisitionStatus.PENDING.getCode()));
                for (SparePartRequisition req : orderReqs) {
                    req.setStatus(RequisitionStatus.ISSUED.getCode());
                    requisitionMapper.updateById(req);
                }

                resumeFromWaitingParts(order);
            }
        }
    }

    /**
     * Check if all critical pending requisition items for an order have been fulfilled.
     */
    private boolean canResumeOrder(RepairOrder order) {
        List<SparePartRequisition> pendingReqs = requisitionMapper.selectList(
                new LambdaQueryWrapper<SparePartRequisition>()
                        .eq(SparePartRequisition::getOrderId, order.getId())
                        .eq(SparePartRequisition::getStatus, RequisitionStatus.PENDING.getCode()));

        for (SparePartRequisition req : pendingReqs) {
            List<SparePartRequisitionItem> items = requisitionItemMapper.selectList(
                    new LambdaQueryWrapper<SparePartRequisitionItem>()
                            .eq(SparePartRequisitionItem::getRequisitionId, req.getId()));
            for (SparePartRequisitionItem item : items) {
                if (item.getIssuedQuantity() < item.getRequestedQuantity()) {
                    SparePart part = sparePartMapper.selectById(item.getPartId());
                    if (part != null && part.getIsCritical() != null && part.getIsCritical() == 1) {
                        return false; // Still has unfulfilled critical parts
                    }
                }
            }
        }
        return true;
    }

    // ==========================================================================
    // Private: Duplicate requisition check
    // ==========================================================================

    private void checkDuplicateRequisition(SparePartRequisitionRequest request) {
        for (SparePartRequisitionRequest.Item item : request.getItems()) {
            // Find active requisitions for the same order containing this part
            List<SparePartRequisition> activeReqs = requisitionMapper.selectList(
                    new LambdaQueryWrapper<SparePartRequisition>()
                            .eq(SparePartRequisition::getOrderId, request.getOrderId())
                            .in(SparePartRequisition::getStatus,
                                    RequisitionStatus.PENDING.getCode(),
                                    RequisitionStatus.APPROVED.getCode(),
                                    RequisitionStatus.ISSUED.getCode()));

            // For rework second requisition, only check requisitions with the same reworkOrderId
            for (SparePartRequisition req : activeReqs) {
                // If this is a rework requisition, skip non-rework requisitions
                if (request.getReworkOrderId() != null && !request.getReworkOrderId().equals(req.getReworkOrderId())) {
                    continue;
                }
                // If this is NOT a rework requisition, skip rework requisitions
                if (request.getReworkOrderId() == null && req.getReworkOrderId() != null) {
                    continue;
                }

                long duplicateCount = requisitionItemMapper.selectCount(
                        new LambdaQueryWrapper<SparePartRequisitionItem>()
                                .eq(SparePartRequisitionItem::getRequisitionId, req.getId())
                                .eq(SparePartRequisitionItem::getPartId, item.getPartId()));
                if (duplicateCount > 0) {
                    SparePart part = sparePartMapper.selectById(item.getPartId());
                    String partName = part != null ? part.getName() : "ID:" + item.getPartId();
                    throw new BusinessException("Duplicate requisition: part '" + partName
                            + "' already has an active requisition for this order");
                }
            }
        }
    }

    // ==========================================================================
    // Private: Helpers
    // ==========================================================================

    private SparePartInventory getInventory(Long partId, Long communityId) {
        return inventoryMapper.selectOne(
                new LambdaQueryWrapper<SparePartInventory>()
                        .eq(SparePartInventory::getPartId, partId)
                        .eq(SparePartInventory::getCommunityId, communityId));
    }

    private SparePartInventory getOrCreateInventory(Long partId, Long communityId) {
        SparePartInventory inventory = getInventory(partId, communityId);
        if (inventory == null) {
            inventory = new SparePartInventory();
            inventory.setPartId(partId);
            inventory.setCommunityId(communityId);
            inventory.setQuantity(0);
            inventory.setReservedQuantity(0);
            inventoryMapper.insert(inventory);
        }
        return inventory;
    }

    private void createPurchaseRequest(SparePart part, Long communityId, int quantity,
                                       Long requisitionId, Long orderId) {
        PurchaseRequest pr = new PurchaseRequest();
        pr.setRequestNo(generatePurchaseNo());
        pr.setPartId(part.getId());
        pr.setCommunityId(communityId);
        pr.setQuantity(quantity);
        pr.setStatus(PurchaseStatus.PENDING.getCode());
        pr.setTriggerRequisitionId(requisitionId);
        pr.setTriggerOrderId(orderId);
        purchaseRequestMapper.insert(pr);

        log.info("Auto-created purchase request {} for part={}, qty={}, orderId={}",
                pr.getRequestNo(), part.getPartNo(), quantity, orderId);
    }

    private void clearTimeoutKeys(Long orderId) {
        redisTemplate.delete("timeout:accept:" + orderId);
        redisTemplate.delete("timeout:visit:" + orderId);
        redisTemplate.delete("timeout:complete:" + orderId);
        redisTemplate.delete("timeout:processed:ACCEPT_TIMEOUT:" + orderId);
        redisTemplate.delete("timeout:processed:VISIT_TIMEOUT:" + orderId);
        redisTemplate.delete("timeout:processed:COMPLETE_TIMEOUT:" + orderId);
    }

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

    private void logAudit(Long partId, Long inventoryId, Long requisitionId,
                          Long orderId, String action, int quantityChange,
                          Integer before, Integer after, Long operatorId, String remark) {
        SparePartAuditLog log = new SparePartAuditLog();
        log.setPartId(partId);
        log.setInventoryId(inventoryId);
        log.setRequisitionId(requisitionId);
        log.setOrderId(orderId);
        log.setAction(action);
        log.setQuantityChange(quantityChange);
        log.setBeforeQuantity(before);
        log.setAfterQuantity(after);
        log.setOperatorId(operatorId);
        log.setRemark(remark);
        auditLogMapper.insert(log);
    }

    private String generateRequisitionNo() {
        String prefix = "REQ";
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long seq = SEQ.incrementAndGet();
        return prefix + date + String.format("%06d", seq % 1000000);
    }

    private String generatePurchaseNo() {
        String prefix = "PUR";
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long seq = SEQ.incrementAndGet();
        return prefix + date + String.format("%06d", seq % 1000000);
    }
}
