create table if not exists file_metadata (
    id bigint not null auto_increment primary key comment '文件主键 ID',
    user_id bigint not null comment '上传用户 ID',
    bucket_name varchar(100) not null comment 'MinIO Bucket 名称',
    object_key varchar(512) not null comment 'MinIO Object Key',
    original_name varchar(255) not null comment '原始文件名',
    content_type varchar(150) not null comment '服务端校验后的 MIME 类型',
    file_size bigint not null comment '文件大小，单位字节',
    file_hash varchar(128) null comment '文件 SHA-256 摘要',
    status varchar(32) not null default 'ACTIVE'
        comment '文件状态：ACTIVE、DELETING、DELETE_FAILED、DELETED',
    create_time datetime not null default current_timestamp comment '创建时间',
    update_time datetime not null default current_timestamp
        on update current_timestamp comment '更新时间',
    deleted tinyint not null default 0 comment '逻辑删除标记：0 未删除，1 已删除',
    unique key uk_file_object (bucket_name, object_key),
    key idx_file_user_time (user_id, create_time),
    key idx_file_user_status_time (user_id, status, create_time),
    key idx_file_status_time (status, create_time)
) comment = '文件元数据表';
