create table if not exists chat_message_file (
    id bigint not null auto_increment primary key comment '关联主键 ID',
    message_id bigint not null comment '聊天消息 ID',
    file_id bigint not null comment '文件 ID',
    sort_order smallint not null default 0 comment '附件展示顺序',
    create_time datetime not null default current_timestamp comment '创建时间',
    unique key uk_message_file (message_id, file_id),
    key idx_message_file_message_order (message_id, sort_order, id),
    key idx_message_file_file (file_id)
) comment = '聊天消息文件关联表';
