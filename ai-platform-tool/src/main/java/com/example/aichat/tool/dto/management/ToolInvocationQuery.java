package com.example.aichat.tool.dto.management;

import com.example.aichat.tool.enums.ToolInvocationStatus;

public class ToolInvocationQuery {
    private Integer page = 1;
    private Integer size = 20;
    private Long toolId;
    private ToolInvocationStatus status;

    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }
    public Integer getSize() { return size; }
    public void setSize(Integer size) { this.size = size; }
    public Long getToolId() { return toolId; }
    public void setToolId(Long toolId) { this.toolId = toolId; }
    public ToolInvocationStatus getStatus() { return status; }
    public void setStatus(ToolInvocationStatus status) { this.status = status; }
}
