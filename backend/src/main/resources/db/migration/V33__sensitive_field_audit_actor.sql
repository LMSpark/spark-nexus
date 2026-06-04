alter table sensitive_field_audit add column actor varchar(64) not null default 'SYSTEM';
alter table sensitive_field_audit add column access_purpose varchar(120) not null default 'LEGACY_PROTECTION';

create index idx_sensitive_field_actor on sensitive_field_audit(actor, created_at);
