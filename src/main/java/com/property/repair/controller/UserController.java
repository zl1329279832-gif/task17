package com.property.repair.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.property.repair.common.exception.BusinessException;
import com.property.repair.common.result.PageResult;
import com.property.repair.common.result.Result;
import com.property.repair.entity.SysUser;
import com.property.repair.mapper.SysUserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
public class UserController {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;

    public UserController(SysUserMapper sysUserMapper, PasswordEncoder passwordEncoder) {
        this.sysUserMapper = sysUserMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public Result<PageResult<SysUser>> list(@RequestParam(defaultValue = "1") Integer pageNum,
                                            @RequestParam(defaultValue = "10") Integer pageSize,
                                            @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(SysUser::getUsername, keyword)
                    .or().like(SysUser::getRealName, keyword)
                    .or().like(SysUser::getPhone, keyword));
        }
        wrapper.orderByDesc(SysUser::getCreatedAt);

        IPage<SysUser> page = sysUserMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        // Mask passwords
        page.getRecords().forEach(u -> u.setPassword(null));

        PageResult<SysUser> pageResult = PageResult.from(page);
        return Result.success(pageResult);
    }

    @PostMapping
    public Result<?> create(@RequestBody SysUser user) {
        SysUser existing = sysUserMapper.selectByUsername(user.getUsername());
        if (existing != null) {
            throw new BusinessException("用户名已存在");
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setStatus(1);
        sysUserMapper.insert(user);
        user.setPassword(null);

        return Result.success("用户创建成功", user);
    }

    @PutMapping("/{id}")
    public Result<?> update(@PathVariable Long id, @RequestBody SysUser user) {
        SysUser existing = sysUserMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("用户不存在");
        }

        if (StringUtils.hasText(user.getRealName())) {
            existing.setRealName(user.getRealName());
        }
        if (StringUtils.hasText(user.getPhone())) {
            existing.setPhone(user.getPhone());
        }
        if (user.getRole() != null) {
            existing.setRole(user.getRole());
        }
        if (user.getCommunityId() != null) {
            existing.setCommunityId(user.getCommunityId());
        }
        if (user.getBuildingId() != null) {
            existing.setBuildingId(user.getBuildingId());
        }
        if (StringUtils.hasText(user.getPassword())) {
            existing.setPassword(passwordEncoder.encode(user.getPassword()));
        }

        sysUserMapper.updateById(existing);
        existing.setPassword(null);

        return Result.success("用户更新成功", existing);
    }

    @PutMapping("/{id}/status")
    public Result<?> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        user.setStatus(status);
        sysUserMapper.updateById(user);

        String action = (status == 1) ? "启用" : "禁用";
        return Result.success("用户已" + action);
    }
}
