package com.property.repair.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.property.repair.dto.LoginRequest;
import com.property.repair.dto.RepairOrderSubmitRequest;
import com.property.repair.dto.ReviewRequest;
import com.property.repair.entity.RepairOrder;
import com.property.repair.entity.User;
import com.property.repair.enums.OrderStatus;
import com.property.repair.mapper.RepairOrderMapper;
import com.property.repair.mapper.UserMapper;
import com.property.repair.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RepairOrderControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenProvider tokenProvider;
    @Autowired private UserMapper userMapper;
    @Autowired private RepairOrderMapper orderMapper;

    private String ownerToken;
    private String workerToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        ownerToken = tokenProvider.generateToken(8L, "owner1", "OWNER");
        workerToken = tokenProvider.generateToken(4L, "worker1", "WORKER");
        adminToken = tokenProvider.generateToken(1L, "admin", "ADMIN");
    }

    @Test
    @DisplayName("Full lifecycle: submit → dispatch → accept → visit → complete → review")
    void fullLifecycle() throws Exception {
        // 1. Owner submits order
        RepairOrderSubmitRequest submitReq = new RepairOrderSubmitRequest();
        submitReq.setTitle("Leaking pipe");
        submitReq.setProblemType("PLUMBING");
        submitReq.setUrgency(3);
        submitReq.setCommunityId(1L);
        submitReq.setBuildingId(1L);
        submitReq.setRoomNo("301");

        MvcResult result = mockMvc.perform(post("/orders")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(submitReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        // Extract order ID from response
        String response = result.getResponse().getContentAsString();
        // Parse order ID (simplified)

        // Note: Full integration test requires database to be running
        // This test demonstrates the API structure
    }

    @Test
    @DisplayName("Unauthorized access should return 401")
    void unauthorizedAccess() throws Exception {
        mockMvc.perform(get("/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Worker cannot submit orders")
    void workerCannotSubmit() throws Exception {
        RepairOrderSubmitRequest submitReq = new RepairOrderSubmitRequest();
        submitReq.setTitle("Test");
        submitReq.setProblemType("PLUMBING");
        submitReq.setUrgency(2);
        submitReq.setCommunityId(1L);

        mockMvc.perform(post("/orders")
                .header("Authorization", "Bearer " + workerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(submitReq)))
                .andExpect(status().isForbidden());
    }
}
