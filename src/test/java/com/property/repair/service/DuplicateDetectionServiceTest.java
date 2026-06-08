package com.property.repair.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.property.repair.common.enums.OrderStatus;
import com.property.repair.dto.response.DuplicateCheckResponse;
import com.property.repair.entity.RepairOrder;
import com.property.repair.mapper.RepairOrderMapper;
import com.property.repair.service.impl.DuplicateDetectionServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DuplicateDetectionServiceTest {

    @Mock
    private RepairOrderMapper repairOrderMapper;

    @InjectMocks
    private DuplicateDetectionServiceImpl duplicateDetectionService;

    @Test
    @DisplayName("1. No duplicate found - hasDuplicate=false, empty list")
    void testNoDuplicateFound() {
        when(repairOrderMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(new ArrayList<>());

        DuplicateCheckResponse response = duplicateDetectionService.checkDuplicate(1L, 100L, 10L);

        assertFalse(response.isDuplicate());
        assertNotNull(response.getDuplicateOrders());
        assertTrue(response.getDuplicateOrders().isEmpty());
    }

    @Test
    @DisplayName("2. Duplicate found - hasDuplicate=true, list contains matching orders")
    void testDuplicateFound() {
        RepairOrder existingOrder = new RepairOrder();
        existingOrder.setId(99L);
        existingOrder.setOrderNo("RO20260608001");
        existingOrder.setTitle("Water leak in kitchen");
        existingOrder.setStatus(OrderStatus.PENDING);
        existingOrder.setCommunityId(1L);
        existingOrder.setBuildingId(100L);
        existingOrder.setCategoryId(10L);
        existingOrder.setCreatedAt(LocalDateTime.now().minusHours(2));

        List<RepairOrder> existingOrders = Collections.singletonList(existingOrder);
        when(repairOrderMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(existingOrders);

        DuplicateCheckResponse response = duplicateDetectionService.checkDuplicate(1L, 100L, 10L);

        assertTrue(response.isDuplicate());
        assertNotNull(response.getDuplicateOrders());
        assertEquals(1, response.getDuplicateOrders().size());

        DuplicateCheckResponse.DuplicateOrderInfo info = response.getDuplicateOrders().get(0);
        assertEquals(99L, info.getId());
        assertEquals("RO20260608001", info.getOrderNo());
        assertEquals("Water leak in kitchen", info.getTitle());
        assertEquals("PENDING", info.getStatus());
    }

    @Test
    @DisplayName("3. Different building does not produce duplicate (query filters by buildingId)")
    void testDuplicateWithDifferentBuilding() {
        // Query for building 200 should return empty (the existing order is for building 100)
        when(repairOrderMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(new ArrayList<>());

        DuplicateCheckResponse response = duplicateDetectionService.checkDuplicate(1L, 200L, 10L);

        assertFalse(response.isDuplicate());
        assertNotNull(response.getDuplicateOrders());
        assertTrue(response.getDuplicateOrders().isEmpty());
    }
}
