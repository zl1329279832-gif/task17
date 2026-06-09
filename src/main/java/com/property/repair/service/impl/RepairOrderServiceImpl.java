package com.property.repair.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.property.repair.common.PageResult;
import com.property.repair.dto.*;
import com.property.repair.entity.*;
import com.property.repair.enums.*;
import com.property.repair.exception.BusinessException;
import com.property.repair.exception.DuplicateOrderException;
import com.property.repair.exception.InvalidStateTransitionException;
import com.property.repair.mapper.*;
import com.property.repair.service.AuditService;
import com.property.repair.service.DispatchStrategy;
import com.property.repair.service.RepairOrderService;
import com.property.repair.service.SparePartService;
import com.property.repair.statemachine.OrderStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RepairOrderServiceImpl extends ServiceImpl<RepairOrderMapper, RepairOrder>
        implements RepairOrderService {

    private final RepairOrderMapper orderMapper;
    private final DispatchRecordMapper dispatchRecordMapper;
    private final RepairProgressMapper progressMapper;
    private final ReviewMapper reviewMapper;
    private final ReworkOrderMapper reworkOrderMapper;
    private final UserMapper userMapper;
    private final AttachmentMapper attachmentMapper;
    private final AuditService auditService;
    private final DispatchStrategy dispatchStrategy;
    private final OrderStateMachine stateMachine;
    private final RedisTemplate<String, Object> redisTemplate;
    private final TimeoutEscalationMapper timeoutEscalationMapper;
    private final SparePartService sparePartService;
    private final SparePartRequisitionMapper sparePartRequisitionMapper;
    private final SparePartRequisitionItemMapper sparePartRequisitionItemMapper;
    private final SparePartMapper sparePartMapper;

    @Value("${repair.duplicate.window-hours:24}")
    private int duplicateWindowHours;

    // Simple order number generator
    private static final AtomicLong SEQ = new AtomicLong(0);

    // ==========================================================================
    // Order Submission
    // ==========================================================================

    @Override
    @Transactional
    public RepairOrderVO submitOrder(RepairOrderSubmitRequest request, Long ownerId) {
        // 1. Check for duplicate submissions
        checkDuplicateOrder(ownerId, request);

        // 2. Create order
        RepairOrder order = new RepairOrder();
        order.setOrderNo(generateOrderNo());
        order.setTitle(request.getTitle());
        order.setDescription(request.getDescription());
        order.setProblemType(request.getProblemType());
        order.setUrgency(request.getUrgency());
        order.setCommunityId(request.getCommunityId());
        order.setBuildingId(request.getBuildingId());
        order.setUnitId(request.getUnitId());
        order.setRoomNo(request.getRoomNo());
        order.setAddressDetail(request.getAddressDetail());
        order.setOwnerId(ownerId);
        order.setStatus(OrderStatus.PENDING.getCode());
        order.setSubmittedAt(LocalDateTime.now());
        orderMapper.insert(order);

        // 3. Link pre-uploaded attachments
        if (request.getAttachmentIds() != null && !request.getAttachmentIds().isEmpty()) {
            for (Long attId : request.getAttachmentIds()) {
                Attachment att = attachmentMapper.selectById(attId);
                if (att != null) {
                    att.setOrderId(order.getId());
                    attachmentMapper.updateById(att);
                }
            }
        }

        // 4. Record progress
        recordProgress(order.getId(), null, OrderStatus.PENDING.getCode(),
                ownerId, UserRole.OWNER.getCode(), "Order submitted");

        // 5. Audit log
        auditService.log(order.getId(), "SUBMIT_ORDER", ownerId, UserRole.OWNER.getCode(),
                null, Map.of("orderNo", order.getOrderNo()), null);

        // 6. Auto-dispatch
        tryAutoDispatch(order);

        return getOrderDetail(order.getId());
    }

    // ==========================================================================
    // Order Detail & Query
    // ==========================================================================

    @Override
    public RepairOrderVO getOrderDetail(Long orderId) {
        RepairOrder order = getById(orderId);
        if (order == null) {
            throw new BusinessException("Order not found");
        }
        return buildVO(order);
    }

    @Override
    public PageResult<RepairOrderVO> queryOrders(RepairOrderQueryRequest query) {
        Page<RepairOrder> page = new Page<>(query.getPage(), query.getSize());
        LambdaQueryWrapper<RepairOrder> wrapper = new LambdaQueryWrapper<>();

        wrapper.eq(query.getStatus() != null, RepairOrder::getStatus, query.getStatus())
               .eq(query.getProblemType() != null, RepairOrder::getProblemType, query.getProblemType())
               .eq(query.getCommunityId() != null, RepairOrder::getCommunityId, query.getCommunityId())
               .eq(query.getBuildingId() != null, RepairOrder::getBuildingId, query.getBuildingId())
               .eq(query.getOwnerId() != null, RepairOrder::getOwnerId, query.getOwnerId())
               .eq(query.getWorkerId() != null, RepairOrder::getAssignedWorkerId, query.getWorkerId())
               .and(StringUtils.hasText(query.getKeyword()), w ->
                       w.like(RepairOrder::getTitle, query.getKeyword())
                        .or().like(RepairOrder::getDescription, query.getKeyword())
                        .or().like(RepairOrder::getOrderNo, query.getKeyword()))
               .orderByDesc(RepairOrder::getSubmittedAt);

        IPage<RepairOrder> result = orderMapper.selectPage(page, wrapper);

        PageResult<RepairOrderVO> voPage = new PageResult<>();
        voPage.setRecords(result.getRecords().stream().map(this::buildVO).collect(Collectors.toList()));
        voPage.setTotal(result.getTotal());
        voPage.setSize(result.getSize());
        voPage.setCurrent(result.getCurrent());
        voPage.setPages(result.getPages());
        return voPage;
    }

    // ==========================================================================
    // Dispatch
    // ==========================================================================

    @Override
    @Transactional
    public void manualDispatch(Long orderId, ManualDispatchRequest request) {
        RepairOrder order = getById(orderId);
        assertNotNull(order);
        assertStatus(order, OrderStatus.PENDING, OrderStatus.TRANSFERRED);

        User worker = userMapper.selectById(request.getWorkerId());
        if (worker == null || !UserRole.WORKER.getCode().equals(worker.getRole())) {
            throw new BusinessException("Invalid worker");
        }

        doDispatch(order, worker, DispatchType.MANUAL, request.getReason(), null);
    }

    private void tryAutoDispatch(RepairOrder order) {
        Long workerId = dispatchStrategy.findBestWorker(order);
        if (workerId != null) {
            User worker = userMapper.selectById(workerId);
            doDispatch(order, worker, DispatchType.AUTO, "Auto-dispatched by system", null);
        } else {
            log.warn("No available worker for order={}, pending manual dispatch", order.getOrderNo());
        }
    }

    private void doDispatch(RepairOrder order, User worker, DispatchType type, String reason, Long fromWorkerId) {
        // Use Redis lock to prevent concurrent dispatch
        String lockKey = "dispatch:lock:" + order.getId();
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", java.time.Duration.ofSeconds(10));
        if (locked == null || !locked) {
            throw new BusinessException("Order is being dispatched, please try again");
        }

        try {
            String fromStatus = order.getStatus();
            int currentLoad = orderMapper.countActiveOrders(worker.getId());

            // Clean up previous dispatch round if re-dispatching
            if (order.getCurrentDispatchId() != null) {
                invalidatePreviousDispatch(order);
            }

            // Update order
            order.setAssignedWorkerId(worker.getId());
            if (order.getOriginalWorkerId() == null) {
                order.setOriginalWorkerId(worker.getId());
            }
            order.setStatus(OrderStatus.DISPATCHED.getCode());
            order.setAssignedAt(LocalDateTime.now());

            // Create new dispatch record with active=1
            DispatchRecord record = new DispatchRecord();
            record.setOrderId(order.getId());
            record.setWorkerId(worker.getId());
            record.setDispatchType(type.getCode());
            record.setFromWorkerId(fromWorkerId);
            record.setReason(reason);
            record.setLoadBefore(currentLoad);
            record.setActive(1);
            dispatchRecordMapper.insert(record);

            // Link order to this dispatch round
            order.setCurrentDispatchId(record.getId());
            orderMapper.updateById(order);

            // Record progress
            recordProgress(order.getId(), fromStatus, OrderStatus.DISPATCHED.getCode(),
                    worker.getId(), type.getCode(), reason);

            // Set accept timeout deadline in Redis
            redisTemplate.opsForValue().set(
                    "timeout:accept:" + order.getId(),
                    LocalDateTime.now().plusMinutes(30).toString(),
                    java.time.Duration.ofMinutes(35));

            auditService.log(order.getId(), "DISPATCH_ORDER", worker.getId(), type.getCode(),
                    Map.of("status", fromStatus),
                    Map.of("status", OrderStatus.DISPATCHED.getCode(),
                           "worker", worker.getRealName()),
                    null);
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    /**
     * Invalidate timeout state from a previous dispatch round.
     * Called when re-dispatching (manual dispatch, transfer, reject+re-dispatch).
     */
    private void invalidatePreviousDispatch(RepairOrder order) {
        Long oldDispatchId = order.getCurrentDispatchId();

        // 1. Deactivate old dispatch record
        DispatchRecord oldRecord = dispatchRecordMapper.selectById(oldDispatchId);
        if (oldRecord != null) {
            oldRecord.setActive(0);
            dispatchRecordMapper.updateById(oldRecord);
        }

        // 2. Mark unhandled escalation records for old dispatch as handled
        List<TimeoutEscalation> oldEscalations = timeoutEscalationMapper.selectList(
                new LambdaQueryWrapper<TimeoutEscalation>()
                        .eq(TimeoutEscalation::getOrderId, order.getId())
                        .eq(TimeoutEscalation::getDispatchId, oldDispatchId)
                        .eq(TimeoutEscalation::getHandled, 0));
        for (TimeoutEscalation esc : oldEscalations) {
            esc.setHandled(1);
            esc.setHandledAt(LocalDateTime.now());
            esc.setHandleRemark("Invalidated by re-dispatch (new dispatch round)");
            timeoutEscalationMapper.updateById(esc);
        }

        // 3. Clear all timeout Redis keys (both processed markers and deadline keys)
        redisTemplate.delete("timeout:processed:ACCEPT_TIMEOUT:" + order.getId());
        redisTemplate.delete("timeout:processed:VISIT_TIMEOUT:" + order.getId());
        redisTemplate.delete("timeout:processed:COMPLETE_TIMEOUT:" + order.getId());
        redisTemplate.delete("timeout:accept:" + order.getId());
        redisTemplate.delete("timeout:visit:" + order.getId());
        redisTemplate.delete("timeout:complete:" + order.getId());

        log.info("Invalidated previous dispatch round: orderId={}, oldDispatchId={}",
                order.getId(), oldDispatchId);
    }

    // ==========================================================================
    // Worker Actions
    // ==========================================================================

    @Override
    @Transactional
    public void acceptOrder(Long orderId, Long workerId) {
        RepairOrder order = getById(orderId);
        assertWorkerOwnership(order, workerId);
        stateMachine.validateTransition(order.getStatus(), OrderStatus.ACCEPTED.getCode());

        String fromStatus = order.getStatus();
        order.setStatus(OrderStatus.ACCEPTED.getCode());
        order.setAcceptedAt(LocalDateTime.now());
        orderMapper.updateById(order);

        // Clear accept timeout
        redisTemplate.delete("timeout:accept:" + orderId);

        // Set visit timeout
        redisTemplate.opsForValue().set(
                "timeout:visit:" + orderId,
                LocalDateTime.now().plusHours(4).toString(),
                java.time.Duration.ofHours(5));

        recordProgress(orderId, fromStatus, OrderStatus.ACCEPTED.getCode(),
                workerId, UserRole.WORKER.getCode(), "Worker accepted the order");

        auditService.log(orderId, "ACCEPT_ORDER", workerId, UserRole.WORKER.getCode(),
                Map.of("status", fromStatus), Map.of("status", OrderStatus.ACCEPTED.getCode()), null);
    }

    @Override
    @Transactional
    public void rejectOrder(Long orderId, Long workerId, String reason) {
        RepairOrder order = getById(orderId);
        assertWorkerOwnership(order, workerId);
        assertStatus(order, OrderStatus.DISPATCHED);

        order.setStatus(OrderStatus.PENDING.getCode());
        order.setAssignedWorkerId(null);
        orderMapper.updateById(order);

        // Clear accept timeout
        redisTemplate.delete("timeout:accept:" + orderId);

        recordProgress(orderId, OrderStatus.DISPATCHED.getCode(), OrderStatus.PENDING.getCode(),
                workerId, UserRole.WORKER.getCode(), "Worker rejected: " + reason);

        // Try auto re-dispatch
        tryAutoDispatch(order);
    }

    @Override
    @Transactional
    public void transferOrder(Long orderId, TransferRequest request) {
        RepairOrder order = getById(orderId);
        assertNotNull(order);

        // Only current assigned worker can transfer
        Long currentWorkerId = order.getAssignedWorkerId();
        if (currentWorkerId == null) {
            throw new BusinessException("No worker assigned to transfer from");
        }

        assertStatus(order, OrderStatus.ACCEPTED, OrderStatus.VISITING);

        User targetWorker = userMapper.selectById(request.getTargetWorkerId());
        if (targetWorker == null || !UserRole.WORKER.getCode().equals(targetWorker.getRole())) {
            throw new BusinessException("Invalid target worker");
        }

        if (targetWorker.getId().equals(currentWorkerId)) {
            throw new BusinessException("Cannot transfer to the same worker");
        }

        String fromStatus = order.getStatus();

        // Record the transfer: first set TRANSFERRED, then re-dispatch
        order.setStatus(OrderStatus.TRANSFERRED.getCode());
        order.setPreviousStatus(fromStatus);
        orderMapper.updateById(order);

        recordProgress(orderId, fromStatus, OrderStatus.TRANSFERRED.getCode(),
                currentWorkerId, UserRole.WORKER.getCode(),
                "Transferred to " + targetWorker.getRealName() + ": " + request.getReason());

        // Dispatch to new worker
        doDispatch(order, targetWorker, DispatchType.TRANSFER, request.getReason(), currentWorkerId);
    }

    @Override
    @Transactional
    public void visitOrder(Long orderId, Long workerId) {
        RepairOrder order = getById(orderId);
        assertWorkerOwnership(order, workerId);
        stateMachine.validateTransition(order.getStatus(), OrderStatus.VISITING.getCode());

        String fromStatus = order.getStatus();
        order.setStatus(OrderStatus.VISITING.getCode());
        order.setVisitAt(LocalDateTime.now());
        orderMapper.updateById(order);

        // Clear visit timeout
        redisTemplate.delete("timeout:visit:" + orderId);

        // Set complete timeout
        redisTemplate.opsForValue().set(
                "timeout:complete:" + orderId,
                LocalDateTime.now().plusHours(48).toString(),
                java.time.Duration.ofHours(49));

        recordProgress(orderId, fromStatus, OrderStatus.VISITING.getCode(),
                workerId, UserRole.WORKER.getCode(), "Worker arrived on-site");
    }

    @Override
    @Transactional
    public void suspendOrder(Long orderId, SuspendRequest request) {
        RepairOrder order = getById(orderId);
        assertNotNull(order);
        assertStatus(order, OrderStatus.ACCEPTED, OrderStatus.VISITING, OrderStatus.REWORKING);

        String fromStatus = order.getStatus();
        order.setPreviousStatus(fromStatus);
        order.setStatus(OrderStatus.SUSPENDED.getCode());
        order.setSuspendReason(request.getReason());
        order.setSuspendedAt(LocalDateTime.now());
        orderMapper.updateById(order);

        // Clear all active timeout Redis keys so timeout task won't fire
        redisTemplate.delete("timeout:accept:" + orderId);
        redisTemplate.delete("timeout:visit:" + orderId);
        redisTemplate.delete("timeout:complete:" + orderId);
        // Also clear processed markers so timeout can re-fire correctly after resume
        redisTemplate.delete("timeout:processed:ACCEPT_TIMEOUT:" + orderId);
        redisTemplate.delete("timeout:processed:VISIT_TIMEOUT:" + orderId);
        redisTemplate.delete("timeout:processed:COMPLETE_TIMEOUT:" + orderId);

        recordProgress(orderId, fromStatus, OrderStatus.SUSPENDED.getCode(),
                order.getAssignedWorkerId(), UserRole.WORKER.getCode(),
                "Suspended: " + request.getReason());

        auditService.log(orderId, "SUSPEND_ORDER", order.getAssignedWorkerId(),
                UserRole.WORKER.getCode(),
                Map.of("status", fromStatus),
                Map.of("status", OrderStatus.SUSPENDED.getCode(),
                       "reason", request.getReason()),
                null);
    }

    @Override
    @Transactional
    public void resumeOrder(Long orderId, Long workerId) {
        RepairOrder order = getById(orderId);
        assertWorkerOwnership(order, workerId);
        assertStatus(order, OrderStatus.SUSPENDED);

        String resumeTo = order.getPreviousStatus() != null
                ? order.getPreviousStatus() : OrderStatus.ACCEPTED.getCode();

        // Calculate pause duration and adjust SLA timestamps
        LocalDateTime suspendedAt = order.getSuspendedAt();
        long pauseSeconds = 0;
        if (suspendedAt != null) {
            pauseSeconds = java.time.Duration.between(suspendedAt, LocalDateTime.now()).getSeconds();
            int totalSuspended = (order.getTotalSuspendedSeconds() != null
                    ? order.getTotalSuspendedSeconds() : 0) + (int) pauseSeconds;
            order.setTotalSuspendedSeconds(totalSuspended);

            // Adjust the relevant timestamp based on the state we're resuming to
            if (OrderStatus.ACCEPTED.getCode().equals(resumeTo)) {
                if (order.getAcceptedAt() != null) {
                    order.setAcceptedAt(order.getAcceptedAt().plusSeconds(pauseSeconds));
                }
            } else if (OrderStatus.VISITING.getCode().equals(resumeTo)) {
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

        // Re-establish Redis timeout keys with adjusted deadlines
        if (OrderStatus.ACCEPTED.getCode().equals(resumeTo) && order.getAcceptedAt() != null) {
            LocalDateTime visitDeadline = order.getAcceptedAt().plusHours(4);
            redisTemplate.opsForValue().set(
                    "timeout:visit:" + orderId,
                    visitDeadline.toString(),
                    java.time.Duration.ofHours(5));
        } else if (OrderStatus.VISITING.getCode().equals(resumeTo) && order.getVisitAt() != null) {
            LocalDateTime completeDeadline = order.getVisitAt().plusHours(48);
            redisTemplate.opsForValue().set(
                    "timeout:complete:" + orderId,
                    completeDeadline.toString(),
                    java.time.Duration.ofHours(49));
        }

        recordProgress(orderId, OrderStatus.SUSPENDED.getCode(), resumeTo,
                workerId, UserRole.WORKER.getCode(),
                String.format("Order resumed (paused %d sec). SLA deadline adjusted.", pauseSeconds));

        auditService.log(orderId, "RESUME_ORDER", workerId, UserRole.WORKER.getCode(),
                Map.of("status", OrderStatus.SUSPENDED.getCode()),
                Map.of("status", resumeTo, "pauseSeconds", pauseSeconds),
                null);
    }

    @Override
    @Transactional
    public void completeOrder(Long orderId, CompleteRequest request) {
        RepairOrder order = getById(orderId);
        assertNotNull(order);
        assertStatus(order, OrderStatus.VISITING, OrderStatus.REWORKING);

        Long workerId = order.getAssignedWorkerId();
        String fromStatus = order.getStatus();

        order.setStatus(OrderStatus.COMPLETED.getCode());
        order.setCompletedAt(LocalDateTime.now());
        orderMapper.updateById(order);

        // Clear complete timeout
        redisTemplate.delete("timeout:complete:" + orderId);

        // Mark all issued spare parts as consumed
        sparePartService.consumePartsForOrder(orderId);

        recordProgress(orderId, fromStatus, OrderStatus.COMPLETED.getCode(),
                workerId, UserRole.WORKER.getCode(),
                "Repair completed: " + request.getRemark());

        // Link evidence attachments
        if (request.getEvidenceAttachmentIds() != null) {
            for (Long attId : request.getEvidenceAttachmentIds()) {
                Attachment att = attachmentMapper.selectById(attId);
                if (att != null) {
                    att.setOrderId(orderId);
                    att.setStage(AttachmentStage.COMPLETE.getCode());
                    attachmentMapper.updateById(att);
                }
            }
        }

        auditService.log(orderId, "COMPLETE_ORDER", workerId, UserRole.WORKER.getCode(),
                Map.of("status", fromStatus),
                Map.of("status", OrderStatus.COMPLETED.getCode(), "remark", request.getRemark()), null);
    }

    // ==========================================================================
    // Owner Actions
    // ==========================================================================

    @Override
    @Transactional
    public void reviewOrder(Long orderId, ReviewRequest request) {
        RepairOrder order = getById(orderId);
        assertNotNull(order);
        assertStatus(order, OrderStatus.COMPLETED);

        Long ownerId = order.getOwnerId();

        // Check if already reviewed
        long reviewCount = reviewMapper.selectCount(
                new LambdaQueryWrapper<Review>().eq(Review::getOrderId, orderId));
        if (reviewCount > 0) {
            throw new BusinessException("Order already reviewed");
        }

        // Create review
        Review review = new Review();
        review.setOrderId(orderId);
        review.setReviewerId(ownerId);
        review.setRating(request.getRating());
        review.setContent(request.getContent());
        reviewMapper.insert(review);

        // Update order status
        order.setStatus(OrderStatus.REVIEWED.getCode());
        orderMapper.updateById(order);

        recordProgress(orderId, OrderStatus.COMPLETED.getCode(), OrderStatus.REVIEWED.getCode(),
                ownerId, UserRole.OWNER.getCode(),
                "Reviewed with rating: " + request.getRating());
    }

    @Override
    @Transactional
    public void requestRework(Long orderId, ReworkRequest request) {
        RepairOrder order = getById(orderId);
        assertNotNull(order);
        assertStatus(order, OrderStatus.COMPLETED);

        Long ownerId = order.getOwnerId();

        // Create rework order
        ReworkOrder rework = new ReworkOrder();
        rework.setOrderId(orderId);
        rework.setReworkNo(generateReworkNo());
        rework.setReason(request.getReason());
        rework.setDescription(request.getDescription());
        rework.setAssignedWorkerId(order.getAssignedWorkerId());
        rework.setStatus("PENDING");
        rework.setRequesterId(ownerId);
        reworkOrderMapper.insert(rework);

        // Invalidate old review (soft delete via @TableLogic)
        Review existingReview = reviewMapper.selectOne(
                new LambdaQueryWrapper<Review>()
                        .eq(Review::getOrderId, orderId));
        if (existingReview != null) {
            existingReview.setDeleted(1);
            reviewMapper.updateById(existingReview);
        }

        // Update main order to REWORKING with timestamp cleanup
        String fromStatus = order.getStatus();
        order.setStatus(OrderStatus.REWORKING.getCode());
        order.setVisitAt(null);
        order.setCompletedAt(null);
        order.setCurrentDispatchId(null);
        orderMapper.updateById(order);

        // Clear all timeout Redis keys and processed markers
        redisTemplate.delete("timeout:processed:ACCEPT_TIMEOUT:" + orderId);
        redisTemplate.delete("timeout:processed:VISIT_TIMEOUT:" + orderId);
        redisTemplate.delete("timeout:processed:COMPLETE_TIMEOUT:" + orderId);
        redisTemplate.delete("timeout:accept:" + orderId);
        redisTemplate.delete("timeout:visit:" + orderId);
        redisTemplate.delete("timeout:complete:" + orderId);

        recordProgress(orderId, fromStatus, OrderStatus.REWORKING.getCode(),
                ownerId, UserRole.OWNER.getCode(),
                "Rework requested: " + request.getReason());

        auditService.log(orderId, "REQUEST_REWORK", ownerId, UserRole.OWNER.getCode(),
                Map.of("status", fromStatus),
                Map.of("status", OrderStatus.REWORKING.getCode(),
                       "reworkNo", rework.getReworkNo()),
                null);
    }

    @Override
    @Transactional
    public void confirmOrder(Long orderId, Long ownerId) {
        RepairOrder order = getById(orderId);
        assertNotNull(order);
        if (!order.getOwnerId().equals(ownerId)) {
            throw new BusinessException(403, "Only the order owner can confirm");
        }
        assertStatus(order, OrderStatus.COMPLETED);

        order.setStatus(OrderStatus.REVIEWED.getCode());
        orderMapper.updateById(order);

        recordProgress(orderId, OrderStatus.COMPLETED.getCode(), OrderStatus.REVIEWED.getCode(),
                ownerId, UserRole.OWNER.getCode(), "Owner confirmed completion");
    }

    @Override
    @Transactional
    public void cancelOrder(Long orderId, Long ownerId) {
        RepairOrder order = getById(orderId);
        assertNotNull(order);
        if (!order.getOwnerId().equals(ownerId)) {
            throw new BusinessException(403, "Only the order owner can cancel");
        }

        // Can only cancel if not yet being worked on
        OrderStatus status = OrderStatus.valueOf(order.getStatus());
        if (status.isActive()) {
            throw new BusinessException("Cannot cancel an order that is being processed");
        }

        String fromStatus = order.getStatus();
        order.setStatus(OrderStatus.CANCELLED.getCode());
        orderMapper.updateById(order);

        recordProgress(orderId, fromStatus, OrderStatus.CANCELLED.getCode(),
                ownerId, UserRole.OWNER.getCode(), "Order cancelled by owner");
    }

    // ==========================================================================
    // Admin Actions
    // ==========================================================================

    @Override
    @Transactional
    public void closeOrder(Long orderId, String reason) {
        RepairOrder order = getById(orderId);
        assertNotNull(order);

        String fromStatus = order.getStatus();
        order.setStatus(OrderStatus.CLOSED.getCode());
        orderMapper.updateById(order);

        recordProgress(orderId, fromStatus, OrderStatus.CLOSED.getCode(),
                0L, UserRole.ADMIN.getCode(), "Closed by admin: " + reason);
    }

    @Override
    @Transactional
    public void mergeDuplicateOrder(Long duplicateOrderId, Long parentOrderId) {
        RepairOrder duplicate = getById(duplicateOrderId);
        RepairOrder parent = getById(parentOrderId);
        assertNotNull(duplicate);
        assertNotNull(parent);

        duplicate.setParentOrderId(parentOrderId);
        duplicate.setIsDuplicate(1);
        duplicate.setDuplicateNote("Merged into " + parent.getOrderNo());
        duplicate.setStatus(OrderStatus.CLOSED.getCode());
        orderMapper.updateById(duplicate);

        recordProgress(duplicateOrderId, duplicate.getStatus(), OrderStatus.CLOSED.getCode(),
                0L, UserRole.ADMIN.getCode(),
                "Merged as duplicate into order " + parent.getOrderNo());
    }

    // ==========================================================================
    // Private Helpers
    // ==========================================================================

    private void checkDuplicateOrder(Long ownerId, RepairOrderSubmitRequest request) {
        LocalDateTime since = LocalDateTime.now().minusHours(duplicateWindowHours);
        List<RepairOrder> duplicates = orderMapper.findPotentialDuplicates(
                ownerId, request.getCommunityId(), request.getBuildingId(),
                request.getProblemType(), since);

        if (!duplicates.isEmpty()) {
            RepairOrder existing = duplicates.get(0);
            // Instead of throwing, log a warning — the caller can decide to merge later
            log.warn("Potential duplicate detected: owner={}, existing order={}, new title={}",
                    ownerId, existing.getOrderNo(), request.getTitle());
            // We still allow submission but flag it
        }
    }

    private String generateOrderNo() {
        String prefix = "RO";
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long seq = SEQ.incrementAndGet();
        return prefix + date + String.format("%06d", seq % 1000000);
    }

    private String generateReworkNo() {
        String prefix = "RW";
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long seq = SEQ.incrementAndGet();
        return prefix + date + String.format("%06d", seq % 1000000);
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

    private RepairOrderVO buildVO(RepairOrder order) {
        RepairOrderVO vo = new RepairOrderVO();
        // Basic fields
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setTitle(order.getTitle());
        vo.setDescription(order.getDescription());
        vo.setProblemType(order.getProblemType());
        vo.setUrgency(order.getUrgency());
        vo.setCommunityId(order.getCommunityId());
        vo.setBuildingId(order.getBuildingId());
        vo.setUnitId(order.getUnitId());
        vo.setRoomNo(order.getRoomNo());
        vo.setAddressDetail(order.getAddressDetail());
        vo.setOwnerId(order.getOwnerId());
        vo.setAssignedWorkerId(order.getAssignedWorkerId());
        vo.setStatus(order.getStatus());
        vo.setSuspendReason(order.getSuspendReason());
        vo.setSuspendedAt(order.getSuspendedAt());
        vo.setTotalSuspendedSeconds(order.getTotalSuspendedSeconds());
        vo.setCurrentDispatchId(order.getCurrentDispatchId());
        vo.setSubmittedAt(order.getSubmittedAt());
        vo.setAssignedAt(order.getAssignedAt());
        vo.setAcceptedAt(order.getAcceptedAt());
        vo.setVisitAt(order.getVisitAt());
        vo.setCompletedAt(order.getCompletedAt());
        vo.setParentOrderId(order.getParentOrderId());
        vo.setIsDuplicate(order.getIsDuplicate());

        // Enrich with names
        User owner = userMapper.selectById(order.getOwnerId());
        if (owner != null) {
            vo.setOwnerName(owner.getRealName());
            vo.setOwnerPhone(owner.getPhone());
        }
        if (order.getAssignedWorkerId() != null) {
            User worker = userMapper.selectById(order.getAssignedWorkerId());
            if (worker != null) {
                vo.setAssignedWorkerName(worker.getRealName());
                vo.setAssignedWorkerPhone(worker.getPhone());
            }
        }

        // Attachments
        List<Attachment> attachments = attachmentMapper.selectList(
                new LambdaQueryWrapper<Attachment>().eq(Attachment::getOrderId, order.getId()));
        vo.setAttachments(attachments.stream().map(a -> {
            RepairOrderVO.AttachmentVO av = new RepairOrderVO.AttachmentVO();
            av.setId(a.getId());
            av.setFileName(a.getFileName());
            av.setFileType(a.getFileType());
            av.setFileSize(a.getFileSize());
            av.setFileUrl(a.getFileUrl());
            av.setStage(a.getStage());
            av.setCreatedAt(a.getCreatedAt());
            return av;
        }).collect(Collectors.toList()));

        // Progress timeline
        List<RepairProgress> progressList = progressMapper.selectList(
                new LambdaQueryWrapper<RepairProgress>()
                        .eq(RepairProgress::getOrderId, order.getId())
                        .orderByAsc(RepairProgress::getCreatedAt));
        vo.setProgressTimeline(progressList.stream().map(p -> {
            RepairOrderVO.ProgressVO pv = new RepairOrderVO.ProgressVO();
            pv.setId(p.getId());
            pv.setFromStatus(p.getFromStatus());
            pv.setToStatus(p.getToStatus());
            pv.setOperatorId(p.getOperatorId());
            pv.setOperatorRole(p.getOperatorRole());
            pv.setRemark(p.getRemark());
            pv.setEvidenceUrls(p.getEvidenceUrls());
            pv.setCreatedAt(p.getCreatedAt());
            User op = userMapper.selectById(p.getOperatorId());
            if (op != null) pv.setOperatorName(op.getRealName());
            return pv;
        }).collect(Collectors.toList()));

        // Review
        Review review = reviewMapper.selectOne(
                new LambdaQueryWrapper<Review>().eq(Review::getOrderId, order.getId()));
        if (review != null) {
            RepairOrderVO.ReviewVO rv = new RepairOrderVO.ReviewVO();
            rv.setId(review.getId());
            rv.setRating(review.getRating());
            rv.setContent(review.getContent());
            rv.setReply(review.getReply());
            rv.setReplyAt(review.getReplyAt());
            rv.setCreatedAt(review.getCreatedAt());
            vo.setReview(rv);
        }

        // Rework orders
        List<ReworkOrder> reworkList = reworkOrderMapper.selectList(
                new LambdaQueryWrapper<ReworkOrder>()
                        .eq(ReworkOrder::getOrderId, order.getId())
                        .orderByDesc(ReworkOrder::getCreatedAt));
        vo.setReworkOrders(reworkList.stream().map(r -> {
            RepairOrderVO.ReworkOrderVO rov = new RepairOrderVO.ReworkOrderVO();
            rov.setId(r.getId());
            rov.setReworkNo(r.getReworkNo());
            rov.setReason(r.getReason());
            rov.setDescription(r.getDescription());
            rov.setStatus(r.getStatus());
            rov.setAssignedWorkerId(r.getAssignedWorkerId());
            rov.setCompletedAt(r.getCompletedAt());
            rov.setCreatedAt(r.getCreatedAt());
            if (r.getAssignedWorkerId() != null) {
                User w = userMapper.selectById(r.getAssignedWorkerId());
                if (w != null) rov.setAssignedWorkerName(w.getRealName());
            }
            return rov;
        }).collect(Collectors.toList()));

        // Spare part requisitions
        List<SparePartRequisition> reqList = sparePartRequisitionMapper.selectList(
                new LambdaQueryWrapper<SparePartRequisition>()
                        .eq(SparePartRequisition::getOrderId, order.getId())
                        .orderByDesc(SparePartRequisition::getCreatedAt));
        vo.setRequisitions(reqList.stream().map(req -> {
            RepairOrderVO.RequisitionVO rv = new RepairOrderVO.RequisitionVO();
            rv.setId(req.getId());
            rv.setRequisitionNo(req.getRequisitionNo());
            rv.setStatus(req.getStatus());
            rv.setReworkOrderId(req.getReworkOrderId());
            rv.setCreatedAt(req.getCreatedAt());
            List<SparePartRequisitionItem> items = sparePartRequisitionItemMapper.selectList(
                    new LambdaQueryWrapper<SparePartRequisitionItem>()
                            .eq(SparePartRequisitionItem::getRequisitionId, req.getId()));
            rv.setItems(items.stream().map(item -> {
                RepairOrderVO.RequisitionItemVO iv = new RepairOrderVO.RequisitionItemVO();
                iv.setPartId(item.getPartId());
                iv.setRequestedQuantity(item.getRequestedQuantity());
                iv.setIssuedQuantity(item.getIssuedQuantity());
                iv.setConsumedQuantity(item.getConsumedQuantity());
                iv.setReturnedQuantity(item.getReturnedQuantity());
                SparePart part = sparePartMapper.selectById(item.getPartId());
                if (part != null) {
                    iv.setPartName(part.getName());
                    iv.setPartNo(part.getPartNo());
                }
                return iv;
            }).collect(Collectors.toList()));
            return rv;
        }).collect(Collectors.toList()));

        return vo;
    }

    private void assertNotNull(RepairOrder order) {
        if (order == null) {
            throw new BusinessException("Order not found");
        }
    }

    private void assertStatus(RepairOrder order, OrderStatus... allowed) {
        for (OrderStatus s : allowed) {
            if (s.getCode().equals(order.getStatus())) return;
        }
        throw new InvalidStateTransitionException(order.getStatus(),
                "Expected one of: " + java.util.Arrays.stream(allowed)
                        .map(OrderStatus::getCode).collect(Collectors.joining(", ")));
    }

    private void assertWorkerOwnership(RepairOrder order, Long workerId) {
        assertNotNull(order);
        if (!workerId.equals(order.getAssignedWorkerId())) {
            throw new BusinessException(403, "You are not the assigned worker for this order");
        }
    }
}
