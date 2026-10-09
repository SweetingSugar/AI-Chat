package com.example.aichat.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aichat.system.entity.SysRole;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RoleMapper extends BaseMapper<SysRole> {
    List<String> findRoleCodesByUserId(Long userId);
}
