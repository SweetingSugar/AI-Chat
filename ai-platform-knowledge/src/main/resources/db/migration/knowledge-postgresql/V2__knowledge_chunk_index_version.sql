alter table knowledge_chunk
    add column if not exists index_version bigint;

update knowledge_chunk c
set index_version = d.active_index_version
from knowledge_document d
where c.document_id = d.id and c.index_version is null;

alter table knowledge_chunk
    alter column index_version set not null;

drop index if exists uk_knowledge_chunk_order;
create unique index uk_knowledge_chunk_order
    on knowledge_chunk (document_id, index_version, chunk_index);

comment on column knowledge_chunk.index_version is '分块所属索引版本';
