package com.property.repair.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.property.repair.entity.SparePart;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface SparePartMapper extends BaseMapper<SparePart> {

    /**
     * Count historical consumption frequency of each part for a given problem type,
     * community and optionally building. Used by the recommendation algorithm.
     */
    @Select("SELECT ri.part_id, SUM(ri.consumed_quantity) AS total_consumed " +
            "FROM spare_part_requisition_item ri " +
            "JOIN spare_part_requisition r ON ri.requisition_id = r.id " +
            "JOIN repair_order o ON r.order_id = o.id " +
            "WHERE o.problem_type = #{problemType} " +
            "AND o.community_id = #{communityId} " +
            "AND ri.consumed_quantity > 0 " +
            "GROUP BY ri.part_id " +
            "ORDER BY total_consumed DESC")
    List<Map<String, Object>> countHistoricalConsumption(
            @Param("problemType") String problemType,
            @Param("communityId") Long communityId);
}
