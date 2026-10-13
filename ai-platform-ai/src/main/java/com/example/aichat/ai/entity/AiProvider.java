package com.example.aichat.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.aichat.ai.enums.AiProviderType;

import java.time.LocalDateTime;

@TableName("ai_provider")
public class AiProvider {
    @TableId(type = IdType.AUTO)
    public Long id;
    public String providerCode;
    public String providerName;
    public AiProviderType providerType;
    public String baseUrl;
    public String apiKeyCiphertext;
    public Integer credentialVersion;
    public Integer connectTimeout;
    public Integer readTimeout;
    public Integer status;
    public Integer sort;
    public String remark;
    public Long createBy;
    public LocalDateTime createTime;
    public Long updateBy;
    public LocalDateTime updateTime;
    @TableLogic
    public Integer deleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getProviderCode() { return providerCode; }
    public void setProviderCode(String providerCode) { this.providerCode = providerCode; }
    public String getProviderName() { return providerName; }
    public void setProviderName(String providerName) { this.providerName = providerName; }
    public AiProviderType getProviderType() { return providerType; }
    public void setProviderType(AiProviderType providerType) { this.providerType = providerType; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKeyCiphertext() { return apiKeyCiphertext; }
    public void setApiKeyCiphertext(String apiKeyCiphertext) { this.apiKeyCiphertext = apiKeyCiphertext; }
    public Integer getCredentialVersion() { return credentialVersion; }
    public void setCredentialVersion(Integer credentialVersion) { this.credentialVersion = credentialVersion; }
    public Integer getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Integer connectTimeout) { this.connectTimeout = connectTimeout; }
    public Integer getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Integer readTimeout) { this.readTimeout = readTimeout; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Long getCreateBy() { return createBy; }
    public void setCreateBy(Long createBy) { this.createBy = createBy; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public Long getUpdateBy() { return updateBy; }
    public void setUpdateBy(Long updateBy) { this.updateBy = updateBy; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
