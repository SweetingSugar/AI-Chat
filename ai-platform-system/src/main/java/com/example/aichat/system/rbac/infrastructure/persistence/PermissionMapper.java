package com.example.aichat.system.rbac.infrastructure.persistence;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PermissionMapper {
    @Select("select distinct p.code from sys_permission p join sys_role_permission rp on rp.permission_id = p.id join sys_user_role ur on ur.role_id = rp.role_id where ur.user_id = #{userId}")
    List<String> selectPermissionCodesByUserId(long userId);

    @Select("select distinct r.code from sys_role r join sys_user_role ur on ur.role_id = r.id where ur.user_id = #{userId}")
    List<String> selectRoleCodesByUserId(long userId);
}
