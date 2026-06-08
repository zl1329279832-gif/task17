package com.property.repair.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.property.repair.common.constants.RepairConstants;
import com.property.repair.common.enums.OrderEvent;
import com.property.repair.common.enums.OrderStatus;
import com.property.repair.common.enums.RoleType;
import com.property.repair.common.exception.BusinessException;
import com.property.repair.common.result.PageResult;
import com.property.repair.common.result.Result;
import com.property.repair.dto.request.RepairOrderCreateRequest;
import com.property.repair.dto.request.RepairOrderQueryRequest;
import com.property.repair.dto.request.SuspendRequest;
import com.property.repair.dto.response.RepairOrderDetailResponse;
import com.property.repair.dto.response.RepairOrderListResponse;
import com.property.repair.entity.*;
import com.property.repair.mapper.*;
import com.property.repair.security.LoginUser;
import com.property.repair.security.SecurityUtils;
import com.property.repair.service.DispatchService;
import com.property.repair.service.DuplicateDetectionService;
import com.property.repair.service.RepairOrderService;
import com.property.repair.statemachine.OrderStateMachine;
import com.property.repair.statemachine.StateChangeListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Transactional
public class RepairOrderServiceImpl implements RepairOrderService {

    private final RepairOrderMapper repairOrderMapper;
    private final OrderStateMachine orderStateMachine;
    private final StateChangeListener stateChangeListener;
    private final DispatchService dispatchService;
    private final DuplicateDetectionService duplicateDetectionService;
    private final AttachmentMapper attachmentMapper;
    private final CommunityMapper communityMapper;
    private final BuildingMapper buildingMapper;
    private final CategoryMapper categoryMapper;
    private final SysUserMapper sysUserMapper;
    private final RepairProgressMapper repairProgressMapper;
    private final EvaluationMapper evaluationMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public RepairOrderServiceImpl(RepairOrderMapper repairOrderMapper,
                                  OrderStateMachine orderStateMachine,
                                  StateChangeListener stateChangeListener,
                                  DispatchService dispatchService,
                                  DuplicateDetectionService duplicateDetectionService,
                                  AttachmentMapper attachmentMapper,
                                  CommunityMapper communityMapper,
                                  BuildingMapper buildingMapper,
                                  CategoryMapper categoryMapper,
                                  SysUserMapper sysUserMapper,
                                  RepairProgressMapper repairProgressMapper,
                                  EvaluationMapper evaluationMapper,
                                  StringRedisTemplate stringRedisTemplate) {
        this.repairOrderMapper = repairOrderMapper;
        this.orderStateMachine = orderStateMachine;
        this.stateChangeListener = stateChangeListener;
        this.dispatchService = dispatchService;
        this.duplicateDetectionService = duplicateDetectionService;
        this.attachmentMapper = attachmentMapper;
        this.communityMapper = communityMapper;
        this.buildingMapper = buildingMapper;
        this.categoryMapper = categoryMapper;
        this.sysUserMapper = sysUserMapper;
        this.repairProgressMapper = repairProgressMapper;
        this.evaluationMapper = evaluationMapper;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public Result<?> createOrder(RepairOrderCreateRequest request) {
        LoginUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            throw new BusinessException(401, "用户未登录");
        }

        Community community = communityMapper.selectById(request.getCommunityId());
        if (community == null) {
            throw new BusinessException("小区不存在");
        }

        Building building = buildingMapper.selectById(request.getBuildingId());
        if (building == null || !building.getCommunityId().equals(request.getCommunityId())) {
            throw new BusinessException("楼栋不存在或不属于该小区");
        }

        Category category = categoryMapper.selectById(request.getCategoryId());
        if (category == null) {
            throw new BusinessException("报修类型不存在");
        }

        if (!Boolean.TRUE.equals(request.getForceCreate())) {
            var duplicateCheck = duplicateDetectionService.checkDuplicate(
                    request.getCommunityId(), request.getBuildingId(), request.getCategoryId());
            if (duplicateCheck.isDuplicate()) {
                return Result.error(409, "存在疑似重复报修单，请确认后强制提交");
            }
        }

        String orderNo = generateOrderNo();

        RepairOrder order = new RepairOrder();
        order.setOrderNo(orderNo);
        order.setOwnerId(currentUser.getUserId());
        order.setCommunityId(request.getCommunityId());
        order.setBuildingId(request.getBuildingId());
        order.setUnitNumber(request.getUnitNumber());
        order.setCategoryId(request.getCategoryId());
        order.setTitle(request.getTitle());
        order.setDescription(request.getDescription());
        order.setUrgency(request.getUrgency());
        order.setExpectedTime(request.getExpectedTime());
        order.setStatus(OrderStatus.PENDING);
        order.setReworkCount(0);
        order.setVersion(0);
        repairOrderMapper.insert(order);

        if (request.getAttachmentIds() != null && !request.getAttachmentIds().isEmpty()) {
            for (Long attachmentId : request.getAttachmentIds()) {
                Attachment attachment = attachmentMapper.selectById(attachmentId);
                if (attachment != null) {
                    attachment.setOrderId(order.getId());
                    attachmentMapper.updateById(attachment);
                }
            }
        }

        try {
            dispatchService.autoDispatch(order);
        } catch (Exception e) {
            // Auto dispatch failure is non-critical; order stays PENDING
        }

        return Result.success("报修单创建成功", order);
    }

