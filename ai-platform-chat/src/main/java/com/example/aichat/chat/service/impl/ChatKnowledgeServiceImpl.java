package com.example.aichat.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.aichat.chat.entity.ChatMessageKnowledgeBase;
import com.example.aichat.chat.entity.ChatMessageKnowledgeSource;
import com.example.aichat.chat.mapper.ChatMessageKnowledgeBaseMapper;
import com.example.aichat.chat.mapper.ChatMessageKnowledgeSourceMapper;
import com.example.aichat.chat.service.ChatKnowledgeService;
import com.example.aichat.chat.vo.ChatKnowledgeSourceVO;
import com.example.aichat.knowledge.retrieval.KnowledgeContext;
import com.example.aichat.knowledge.retrieval.KnowledgeSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ChatKnowledgeServiceImpl implements ChatKnowledgeService {
    private final ChatMessageKnowledgeBaseMapper baseMapper;
    private final ChatMessageKnowledgeSourceMapper sourceMapper;

    public ChatKnowledgeServiceImpl(ChatMessageKnowledgeBaseMapper baseMapper,
                                    ChatMessageKnowledgeSourceMapper sourceMapper) {
        this.baseMapper = baseMapper;
        this.sourceMapper = sourceMapper;
    }

    @Override
    @Transactional
    public void bindKnowledgeBases(Long messageId, List<Long> knowledgeBaseIds) {
        baseMapper.delete(new QueryWrapper<ChatMessageKnowledgeBase>().eq("message_id", messageId));
        if (knowledgeBaseIds == null) return;
        knowledgeBaseIds.stream().filter(id -> id != null).distinct().forEach(baseId -> {
            ChatMessageKnowledgeBase relation = new ChatMessageKnowledgeBase();
            relation.messageId = messageId;
            relation.knowledgeBaseId = baseId;
            baseMapper.insert(relation);
        });
    }

    @Override
    public List<Long> findKnowledgeBaseIds(Long messageId) {
        return baseMapper.selectKnowledgeBaseIds(messageId);
    }

    @Override
    @Transactional
    public void saveSources(Long messageId, KnowledgeContext context) {
        sourceMapper.delete(new QueryWrapper<ChatMessageKnowledgeSource>().eq("message_id", messageId));
        if (context == null) return;
        for (KnowledgeSource source : context.sources()) {
            ChatMessageKnowledgeSource entity = new ChatMessageKnowledgeSource();
            entity.messageId = messageId;
            entity.knowledgeBaseId = source.knowledgeBaseId();
            entity.documentId = source.documentId();
            entity.chunkId = source.chunkId();
            entity.title = source.title();
            entity.content = source.content();
            entity.score = source.score();
            entity.rankNo = source.rank();
            sourceMapper.insert(entity);
        }
    }

    @Override
    public Map<Long, List<ChatKnowledgeSourceVO>> findSourcesByMessageIds(List<Long> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) return Map.of();
        Map<Long, List<ChatKnowledgeSourceVO>> result = new LinkedHashMap<>();
        for (ChatMessageKnowledgeSource source : sourceMapper.selectByMessageIds(messageIds)) {
            result.computeIfAbsent(source.messageId, ignored -> new java.util.ArrayList<>())
                    .add(new ChatKnowledgeSourceVO(source.knowledgeBaseId, source.documentId,
                            source.chunkId, source.title, source.content, source.score, source.rankNo));
        }
        return result;
    }
}
