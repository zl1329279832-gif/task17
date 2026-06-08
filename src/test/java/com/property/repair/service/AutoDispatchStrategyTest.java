package com.property.repair.service;

import com.property.repair.entity.Building;
import com.property.repair.entity.RepairOrder;
import com.property.repair.entity.SysUser;
import com.property.repair.entity.WorkerSkill;
import com.property.repair.mapper.BuildingMapper;
import com.property.repair.mapper.RepairOrderMapper;
import com.property.repair.mapper.WorkerSkillMapper;
import com.property.repair.strategy.AutoDispatchStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutoDispatchStrategyTest {

    @Mock
    private WorkerSkillMapper workerSkillMapper;

    @Mock
    private RepairOrderMapper repairOrderMapper;

    @Mock
    private BuildingMapper buildingMapper;

    @InjectMocks
    private AutoDispatchStrategy autoDispatchStrategy;

    private RepairOrder order;
    private Building orderBuilding;

    @BeforeEach
    void setUp() {
        order = new RepairOrder();
        order.setId(1L);
        order.setCategoryId(10L);
        order.setBuildingId(100L);
        order.setCommunityId(1L);

        orderBuilding = new Building();
        orderBuilding.setId(100L);
        orderBuilding.setLongitude(new BigDecimal("116.400"));
        orderBuilding.setLatitude(new BigDecimal("39.900"));
    }

    private SysUser createWorker(Long id, Integer onlineStatus, Long buildingId) {
        SysUser worker = new SysUser();
        worker.setId(id);
        worker.setUsername("worker" + id);
        worker.setOnlineStatus(onlineStatus);
        worker.setBuildingId(buildingId);
        return worker;
    }

    private WorkerSkill createSkill(Long workerId, Long categoryId, int proficiency) {
        WorkerSkill skill = new WorkerSkill();
        skill.setWorkerId(workerId);
        skill.setCategoryId(categoryId);
        skill.setProficiency(proficiency);
        return skill;
    }

    @Test
    @DisplayName("1. Select worker with the best overall score")
    void testSelectWorkerWithBestScore() {
        SysUser worker1 = createWorker(1L, 1, 200L);
        SysUser worker2 = createWorker(2L, 1, 201L);
        List<SysUser> candidates = Arrays.asList(worker1, worker2);

        when(buildingMapper.selectById(100L)).thenReturn(orderBuilding);

        // Worker1: proficiency=3 -> skill=30, online=15
        WorkerSkill skill1 = createSkill(1L, 10L, 3);
        when(workerSkillMapper.selectByWorkerId(1L)).thenReturn(Collections.singletonList(skill1));
        when(repairOrderMapper.countByWorkerAndStatuses(eq(1L), anyList())).thenReturn(0);
        Building workerBuilding1 = new Building();
        workerBuilding1.setId(200L);
        workerBuilding1.setLongitude(new BigDecimal("116.401"));
        workerBuilding1.setLatitude(new BigDecimal("39.901"));
        when(buildingMapper.selectById(200L)).thenReturn(workerBuilding1);

        // Worker2: proficiency=1 -> skill=10, online=15
        WorkerSkill skill2 = createSkill(2L, 10L, 1);
        when(workerSkillMapper.selectByWorkerId(2L)).thenReturn(Collections.singletonList(skill2));
        when(repairOrderMapper.countByWorkerAndStatuses(eq(2L), anyList())).thenReturn(3);
        Building workerBuilding2 = new Building();
        workerBuilding2.setId(201L);
        workerBuilding2.setLongitude(new BigDecimal("116.500"));
        workerBuilding2.setLatitude(new BigDecimal("39.950"));
        when(buildingMapper.selectById(201L)).thenReturn(workerBuilding2);

        Long selectedId = autoDispatchStrategy.selectWorker(order, candidates);
        // Worker1 has higher skill proficiency (30 vs 10) and lower load (30 vs 12)
        assertEquals(1L, selectedId);
    }

    @Test
    @DisplayName("2. Candidates without matching skill are excluded")
    void testSelectWorkerNoSkillMatch() {
        SysUser worker1 = createWorker(1L, 1, null);
        SysUser worker2 = createWorker(2L, 1, null);
        List<SysUser> candidates = Arrays.asList(worker1, worker2);

        when(buildingMapper.selectById(100L)).thenReturn(orderBuilding);

        // Worker1: no matching skill for category 10
        WorkerSkill otherSkill = createSkill(1L, 99L, 3);
        when(workerSkillMapper.selectByWorkerId(1L)).thenReturn(Collections.singletonList(otherSkill));

        // Worker2: has matching skill
        WorkerSkill matchingSkill = createSkill(2L, 10L, 2);
        when(workerSkillMapper.selectByWorkerId(2L)).thenReturn(Collections.singletonList(matchingSkill));
        when(repairOrderMapper.countByWorkerAndStatuses(eq(2L), anyList())).thenReturn(0);

        Long selectedId = autoDispatchStrategy.selectWorker(order, candidates);
        // Only worker2 has a matching skill, so worker2 must be selected
        assertEquals(2L, selectedId);
    }

    @Test
    @DisplayName("3. All workers offline still selectable (online score = 0)")
    void testSelectWorkerAllOffline() {
        SysUser worker1 = createWorker(1L, 0, null);
        SysUser worker2 = createWorker(2L, 0, null);
        List<SysUser> candidates = Arrays.asList(worker1, worker2);

        when(buildingMapper.selectById(100L)).thenReturn(orderBuilding);

        WorkerSkill skill1 = createSkill(1L, 10L, 3);
        when(workerSkillMapper.selectByWorkerId(1L)).thenReturn(Collections.singletonList(skill1));
        when(repairOrderMapper.countByWorkerAndStatuses(eq(1L), anyList())).thenReturn(0);

        WorkerSkill skill2 = createSkill(2L, 10L, 1);
        when(workerSkillMapper.selectByWorkerId(2L)).thenReturn(Collections.singletonList(skill2));
        when(repairOrderMapper.countByWorkerAndStatuses(eq(2L), anyList())).thenReturn(0);

        Long selectedId = autoDispatchStrategy.selectWorker(order, candidates);
        // Both offline, worker1 has higher skill -> selected
        assertNotNull(selectedId);
        assertEquals(1L, selectedId);
    }

    @Test
    @DisplayName("4. Worker with lowest load is preferred when skill equal")
    void testSelectWorkerWithLowestLoad() {
        SysUser worker1 = createWorker(1L, 1, null);
        SysUser worker2 = createWorker(2L, 1, null);
        List<SysUser> candidates = Arrays.asList(worker1, worker2);

        when(buildingMapper.selectById(100L)).thenReturn(orderBuilding);

        // Both have same proficiency
        WorkerSkill skill1 = createSkill(1L, 10L, 2);
        when(workerSkillMapper.selectByWorkerId(1L)).thenReturn(Collections.singletonList(skill1));
        when(repairOrderMapper.countByWorkerAndStatuses(eq(1L), anyList())).thenReturn(5);

        WorkerSkill skill2 = createSkill(2L, 10L, 2);
        when(workerSkillMapper.selectByWorkerId(2L)).thenReturn(Collections.singletonList(skill2));
        when(repairOrderMapper.countByWorkerAndStatuses(eq(2L), anyList())).thenReturn(0);

        Long selectedId = autoDispatchStrategy.selectWorker(order, candidates);
        // Worker2 has lower load (score 30 vs 0), higher total
        assertEquals(2L, selectedId);
    }

    @Test
    @DisplayName("5. Empty candidate list returns null")
    void testSelectWorkerNoCandidates() {
        Long selectedId = autoDispatchStrategy.selectWorker(order, Collections.emptyList());
        assertNull(selectedId);
    }

    @Test
    @DisplayName("5b. Null candidate list returns null")
    void testSelectWorkerNullCandidates() {
        Long selectedId = autoDispatchStrategy.selectWorker(order, null);
        assertNull(selectedId);
    }

    @Test
    @DisplayName("6. Single candidate is selected directly")
    void testSelectWorkerSingleCandidate() {
        SysUser worker = createWorker(1L, 1, null);
        List<SysUser> candidates = Collections.singletonList(worker);

        when(buildingMapper.selectById(100L)).thenReturn(orderBuilding);

        WorkerSkill skill = createSkill(1L, 10L, 2);
        when(workerSkillMapper.selectByWorkerId(1L)).thenReturn(Collections.singletonList(skill));
        when(repairOrderMapper.countByWorkerAndStatuses(eq(1L), anyList())).thenReturn(0);

        Long selectedId = autoDispatchStrategy.selectWorker(order, candidates);
        assertEquals(1L, selectedId);
    }
}
