package com.example.aichat.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aichat.system.entity.SysPermission;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PermissionMapper extends BaseMapper<SysPermission> {
    @Select("select distinct p.permission_code from sys_permission p join sys_role_permission rp on rp.permission_id = p.id join sys_user_role ur on ur.role_id = rp.role_id join sys_role r on r.id = ur.role_id where ur.user_id = #{userId} and p.status = 1 and r.status = 1 and p.permission_code is not null")
    List<String> findPermissionCodesByUserId(Long userId);
}
