create table if not exists chat_message_knowledge_base (
    id bigint not null auto_increment primary key comment '关联主键 ID',
    message_id bigint not null comment '聊天消息 ID',
    knowledge_base_id bigint not null comment '知识库 ID',
    create_time datetime not null default current_timestamp comment '创建时间',
    unique key uk_chat_message_knowledge_base (message_id, knowledge_base_id),
    key idx_chat_message_knowledge_base_message (message_id),
    key idx_chat_message_knowledge_base_base (knowledge_base_id)
) comment = '聊天消息选择的知识库';

create table if not exists chat_message_knowledge_source (
    id bigint not null auto_increment primary key comment '知识来源主键 ID',
    message_id bigint not null comment '聊天消息 ID',
    knowledge_base_id bigint not null comment '知识库 ID',
    document_id bigint not null comment '知识库文档 ID',
    chunk_id bigint not null comment '文档分块 ID',
    title varchar(255) not null comment '文档分块标题',
    content text not null comment '文档分块内容',
    score double not null comment '检索相关性得分',
    rank_no int not null comment '检索结果排名，从 1 开始',
    create_time datetime not null default current_timestamp comment '创建时间',
    key idx_chat_message_knowledge_source_message (message_id, rank_no),
    key idx_chat_message_knowledge_source_base (knowledge_base_id)
) comment = '聊天消息引用的知识来源';
