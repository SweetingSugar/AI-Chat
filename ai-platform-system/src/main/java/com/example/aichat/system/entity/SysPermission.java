package com.example.aichat.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("sys_permission")
public class SysPermission {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentId;
    private String permissionName;
    private String permissionCode;
    private Integer permissionType;
    private String path;
    private String component;
    private String icon;
    private Integer sort;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getParentId() { return parentId; } public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getPermissionName() { return permissionName; } public void setPermissionName(String permissionName) { this.permissionName = permissionName; }
    public String getPermissionCode() { return permissionCode; } public void setPermissionCode(String permissionCode) { this.permissionCode = permissionCode; }
    public Integer getPermissionType() { return permissionType; } public void setPermissionType(Integer permissionType) { this.permissionType = permissionType; }
    public String getPath() { return path; } public void setPath(String path) { this.path = path; }
    public String getComponent() { return component; } public void setComponent(String component) { this.component = component; }
    public String getIcon() { return icon; } public void setIcon(String icon) { this.icon = icon; }
    public Integer getSort() { return sort; } public void setSort(Integer sort) { this.sort = sort; }
    public Integer getStatus() { return status; } public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getCreateTime() { return createTime; } public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; } public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
