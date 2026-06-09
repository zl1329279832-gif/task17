package com.property.repair.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.property.repair.entity.RepairOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface RepairOrderMapper extends BaseMapper<RepairOrder> {

    /**
     * Count active orders for a worker (current load).
     */
    @Select("SELECT COUNT(*) FROM repair_order " +
            "WHERE assigned_worker_id = #{workerId} " +
            "AND status IN ('DISPATCHED','ACCEPTED','VISITING','REWORKING','WAITING_PARTS') " +
            "AND deleted = 0")
    int countActiveOrders(@Param("workerId") Long workerId);

    /**
     * Find potential duplicate orders for a given owner, community, building, problem type.
     */
    @Select("SELECT * FROM repair_order " +
            "WHERE owner_id = #{ownerId} " +
            "AND community_id = #{communityId} " +
            "AND (building_id = #{buildingId} OR (building_id IS NULL AND #{buildingId} IS NULL)) " +
            "AND problem_type = #{problemType} " +
            "AND status NOT IN ('COMPLETED','REVIEWED','CLOSED','CANCELLED') " +
            "AND submitted_at >= #{since} " +
            "AND deleted = 0 " +
            "ORDER BY submitted_at DESC")
    List<RepairOrder> findPotentialDuplicates(
            @Param("ownerId") Long ownerId,
            @Param("communityId") Long communityId,
            @Param("buildingId") Long buildingId,
            @Param("problemType") String problemType,
            @Param("since") LocalDateTime since);
}
