package com.example.aichat.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aichat.system.entity.SysUserRole;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserRoleMapper extends BaseMapper<SysUserRole> {
    @Select("select user_id from sys_user_role where role_id = #{roleId}")
    List<Long> findUserIdsByRoleId(Long roleId);
}
