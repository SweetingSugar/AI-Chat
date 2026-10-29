alter table ai_model
    add column support_vision tinyint not null default 0 after support_stream;
