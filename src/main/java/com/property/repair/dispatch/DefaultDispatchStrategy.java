package com.property.repair.dispatch;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.property.repair.dto.DispatchCandidate;
import com.property.repair.entity.RepairOrder;
import com.property.repair.entity.User;
import com.property.repair.entity.WorkerSkill;
import com.property.repair.enums.UserRole;
import com.property.repair.mapper.RepairOrderMapper;
import com.property.repair.mapper.UserMapper;
import com.property.repair.mapper.WorkerSkillMapper;
import com.property.repair.service.DispatchStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Default auto-dispatch strategy that scores workers based on:
 *
 * 1. Skill match (weight: 40%) — proficiency in the problem type
 * 2. Current load (weight: 30%) — fewer active orders = better
 * 3. Community proximity (weight: 20%) — same community/building preferred
 * 4. Online status (weight: 10%) — online workers preferred
 *
 * Workers with 0 skill proficiency for the problem type are excluded.
 * If no worker has the matching skill, falls back to least-loaded worker in the community.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultDispatchStrategy implements DispatchStrategy {

    private static final BigDecimal WEIGHT_SKILL     = new BigDecimal("0.40");
    private static final BigDecimal WEIGHT_LOAD      = new BigDecimal("0.30");
    private static final BigDecimal WEIGHT_COMMUNITY = new BigDecimal("0.20");
    private static final BigDecimal WEIGHT_ONLINE    = new BigDecimal("0.10");

    private static final int MAX_LOAD = 10; // Normalization: 10 orders = full load

    private final UserMapper userMapper;
    private final WorkerSkillMapper workerSkillMapper;
    private final RepairOrderMapper orderMapper;

    @Override
    public Long findBestWorker(RepairOrder order) {
        List<DispatchCandidate> candidates = getCandidates(order);
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(0).getWorkerId();
    }

    @Override
    public List<DispatchCandidate> getCandidates(RepairOrder order) {
        // 1. Find workers with matching skill for this problem type in this community
        List<User> skilledWorkers = userMapper.findWorkersBySkillAndCommunity(
                order.getProblemType(), order.getCommunityId());

        // 2. If no skilled workers, fallback: all enabled workers in the community
        if (skilledWorkers.isEmpty()) {
            log.info("No skilled workers for problemType={} in community={}, falling back",
                    order.getProblemType(), order.getCommunityId());
            skilledWorkers = userMapper.selectList(
                    new LambdaQueryWrapper<User>()
                            .eq(User::getRole, UserRole.WORKER.getCode())
                            .eq(User::getStatus, 1)
                            .eq(User::getCommunityId, order.getCommunityId()));
        }

        // 3. If still empty, try all workers
        if (skilledWorkers.isEmpty()) {
            skilledWorkers = userMapper.selectList(
                    new LambdaQueryWrapper<User>()
                            .eq(User::getRole, UserRole.WORKER.getCode())
                            .eq(User::getStatus, 1));
        }

        // 4. Score each candidate
        List<DispatchCandidate> candidates = skilledWorkers.stream()
                .map(w -> buildCandidate(w, order))
                .sorted(Comparator.comparing(DispatchCandidate::getMatchScore).reversed())
                .collect(Collectors.toList());

        log.debug("Dispatch candidates for order={}: {}", order.getOrderNo(),
                candidates.stream().map(c -> c.getWorkerName() + "=" + c.getMatchScore())
                        .collect(Collectors.joining(", ")));

        return candidates;
    }

    private DispatchCandidate buildCandidate(User worker, RepairOrder order) {
        DispatchCandidate c = new DispatchCandidate();
        c.setWorkerId(worker.getId());
        c.setWorkerName(worker.getRealName());
        c.setPhone(worker.getPhone());
        c.setOnlineStatus(worker.getOnlineStatus());
        c.setLastOnlineAt(worker.getLastOnlineAt());

        // Current load
        int activeOrders = orderMapper.countActiveOrders(worker.getId());
        c.setCurrentLoad(activeOrders);

        // Skill proficiency
        WorkerSkill skill = workerSkillMapper.selectOne(
                new LambdaQueryWrapper<WorkerSkill>()
                        .eq(WorkerSkill::getWorkerId, worker.getId())
                        .eq(WorkerSkill::getProblemType, order.getProblemType()));
        int proficiency = skill != null ? skill.getProficiency() : 0;
        c.setSkillProficiency(proficiency);

        // ---- Scoring ----

        // Skill score: proficiency / 5 (0.0 ~ 1.0)
        BigDecimal skillScore = BigDecimal.valueOf(proficiency)
                .divide(BigDecimal.valueOf(5), 2, RoundingMode.HALF_UP);

        // Load score: (MAX_LOAD - currentLoad) / MAX_LOAD (1.0 = no load, 0.0 = full)
        BigDecimal loadScore = BigDecimal.valueOf(Math.max(0, MAX_LOAD - activeOrders))
                .divide(BigDecimal.valueOf(MAX_LOAD), 2, RoundingMode.HALF_UP);

        // Community score: 1.0 if same community, 0.8 if same building, 0.5 otherwise
        BigDecimal communityScore;
        if (worker.getCommunityId() != null && worker.getCommunityId().equals(order.getCommunityId())) {
            if (worker.getBuildingId() != null && worker.getBuildingId().equals(order.getBuildingId())) {
                communityScore = new BigDecimal("1.0");
            } else {
                communityScore = new BigDecimal("0.8");
            }
        } else {
            communityScore = new BigDecimal("0.5");
        }

        // Online score: 1.0 if online, 0.3 if recently online (<30min), 0.0 if offline
        BigDecimal onlineScore;
        if (worker.getOnlineStatus() != null && worker.getOnlineStatus() == 1) {
            onlineScore = new BigDecimal("1.0");
        } else if (worker.getLastOnlineAt() != null
                && Duration.between(worker.getLastOnlineAt(), LocalDateTime.now()).toMinutes() < 30) {
            onlineScore = new BigDecimal("0.3");
        } else {
            onlineScore = BigDecimal.ZERO;
        }

        // Weighted total
        BigDecimal total = skillScore.multiply(WEIGHT_SKILL)
                .add(loadScore.multiply(WEIGHT_LOAD))
                .add(communityScore.multiply(WEIGHT_COMMUNITY))
                .add(onlineScore.multiply(WEIGHT_ONLINE))
                .setScale(2, RoundingMode.HALF_UP);

        c.setMatchScore(total);
        return c;
    }
}
