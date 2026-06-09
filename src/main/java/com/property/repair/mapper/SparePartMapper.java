package com.property.repair.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.property.repair.entity.SparePart;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SparePartMapper extends BaseMapper<SparePart> {

    /**
     * Find parts historically used for a given problem type in a building.
     * Aggregates consumed parts from completed orders in the same building.
     */
    @Select("SELECT sp.* FROM spare_part sp " +
            "INNER JOIN part_request_item pri ON sp.id = pri.part_id " +
            "INNER JOIN part_request pr ON pri.request_id = pr.id " +
            "INNER JOIN repair_order ro ON pr.order_id = ro.id " +
            "WHERE ro.problem_type = #{problemType} " +
            "AND ro.building_id = #{buildingId} " +
            "AND pri.consumed_qty > 0 " +
            "AND sp.deleted = 0 " +
            "GROUP BY sp.id " +
            "ORDER BY SUM(pri.consumed_qty) DESC " +
            "LIMIT 10")
    List<SparePart> findHistoricalParts(@Param("problemType") String problemType,
                                        @Param("buildingId") Long buildingId);

    /**
     * Find parts historically used for a given problem type (across all buildings).
     */
    @Select("SELECT sp.* FROM spare_part sp " +
            "INNER JOIN part_request_item pri ON sp.id = pri.part_id " +
            "INNER JOIN part_request pr ON pri.request_id = pr.id " +
            "INNER JOIN repair_order ro ON pr.order_id = ro.id " +
            "WHERE ro.problem_type = #{problemType} " +
            "AND pri.consumed_qty > 0 " +
            "AND sp.deleted = 0 " +
            "GROUP BY sp.id " +
            "ORDER BY SUM(pri.consumed_qty) DESC " +
            "LIMIT 10")
    List<SparePart> findHistoricalPartsByProblemType(@Param("problemType") String problemType);
}
