package com.property.repair.service;

import com.property.repair.common.enums.OrderEvent;
import com.property.repair.common.enums.OrderStatus;
import com.property.repair.common.enums.RoleType;
import com.property.repair.common.result.Result;
import com.property.repair.dto.request.RepairOrderCreateRequest;
import com.property.repair.dto.response.DuplicateCheckResponse;
import com.property.repair.entity.*;
import com.property.repair.mapper.*;
import com.property.repair.security.LoginUser;
import com.property.repair.security.SecurityUtils;
import com.property.repair.service.impl.RepairOrderServiceImpl;
import com.property.repair.statemachine.OrderStateMachine;
import com.property.repair.statemachine.StateChangeListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RepairOrderServiceTest {

    @Mock
    private RepairOrderMapper repairOrderMapper;

    @Mock
    private OrderStateMachine orderStateMachine;

    @Mock
    private StateChangeListener stateChangeListener;

    @Mock
    private DispatchService dispatchService;

    @Mock
    private DuplicateDetectionService duplicateDetectionService;

    @Mock
    private AttachmentMapper attachmentMapper;

    @Mock
    private CommunityMapper communityMapper;

    @Mock
    private BuildingMapper buildingMapper;

    @Mock
    private CategoryMapper categoryMapper;

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private RepairProgressMapper repairProgressMapper;

    @Mock
    private EvaluationMapper evaluationMapper;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private RepairOrderServiceImpl repairOrderService;

    private MockedStatic<SecurityUtils> securityUtilsMock;
    private LoginUser currentUser;

    @BeforeEach
    void setUp() {
        currentUser = new LoginUser();
        currentUser.setUserId(1L);
        currentUser.setUsername("owner1");
        currentUser.setRole(RoleType.OWNER);
        currentUser.setCommunityId(1L);

        securityUtilsMock = mockStatic(SecurityUtils.class);
        securityUtilsMock.when(SecurityUtils::getCurrentUser).thenReturn(currentUser);
        securityUtilsMock.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
        securityUtilsMock.when(SecurityUtils::getCurrentRole).thenReturn(RoleType.OWNER);
    }

    @AfterEach
    void tearDown() {
        securityUtilsMock.close();
    }

    private RepairOrderCreateRequest buildCreateRequest() {
        RepairOrderCreateRequest request = new RepairOrderCreateRequest();
        request.setCommunityId(1L);
        request.setBuildingId(100L);
        request.setUnitNumber("3-201");
        request.setCategoryId(10L);
        request.setTitle("Water leak");
        request.setDescription("Water leak in bathroom pipe");
        request.setUrgency(2);
        request.setForceCreate(true);
        return request;
    }

    @Test
    @DisplayName("1. Create order successfully")
    void testCreateOrderSuccess() {
        RepairOrderCreateRequest request = buildCreateRequest();

        Community community = new Community();
        community.setId(1L);
        when(communityMapper.selectById(1L)).thenReturn(community);

        Building building = new Building();
        building.setId(100L);
        building.setCommunityId(1L);
        when(buildingMapper.selectById(100L)).thenReturn(building);

        Category category = new Category();
        category.setId(10L);
        when(categoryMapper.selectById(10L)).thenReturn(category);

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L);

        when(repairOrderMapper.insert(any(RepairOrder.class))).thenReturn(1);

        Result<?> result = repairOrderService.createOrder(request);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        verify(repairOrderMapper).insert(any(RepairOrder.class));
    }

    @Test
    @DisplayName("2. Create order with duplicate detection returns 409")
    void testCreateOrderWithDuplicateDetection() {
        RepairOrderCreateRequest request = buildCreateRequest();
        request.setForceCreate(false);

        Community community = new Community();
        community.setId(1L);
        when(communityMapper.selectById(1L)).thenReturn(community);

        Building building = new Building();
        building.setId(100L);
        building.setCommunityId(1L);
        when(buildingMapper.selectById(100L)).thenReturn(building);

        Category category = new Category();
        category.setId(10L);
        when(categoryMapper.selectById(10L)).thenReturn(category);

        DuplicateCheckResponse duplicateResponse = new DuplicateCheckResponse();
        duplicateResponse.setDuplicate(true);
        duplicateResponse.setDuplicateOrders(new ArrayList<>());
        when(duplicateDetectionService.checkDuplicate(1L, 100L, 10L)).thenReturn(duplicateResponse);

        Result<?> result = repairOrderService.createOrder(request);

        assertNotNull(result);
        assertEquals(409, result.getCode());
        verify(repairOrderMapper, never()).insert(any());
    }

    @Test
    @DisplayName("3. Perform accept action successfully")
    void testPerformActionAccept() {
        currentUser.setRole(RoleType.WORKER);
        securityUtilsMock.when(SecurityUtils::getCurrentUser).thenReturn(currentUser);

        RepairOrder order = new RepairOrder();
        order.setId(1L);
        order.setStatus(OrderStatus.ASSIGNED);
        order.setCurrentWorkerId(1L);
        order.setOwnerId(2L);
        order.setCommunityId(1L);
        order.setVersion(0);

        when(repairOrderMapper.selectById(1L)).thenReturn(order);
        when(orderStateMachine.fire(order, OrderEvent.ACCEPT, 1L, RoleType.WORKER))
                .thenReturn(OrderStatus.ACCEPTED);
        when(repairOrderMapper.updateById(any(RepairOrder.class))).thenReturn(1);

        Result<?> result = repairOrderService.performAction(1L, OrderEvent.ACCEPT, null);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        verify(orderStateMachine).fire(order, OrderEvent.ACCEPT, 1L, RoleType.WORKER);
        verify(stateChangeListener).onStateChange(
                eq(order), eq(OrderStatus.ASSIGNED), eq(OrderStatus.ACCEPTED), eq(1L), isNull());
    }

    @Test
    @DisplayName("4. Perform complete action successfully")
    void testPerformActionComplete() {
        currentUser.setRole(RoleType.WORKER);
        securityUtilsMock.when(SecurityUtils::getCurrentUser).thenReturn(currentUser);

        RepairOrder order = new RepairOrder();
        order.setId(1L);
        order.setStatus(OrderStatus.IN_PROGRESS);
        order.setCurrentWorkerId(1L);
        order.setOwnerId(2L);
        order.setCommunityId(1L);
        order.setVersion(0);

        when(repairOrderMapper.selectById(1L)).thenReturn(order);
        when(orderStateMachine.fire(order, OrderEvent.COMPLETE, 1L, RoleType.WORKER))
                .thenReturn(OrderStatus.COMPLETED);
        when(repairOrderMapper.updateById(any(RepairOrder.class))).thenReturn(1);

        Result<?> result = repairOrderService.performAction(1L, OrderEvent.COMPLETE, "Repair finished");

        assertNotNull(result);
        assertEquals(200, result.getCode());
        verify(stateChangeListener).onStateChange(
                eq(order), eq(OrderStatus.IN_PROGRESS), eq(OrderStatus.COMPLETED), eq(1L), eq("Repair finished"));
    }
}
