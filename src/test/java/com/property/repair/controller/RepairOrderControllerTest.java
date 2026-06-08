package com.property.repair.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.property.repair.common.enums.OrderEvent;
import com.property.repair.common.enums.OrderStatus;
import com.property.repair.common.exception.GlobalExceptionHandler;
import com.property.repair.common.result.PageResult;
import com.property.repair.common.result.Result;
import com.property.repair.dto.request.RepairOrderCreateRequest;
import com.property.repair.dto.request.RepairOrderQueryRequest;
import com.property.repair.dto.response.RepairOrderDetailResponse;
import com.property.repair.dto.response.RepairOrderListResponse;
import com.property.repair.entity.RepairOrder;
import com.property.repair.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class RepairOrderControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private RepairOrderService repairOrderService;

    @Mock
    private ReworkService reworkService;

    @Mock
    private EvaluationService evaluationService;

    @Mock
    private RepairProgressService repairProgressService;

    @Mock
    private DuplicateDetectionService duplicateDetectionService;

    @InjectMocks
    private RepairOrderController repairOrderController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(repairOrderController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("1. POST /api/repair-orders - create order 200")
    void testCreateOrder() throws Exception {
        RepairOrderCreateRequest request = new RepairOrderCreateRequest();
        request.setCommunityId(1L);
        request.setBuildingId(100L);
        request.setUnitNumber("3-201");
        request.setCategoryId(10L);
        request.setTitle("Water leak");
        request.setDescription("Water leak in bathroom pipe");
        request.setUrgency(2);

        RepairOrder createdOrder = new RepairOrder();
        createdOrder.setId(1L);
        createdOrder.setOrderNo("RO20260608001");
        createdOrder.setStatus(OrderStatus.PENDING);

        doReturn(Result.success("报修单创建成功", createdOrder))
                .when(repairOrderService).createOrder(any(RepairOrderCreateRequest.class));

        mockMvc.perform(post("/api/repair-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("报修单创建成功"));
    }

    @Test
    @DisplayName("2. GET /api/repair-orders/{id} - get order detail 200")
    void testGetOrderDetail() throws Exception {
        RepairOrderDetailResponse detail = new RepairOrderDetailResponse();
        detail.setId(1L);
        detail.setOrderNo("RO20260608001");
        detail.setTitle("Water leak");
        detail.setDescription("Water leak in bathroom pipe");
        detail.setUrgency(2);
        detail.setStatus(OrderStatus.PENDING);
        detail.setStatusDescription("待派单");
        detail.setOwnerId(1L);
        detail.setOwnerName("张三");
        detail.setCommunityId(1L);
        detail.setCommunityName("阳光小区");
        detail.setBuildingId(100L);
        detail.setBuildingName("1号楼");
        detail.setUnitNumber("3-201");
        detail.setCategoryId(10L);
        detail.setCategoryName("水管维修");

        when(repairOrderService.getOrderDetail(1L)).thenReturn(Result.success(detail));

        mockMvc.perform(get("/api/repair-orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.orderNo").value("RO20260608001"))
                .andExpect(jsonPath("$.data.title").value("Water leak"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.ownerName").value("张三"));
    }

    @Test
    @DisplayName("3. GET /api/repair-orders - get order list 200")
    void testGetOrderList() throws Exception {
        RepairOrderListResponse listItem = new RepairOrderListResponse();
        listItem.setId(1L);
        listItem.setOrderNo("RO20260608001");
        listItem.setTitle("Water leak");
        listItem.setUrgency(2);
        listItem.setStatus(OrderStatus.PENDING);
        listItem.setStatusDescription("待派单");
        listItem.setCommunityName("阳光小区");
        listItem.setBuildingName("1号楼");
        listItem.setOwnerName("张三");

        PageResult<RepairOrderListResponse> pageResult = new PageResult<>();
        pageResult.setRecords(Collections.singletonList(listItem));
        pageResult.setTotal(1);
        pageResult.setPageNum(1);
        pageResult.setPageSize(10);
        pageResult.setPages(1);

        when(repairOrderService.getOrderList(any(RepairOrderQueryRequest.class)))
                .thenReturn(Result.success(pageResult));

        mockMvc.perform(get("/api/repair-orders")
                        .param("pageNum", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(1))
                .andExpect(jsonPath("$.data.records[0].orderNo").value("RO20260608001"));
    }

    @Test
    @DisplayName("4. POST /api/repair-orders/{id}/accept - accept order 200")
    void testAcceptOrder() throws Exception {
        doReturn(Result.success("操作成功"))
                .when(repairOrderService).performAction(eq(1L), eq(OrderEvent.ACCEPT), isNull());

        mockMvc.perform(post("/api/repair-orders/1/accept"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("操作成功"));
    }

    @Test
    @DisplayName("5. POST /api/repair-orders/{id}/complete - complete order 200")
    void testCompleteOrder() throws Exception {
        doReturn(Result.success("完工提交成功"))
                .when(repairOrderService).completeOrder(eq(1L), anyString(), any());

        mockMvc.perform(post("/api/repair-orders/1/complete")
                        .param("summary", "Fixed the pipe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("完工提交成功"));
    }
}
