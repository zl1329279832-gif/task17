package com.property.repair.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.property.repair.common.enums.RoleType;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    private String username;

    private String password;

    private String realName;

    private String phone;

    @TableField("role")
    private RoleType role;

    private Long communityId;

    private Long buildingId;

    private String unitNumber;

    @TableField("online_status")
    private Integer onlineStatus = 0;

    @TableField("status")
    private Integer status = 1;
}
