package com.property.repair.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.property.repair.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT u.* FROM sys_user u " +
            "INNER JOIN sys_worker_skill ws ON u.id = ws.worker_id " +
            "WHERE u.role = 'WORKER' AND u.status = 1 " +
            "AND ws.problem_type = #{problemType} " +
            "AND (u.community_id = #{communityId} OR u.community_id IS NULL)")
    List<User> findWorkersBySkillAndCommunity(
            @Param("problemType") String problemType,
            @Param("communityId") Long communityId);
}
