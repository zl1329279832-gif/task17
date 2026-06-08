package com.property.repair.dispatch;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.property.repair.dto.DispatchCandidate;
import com.property.repair.entity.RepairOrder;
import com.property.repair.entity.User;
import com.property.repair.entity.WorkerSkill;
import com.property.repair.mapper.RepairOrderMapper;
import com.property.repair.mapper.UserMapper;
import com.property.repair.mapper.WorkerSkillMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultDispatchStrategyTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private WorkerSkillMapper workerSkillMapper;

    @Mock
    private RepairOrderMapper orderMapper;

    private DefaultDispatchStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new DefaultDispatchStrategy(userMapper, workerSkillMapper, orderMapper);
    }

    @Test
    @DisplayName("Should select online skilled worker with lowest load")
    void selectBestWorker_onlineSkilledLowLoad() {
        // Setup
        RepairOrder order = createOrder("PLUMBING", 1L, 1L);

        User worker1 = createWorker(4L, "Zhang San", 1L, 1L, 1);  // online
        User worker2 = createWorker(5L, "Li Si", 1L, 2L, 0);      // offline

        when(userMapper.findWorkersBySkillAndCommunity("PLUMBING", 1L))
                .thenReturn(Arrays.asList(worker1, worker2));
        when(orderMapper.countActiveOrders(4L)).thenReturn(2);
        when(orderMapper.countActiveOrders(5L)).thenReturn(1);
        when(workerSkillMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(createSkill(4L, "PLUMBING", 5))
                .thenReturn(createSkill(5L, "PLUMBING", 3));

        // Execute
        Long bestWorker = strategy.findBestWorker(order);

        // Assert — worker1 should win: higher skill (5 vs 3) + online
        assertNotNull(bestWorker);
        assertEquals(4L, bestWorker);
    }

    @Test
    @DisplayName("Should fallback to all workers when no skilled workers")
    void fallbackToAllWorkers() {
        RepairOrder order = createOrder("PLUMBING", 1L, 1L);

        // No skilled workers
        when(userMapper.findWorkersBySkillAndCommunity("PLUMBING", 1L))
                .thenReturn(Collections.emptyList());

        // Fallback: all workers in community
        User fallbackWorker = createWorker(7L, "Zhao Liu", 2L, 4L, 1);
        when(userMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(fallbackWorker));
        when(orderMapper.countActiveOrders(7L)).thenReturn(0);
        when(workerSkillMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(null);  // no matching skill

        Long bestWorker = strategy.findBestWorker(order);
        assertNotNull(bestWorker);
        assertEquals(7L, bestWorker);
    }

    @Test
    @DisplayName("Should return null when no workers available")
    void noWorkersAvailable() {
        RepairOrder order = createOrder("PLUMBING", 1L, 1L);

        when(userMapper.findWorkersBySkillAndCommunity("PLUMBING", 1L))
                .thenReturn(Collections.emptyList());
        when(userMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        Long bestWorker = strategy.findBestWorker(order);
        assertNull(bestWorker);
    }

    @Test
    @DisplayName("Should rank candidates by composite score")
    void rankCandidates() {
        RepairOrder order = createOrder("ELECTRICAL", 1L, 1L);

        User w1 = createWorker(4L, "Worker A", 1L, 1L, 1);
        User w2 = createWorker(5L, "Worker B", 1L, 1L, 1);
        User w3 = createWorker(6L, "Worker C", 1L, 2L, 0);

        when(userMapper.findWorkersBySkillAndCommunity("ELECTRICAL", 1L))
                .thenReturn(Arrays.asList(w1, w2, w3));
        when(orderMapper.countActiveOrders(4L)).thenReturn(5);  // high load
        when(orderMapper.countActiveOrders(5L)).thenReturn(1);  // low load
        when(orderMapper.countActiveOrders(6L)).thenReturn(3);

        when(workerSkillMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(createSkill(4L, "ELECTRICAL", 4))
                .thenReturn(createSkill(5L, "ELECTRICAL", 4))
                .thenReturn(createSkill(6L, "ELECTRICAL", 5));

        List<DispatchCandidate> candidates = strategy.getCandidates(order);

        assertEquals(3, candidates.size());
        // Scores should be descending
        assertTrue(candidates.get(0).getMatchScore().compareTo(candidates.get(1).getMatchScore()) >= 0);
        assertTrue(candidates.get(1).getMatchScore().compareTo(candidates.get(2).getMatchScore()) >= 0);
    }

    // ---- Helpers ----

    private RepairOrder createOrder(String problemType, Long communityId, Long buildingId) {
        RepairOrder order = new RepairOrder();
        order.setProblemType(problemType);
        order.setCommunityId(communityId);
        order.setBuildingId(buildingId);
        return order;
    }

    private User createWorker(Long id, String name, Long communityId, Long buildingId, int online) {
        User worker = new User();
        worker.setId(id);
        worker.setRealName(name);
        worker.setRole("WORKER");
        worker.setCommunityId(communityId);
        worker.setBuildingId(buildingId);
        worker.setOnlineStatus(online);
        worker.setStatus(1);
        return worker;
    }

    private WorkerSkill createSkill(Long workerId, String type, int proficiency) {
        WorkerSkill skill = new WorkerSkill();
        skill.setWorkerId(workerId);
        skill.setProblemType(type);
        skill.setProficiency(proficiency);
        return skill;
    }
}
