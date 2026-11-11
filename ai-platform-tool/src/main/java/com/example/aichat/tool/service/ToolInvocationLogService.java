package com.example.aichat.tool.service;

import com.example.aichat.common.api.PageResult;
import com.example.aichat.tool.dto.management.ToolInvocationQuery;
import com.example.aichat.tool.dto.management.ToolInvocationView;
import com.example.aichat.tool.entity.ToolInvocationLog;

public interface ToolInvocationLogService {
    void record(ToolInvocationLog log);

    PageResult<ToolInvocationView> page(ToolInvocationQuery query);
}
