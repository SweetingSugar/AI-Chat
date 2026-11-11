package com.example.aichat.tool.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.aichat.tool.enums.ToolAuthType;
import com.example.aichat.tool.enums.ToolDiscoveryStatus;

import java.time.LocalDateTime;

@TableName("tool_mcp_server")
public class ToolMcpServer {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String serverCode;
    private String serverName;
    private String endpointUrl;
    private ToolAuthType authType;
    private String authHeaderName;
    private String credentialCiphertext;
    private Integer credentialVersion;
    private String secretHeadersCiphertext;
    private Integer connectTimeout;
    private Integer readTimeout;
    private Integer status;
    @TableLogic
    private Integer deleted;
    private LocalDateTime lastDiscoveryTime;
    private ToolDiscoveryStatus lastDiscoveryStatus;
    private String lastDiscoveryError;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getServerCode() { return serverCode; }
    public void setServerCode(String serverCode) { this.serverCode = serverCode; }
    public String getServerName() { return serverName; }
    public void setServerName(String serverName) { this.serverName = serverName; }
    public String getEndpointUrl() { return endpointUrl; }
    public void setEndpointUrl(String endpointUrl) { this.endpointUrl = endpointUrl; }
    public ToolAuthType getAuthType() { return authType; }
    public void setAuthType(ToolAuthType authType) { this.authType = authType; }
    public String getAuthHeaderName() { return authHeaderName; }
    public void setAuthHeaderName(String authHeaderName) { this.authHeaderName = authHeaderName; }
    public String getCredentialCiphertext() { return credentialCiphertext; }
    public void setCredentialCiphertext(String credentialCiphertext) { this.credentialCiphertext = credentialCiphertext; }
    public Integer getCredentialVersion() { return credentialVersion; }
    public void setCredentialVersion(Integer credentialVersion) { this.credentialVersion = credentialVersion; }
    public String getSecretHeadersCiphertext() { return secretHeadersCiphertext; }
    public void setSecretHeadersCiphertext(String secretHeadersCiphertext) { this.secretHeadersCiphertext = secretHeadersCiphertext; }
    public Integer getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Integer connectTimeout) { this.connectTimeout = connectTimeout; }
    public Integer getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Integer readTimeout) { this.readTimeout = readTimeout; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
    public LocalDateTime getLastDiscoveryTime() { return lastDiscoveryTime; }
    public void setLastDiscoveryTime(LocalDateTime lastDiscoveryTime) { this.lastDiscoveryTime = lastDiscoveryTime; }
    public ToolDiscoveryStatus getLastDiscoveryStatus() { return lastDiscoveryStatus; }
    public void setLastDiscoveryStatus(ToolDiscoveryStatus lastDiscoveryStatus) { this.lastDiscoveryStatus = lastDiscoveryStatus; }
    public String getLastDiscoveryError() { return lastDiscoveryError; }
    public void setLastDiscoveryError(String lastDiscoveryError) { this.lastDiscoveryError = lastDiscoveryError; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
