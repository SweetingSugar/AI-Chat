package com.example.aichat.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("sys_role")
public class SysRole {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String roleName;
    private String roleCode;
    private String description;
    private Integer sort;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public String getRoleName() { return roleName; } public void setRoleName(String roleName) { this.roleName = roleName; }
    public String getRoleCode() { return roleCode; } public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
    public String getDescription() { return description; } public void setDescription(String description) { this.description = description; }
    public Integer getSort() { return sort; } public void setSort(Integer sort) { this.sort = sort; }
    public Integer getStatus() { return status; } public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getCreateTime() { return createTime; } public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; } public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
