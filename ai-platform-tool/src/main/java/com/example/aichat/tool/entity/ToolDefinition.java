package com.example.aichat.tool.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.aichat.tool.enums.ToolAuthType;
import com.example.aichat.tool.enums.ToolAvailability;
import com.example.aichat.tool.enums.ToolSource;
import com.example.aichat.tool.enums.ToolType;

import java.time.LocalDateTime;

@TableName("tool_definition")
public class ToolDefinition {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String toolCode;
    private String toolName;
    private String description;
    private ToolType toolType;
    private ToolSource source;
    private Integer status;
    private ToolAvailability availability;
    private Long mcpServerId;
    private String remoteToolName;
    private String httpConfig;
    private ToolAuthType authType;
    private String authHeaderName;
    private String credentialCiphertext;
    private Integer credentialVersion;
    private String secretHeadersCiphertext;
    private String inputSchema;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getToolCode() { return toolCode; }
    public void setToolCode(String toolCode) { this.toolCode = toolCode; }
    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public ToolType getToolType() { return toolType; }
    public void setToolType(ToolType toolType) { this.toolType = toolType; }
    public ToolSource getSource() { return source; }
    public void setSource(ToolSource source) { this.source = source; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public ToolAvailability getAvailability() { return availability; }
    public void setAvailability(ToolAvailability availability) { this.availability = availability; }
    public Long getMcpServerId() { return mcpServerId; }
    public void setMcpServerId(Long mcpServerId) { this.mcpServerId = mcpServerId; }
    public String getRemoteToolName() { return remoteToolName; }
    public void setRemoteToolName(String remoteToolName) { this.remoteToolName = remoteToolName; }
    public String getHttpConfig() { return httpConfig; }
    public void setHttpConfig(String httpConfig) { this.httpConfig = httpConfig; }
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
    public String getInputSchema() { return inputSchema; }
    public void setInputSchema(String inputSchema) { this.inputSchema = inputSchema; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
