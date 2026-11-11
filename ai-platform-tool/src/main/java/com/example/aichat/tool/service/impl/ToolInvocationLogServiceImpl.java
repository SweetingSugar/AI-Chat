package com.example.aichat.tool.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.aichat.common.api.PageResult;
import com.example.aichat.tool.dto.management.ToolInvocationQuery;
import com.example.aichat.tool.dto.management.ToolInvocationView;
import com.example.aichat.tool.entity.ToolInvocationLog;
import com.example.aichat.tool.mapper.ToolInvocationLogMapper;
import com.example.aichat.tool.service.ToolInvocationLogService;
import org.springframework.stereotype.Service;

@Service
public class ToolInvocationLogServiceImpl implements ToolInvocationLogService {
    private final ToolInvocationLogMapper mapper;

    public ToolInvocationLogServiceImpl(ToolInvocationLogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void record(ToolInvocationLog log) {
        mapper.insert(log);
    }

    @Override
    public PageResult<ToolInvocationView> page(ToolInvocationQuery query) {
        QueryWrapper<ToolInvocationLog> wrapper = new QueryWrapper<>();
        if (query.getToolId() != null) {
            wrapper.eq("tool_id", query.getToolId());
        }
        if (query.getStatus() != null) {
            wrapper.eq("status", query.getStatus().name());
        }
        wrapper.orderByDesc("start_time").orderByDesc("id");
        long current = query.getPage() == null ? 1 : Math.max(1, query.getPage());
        long size = query.getSize() == null ? 20 : Math.min(Math.max(1, query.getSize()), 100);
        return PageResult.from(mapper.selectPage(new Page<>(current, size), wrapper), this::toView);
    }

    private ToolInvocationView toView(ToolInvocationLog log) {
        return new ToolInvocationView(log.getId(), log.getRequestId(), log.getToolId(), log.getToolCode(),
                log.getUserId(), log.getToolType(), log.getStatus(), log.getDurationMs(), log.getErrorCode(),
                log.getErrorMessage(), log.getStartTime(), log.getEndTime(), log.getCreateTime());
    }
}
