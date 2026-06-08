package com.property.repair.strategy;

import com.property.repair.common.enums.OrderStatus;
import com.property.repair.entity.Building;
import com.property.repair.entity.RepairOrder;
import com.property.repair.entity.SysUser;
import com.property.repair.entity.WorkerSkill;
import com.property.repair.mapper.BuildingMapper;
import com.property.repair.mapper.RepairOrderMapper;
import com.property.repair.mapper.WorkerSkillMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

@Component("autoDispatchStrategy")
public class AutoDispatchStrategy implements DispatchStrategy {

    private final WorkerSkillMapper workerSkillMapper;
    private final RepairOrderMapper repairOrderMapper;
    private final BuildingMapper buildingMapper;

    public AutoDispatchStrategy(WorkerSkillMapper workerSkillMapper,
                                RepairOrderMapper repairOrderMapper,
                                BuildingMapper buildingMapper) {
        this.workerSkillMapper = workerSkillMapper;
        this.repairOrderMapper = repairOrderMapper;
        this.buildingMapper = buildingMapper;
    }

    @Override
    public Long selectWorker(RepairOrder order, List<SysUser> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        Building orderBuilding = buildingMapper.selectById(order.getBuildingId());

        Long bestWorkerId = null;
        int bestScore = -1;

        List<String> activeStatuses = Arrays.asList(
                OrderStatus.ASSIGNED.name(),
                OrderStatus.ACCEPTED.name(),
                OrderStatus.IN_PROGRESS.name(),
                OrderStatus.SUSPENDED.name()
        );

        for (SysUser candidate : candidates) {
            // 1. Skill match (30 points): check proficiency from worker_skill
            List<WorkerSkill> skills = workerSkillMapper.selectByWorkerId(candidate.getId());
            WorkerSkill matchedSkill = null;
            for (WorkerSkill skill : skills) {
                if (skill.getCategoryId().equals(order.getCategoryId())) {
                    matchedSkill = skill;
                    break;
                }
            }

            // No matching skill -> exclude this candidate
            if (matchedSkill == null) {
                continue;
            }

            int skillScore;
            switch (matchedSkill.getProficiency()) {
                case 1:
                    skillScore = 10;
                    break;
                case 2:
                    skillScore = 20;
                    break;
                case 3:
                    skillScore = 30;
                    break;
                default:
                    skillScore = 10;
                    break;
            }

            // 2. Workload (30 points): count active orders
            int activeOrderCount = repairOrderMapper.countByWorkerAndStatuses(
                    candidate.getId(), activeStatuses);
            int workloadScore = Math.max(0, 30 - activeOrderCount * 6);

            // 3. Building proximity (25 points): compare coordinates
            int proximityScore = calculateProximityScore(candidate, orderBuilding);

            // 4. Online status (15 points)
            int onlineScore = (candidate.getOnlineStatus() != null && candidate.getOnlineStatus() == 1) ? 15 : 0;

            int totalScore = skillScore + workloadScore + proximityScore + onlineScore;

            if (totalScore > bestScore) {
                bestScore = totalScore;
                bestWorkerId = candidate.getId();
            }
        }

        return bestWorkerId;
    }

    /**
     * Calculate proximity score based on building coordinates.
     * If coordinates are unavailable, return a default score of 12.
     */
    private int calculateProximityScore(SysUser worker, Building orderBuilding) {
        if (orderBuilding == null || orderBuilding.getLongitude() == null || orderBuilding.getLatitude() == null) {
            return 12;
        }

        if (worker.getBuildingId() == null) {
            return 12;
        }

        Building workerBuilding = buildingMapper.selectById(worker.getBuildingId());
        if (workerBuilding == null || workerBuilding.getLongitude() == null || workerBuilding.getLatitude() == null) {
            return 12;
        }

        double distance = calculateDistance(
                orderBuilding.getLatitude().doubleValue(),
                orderBuilding.getLongitude().doubleValue(),
                workerBuilding.getLatitude().doubleValue(),
                workerBuilding.getLongitude().doubleValue()
        );

        // Within 500m -> 25 points, within 1km -> 20, within 2km -> 15, within 5km -> 10, beyond -> 5
        if (distance <= 0.5) {
            return 25;
        } else if (distance <= 1.0) {
            return 20;
        } else if (distance <= 2.0) {
            return 15;
        } else if (distance <= 5.0) {
            return 10;
        } else {
            return 5;
        }
    }

    /**
     * Calculate approximate distance in kilometers using Haversine formula.
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