    @Override
    @Transactional(readOnly = true)
    public Result<RepairOrderDetailResponse> getOrderDetail(Long id) {
        RepairOrder order = repairOrderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException("报修单不存在");
        }

        applyDataScopeCheck(order);
        RepairOrderDetailResponse response = buildDetailResponse(order);
        return Result.success(response);
    }

    @Override
    @Transactional(readOnly = true)
    public Result<PageResult<RepairOrderListResponse>> getOrderList(RepairOrderQueryRequest request) {
        LambdaQueryWrapper<RepairOrder> wrapper = new LambdaQueryWrapper<>();

        if (request.getCommunityId() != null) {
            wrapper.eq(RepairOrder::getCommunityId, request.getCommunityId());
        }
        if (request.getBuildingId() != null) {
            wrapper.eq(RepairOrder::getBuildingId, request.getBuildingId());
        }
        if (request.getCategoryId() != null) {
            wrapper.eq(RepairOrder::getCategoryId, request.getCategoryId());
        }
        if (StringUtils.hasText(request.getStatus())) {
            wrapper.eq(RepairOrder::getStatus, OrderStatus.valueOf(request.getStatus()));
        }
        if (request.getWorkerId() != null) {
            wrapper.eq(RepairOrder::getCurrentWorkerId, request.getWorkerId());
        }
        if (request.getOwnerId() != null) {
            wrapper.eq(RepairOrder::getOwnerId, request.getOwnerId());
        }
        if (request.getUrgency() != null) {
            wrapper.eq(RepairOrder::getUrgency, request.getUrgency());
        }
        if (StringUtils.hasText(request.getKeyword())) {
            wrapper.and(w -> w.like(RepairOrder::getTitle, request.getKeyword())
                    .or().like(RepairOrder::getDescription, request.getKeyword())
                    .or().like(RepairOrder::getOrderNo, request.getKeyword()));
        }

        applyDataScope(wrapper);
        wrapper.orderByDesc(RepairOrder::getCreatedAt);

        int pageNum = request.getPageNum() != null ? request.getPageNum() : 1;
        int pageSize = request.getPageSize() != null ? request.getPageSize() : RepairConstants.DEFAULT_PAGE_SIZE;
        if (pageSize > RepairConstants.MAX_PAGE_SIZE) {
            pageSize = RepairConstants.MAX_PAGE_SIZE;
        }

        IPage<RepairOrder> page = repairOrderMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        List<RepairOrderListResponse> records = page.getRecords().stream()
                .map(this::convertToListResponse)
                .collect(Collectors.toList());

        PageResult<RepairOrderListResponse> pageResult = new PageResult<>();
        pageResult.setRecords(records);
        pageResult.setTotal(page.getTotal());
        pageResult.setPageNum((int) page.getCurrent());
        pageResult.setPageSize((int) page.getSize());
        pageResult.setPages((int) page.getPages());

        return Result.success(pageResult);
    }

    @Override
    public Result<?> performAction(Long orderId, OrderEvent event, String remark) {
        LoginUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            throw new BusinessException(401, "用户未登录");
        }

        RepairOrder order = repairOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("报修单不存在");
        }

        OrderStatus fromStatus = order.getStatus();
        OrderStatus toStatus = orderStateMachine.fire(order, event, currentUser.getUserId(), currentUser.getRole());

        order.setStatus(toStatus);
        updateTimeFields(order, event);

        int rows = repairOrderMapper.updateById(order);
        if (rows == 0) {
            throw new BusinessException("操作失败，数据已被修改，请刷新后重试");
        }

        stateChangeListener.onStateChange(order, fromStatus, toStatus, currentUser.getUserId(), remark);

        return Result.success("操作成功");
    }

    @Override
    public Result<?> suspendOrder(Long orderId, SuspendRequest request) {
        return performAction(orderId, OrderEvent.SUSPEND, request.getReason());
    }

    @Override
    public Result<?> completeOrder(Long orderId, String summary, List<Long> attachmentIds) {
        LoginUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            throw new BusinessException(401, "用户未登录");
        }

        RepairOrder order = repairOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("报修单不存在");
        }

        OrderStatus fromStatus = order.getStatus();
        OrderStatus toStatus = orderStateMachine.fire(order, OrderEvent.COMPLETE, currentUser.getUserId(), currentUser.getRole());

        order.setStatus(toStatus);
        order.setCompletedAt(LocalDateTime.now());

        int rows = repairOrderMapper.updateById(order);
        if (rows == 0) {
            throw new BusinessException("操作失败，数据已被修改，请刷新后重试");
        }

        if (attachmentIds != null && !attachmentIds.isEmpty()) {
            for (Long attachmentId : attachmentIds) {
                Attachment attachment = attachmentMapper.selectById(attachmentId);
                if (attachment != null) {
                    attachment.setOrderId(orderId);
                    attachment.setUsageType("COMPLETION");
                    attachmentMapper.updateById(attachment);
                }
            }
        }

        stateChangeListener.onStateChange(order, fromStatus, toStatus, currentUser.getUserId(), summary);

        return Result.success("完工提交成功");
    }

    private void applyDataScopeCheck(RepairOrder order) {
        LoginUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            throw new BusinessException(401, "用户未登录");
        }

        RoleType role = currentUser.getRole();
        if (role == RoleType.ADMIN) {
            return;
        }

        if (role == RoleType.OWNER && !order.getOwnerId().equals(currentUser.getUserId())) {
            throw new BusinessException(403, "无权查看该报修单");
        }

        if (role == RoleType.WORKER && !currentUser.getUserId().equals(order.getCurrentWorkerId())) {
            throw new BusinessException(403, "无权查看该报修单");
        }

        if (role == RoleType.SUPERVISOR) {
            List<Community> communities = communityMapper.selectBySupervisorId(currentUser.getUserId());
            boolean hasAccess = communities.stream()
                    .anyMatch(c -> c.getId().equals(order.getCommunityId()));
            if (!hasAccess) {
                throw new BusinessException(403, "无权查看该报修单");
            }
        }
    }

    private void applyDataScope(LambdaQueryWrapper<RepairOrder> wrapper) {
        LoginUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            throw new BusinessException(401, "用户未登录");
        }

        RoleType role = currentUser.getRole();
        switch (role) {
            case OWNER:
                wrapper.eq(RepairOrder::getOwnerId, currentUser.getUserId());
                break;
            case WORKER:
                wrapper.eq(RepairOrder::getCurrentWorkerId, currentUser.getUserId());
                break;
            case SUPERVISOR:
                List<Community> communities = communityMapper.selectBySupervisorId(currentUser.getUserId());
                if (communities.isEmpty()) {
                    wrapper.eq(RepairOrder::getCommunityId, -1L);
                } else {
                    List<Long> communityIds = communities.stream()
                            .map(Community::getId)
                            .collect(Collectors.toList());
                    wrapper.in(RepairOrder::getCommunityId, communityIds);
                }
                break;
            case ADMIN:
                break;
            default:
                throw new BusinessException(403, "无权限访问");
        }
    }

    private void updateTimeFields(RepairOrder order, OrderEvent event) {
        LocalDateTime now = LocalDateTime.now();
        switch (event) {
            case DISPATCH:
            case REASSIGN:
                order.setAssignedAt(now);
                break;
            case ACCEPT:
                order.setAcceptedAt(now);
                break;
            case START_REPAIR:
                order.setStartedAt(now);
                break;
            case COMPLETE:
                order.setCompletedAt(now);
                break;
            case OWNER_CONFIRM:
                order.setConfirmedAt(now);
                break;
            default:
                break;
        }
    }

    private String generateOrderNo() {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String redisKey = "repair:order_no:" + dateStr;
        Long sequence = stringRedisTemplate.opsForValue().increment(redisKey);
        if (sequence != null && sequence == 1) {
            stringRedisTemplate.expire(redisKey, 2, TimeUnit.DAYS);
        }
        return RepairConstants.ORDER_NO_PREFIX + dateStr + String.format("%03d", sequence != null ? sequence : 1);
    }

    private RepairOrderDetailResponse buildDetailResponse(RepairOrder order) {
        RepairOrderDetailResponse response = new RepairOrderDetailResponse();
        response.setId(order.getId());
        response.setOrderNo(order.getOrderNo());
        response.setTitle(order.getTitle());
        response.setDescription(order.getDescription());
        response.setUrgency(order.getUrgency());
        response.setStatus(order.getStatus());
        response.setStatusDescription(order.getStatus().getDescription());
        response.setOwnerId(order.getOwnerId());
        response.setCommunityId(order.getCommunityId());
        response.setBuildingId(order.getBuildingId());
        response.setUnitNumber(order.getUnitNumber());
        response.setCategoryId(order.getCategoryId());
        response.setCurrentWorkerId(order.getCurrentWorkerId());
        response.setExpectedTime(order.getExpectedTime());
        response.setAssignedAt(order.getAssignedAt());
        response.setAcceptedAt(order.getAcceptedAt());
        response.setStartedAt(order.getStartedAt());
        response.setCompletedAt(order.getCompletedAt());
        response.setConfirmedAt(order.getConfirmedAt());
        response.setReworkCount(order.getReworkCount());
        response.setDuplicateGroup(order.getDuplicateGroup());
        response.setCreatedAt(order.getCreatedAt());
        response.setUpdatedAt(order.getUpdatedAt());

        SysUser owner = sysUserMapper.selectById(order.getOwnerId());
        if (owner != null) {
            response.setOwnerName(owner.getRealName());
            response.setOwnerPhone(owner.getPhone());
        }

        if (order.getCurrentWorkerId() != null) {
            SysUser worker = sysUserMapper.selectById(order.getCurrentWorkerId());
            if (worker != null) {
                response.setWorkerName(worker.getRealName());
                response.setWorkerPhone(worker.getPhone());
            }
        }

        Community community = communityMapper.selectById(order.getCommunityId());
        if (community != null) {
            response.setCommunityName(community.getName());
        }

        Building building = buildingMapper.selectById(order.getBuildingId());
        if (building != null) {
            response.setBuildingName(building.getName());
        }

        Category category = categoryMapper.selectById(order.getCategoryId());
        if (category != null) {
            response.setCategoryName(category.getName());
        }

        LambdaQueryWrapper<Attachment> attachmentWrapper = new LambdaQueryWrapper<>();
        attachmentWrapper.eq(Attachment::getOrderId, order.getId());
        List<Attachment> attachments = attachmentMapper.selectList(attachmentWrapper);
        List<RepairOrderDetailResponse.AttachmentInfo> attachmentInfos = attachments.stream()
                .map(a -> {
                    RepairOrderDetailResponse.AttachmentInfo info = new RepairOrderDetailResponse.AttachmentInfo();
                    info.setId(a.getId());
                    info.setFileName(a.getFileName());
                    info.setFilePath(a.getFilePath());
                    info.setFileType(a.getFileType());
                    info.setUsageType(a.getUsageType());
                    info.setCreatedAt(a.getCreatedAt());
                    return info;
                }).collect(Collectors.toList());
        response.setAttachments(attachmentInfos);

        LambdaQueryWrapper<RepairProgress> progressWrapper = new LambdaQueryWrapper<>();
        progressWrapper.eq(RepairProgress::getOrderId, order.getId())
                .orderByAsc(RepairProgress::getCreatedAt);
        List<RepairProgress> progressList = repairProgressMapper.selectList(progressWrapper);
        List<RepairOrderDetailResponse.ProgressInfo> progressInfos = progressList.stream()
                .map(p -> {
                    RepairOrderDetailResponse.ProgressInfo info = new RepairOrderDetailResponse.ProgressInfo();
                    info.setId(p.getId());
                    info.setFromStatus(p.getFromStatus());
                    info.setToStatus(p.getToStatus());
                    info.setOperatorId(p.getOperatorId());
                    info.setRemark(p.getRemark());
                    info.setCreatedAt(p.getCreatedAt());
                    if (p.getOperatorId() != null) {
                        SysUser operator = sysUserMapper.selectById(p.getOperatorId());
                        if (operator != null) {
                            info.setOperatorName(operator.getRealName());
                        }
                    }
                    return info;
                }).collect(Collectors.toList());
        response.setProgressList(progressInfos);

        LambdaQueryWrapper<Evaluation> evalWrapper = new LambdaQueryWrapper<>();
        evalWrapper.eq(Evaluation::getOrderId, order.getId());
        Evaluation evaluation = evaluationMapper.selectOne(evalWrapper);
        if (evaluation != null) {
            RepairOrderDetailResponse.EvaluationInfo evalInfo = new RepairOrderDetailResponse.EvaluationInfo();
            evalInfo.setId(evaluation.getId());
            evalInfo.setScore(evaluation.getScore());
            evalInfo.setAttitudeScore(evaluation.getAttitudeScore());
            evalInfo.setQualityScore(evaluation.getQualityScore());
            evalInfo.setSpeedScore(evaluation.getSpeedScore());
            evalInfo.setComment(evaluation.getComment());
            evalInfo.setCreatedAt(evaluation.getCreatedAt());
            response.setEvaluation(evalInfo);
        }

        return response;
    }

    private RepairOrderListResponse convertToListResponse(RepairOrder order) {
        RepairOrderListResponse response = new RepairOrderListResponse();
        response.setId(order.getId());
        response.setOrderNo(order.getOrderNo());
        response.setTitle(order.getTitle());
        response.setUrgency(order.getUrgency());
        response.setStatus(order.getStatus());
        response.setStatusDescription(order.getStatus().getDescription());
        response.setUnitNumber(order.getUnitNumber());
        response.setReworkCount(order.getReworkCount());
        response.setCreatedAt(order.getCreatedAt());
        response.setAssignedAt(order.getAssignedAt());
        response.setCompletedAt(order.getCompletedAt());

        Community community = communityMapper.selectById(order.getCommunityId());
        if (community != null) {
            response.setCommunityName(community.getName());
        }

        Building building = buildingMapper.selectById(order.getBuildingId());
        if (building != null) {
            response.setBuildingName(building.getName());
        }

        Category category = categoryMapper.selectById(order.getCategoryId());
        if (category != null) {
            response.setCategoryName(category.getName());
        }

        SysUser ownerUser = sysUserMapper.selectById(order.getOwnerId());
        if (ownerUser != null) {
            response.setOwnerName(ownerUser.getRealName());
        }

        if (order.getCurrentWorkerId() != null) {
            SysUser worker = sysUserMapper.selectById(order.getCurrentWorkerId());
            if (worker != null) {
                response.setWorkerName(worker.getRealName());
            }
        }

        return response;
    }
}
