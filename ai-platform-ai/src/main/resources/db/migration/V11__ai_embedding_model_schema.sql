alter table ai_model
    add column embedding_dimension int null after model_type;

update ai_model
set model_type = upper(model_type)
where model_type is not null;

alter table ai_model
    add constraint chk_ai_model_embedding_dimension
    check ((model_type = 'EMBEDDING' and embedding_dimension is not null and embedding_dimension > 0)
        or (model_type <> 'EMBEDDING' and embedding_dimension is null));
