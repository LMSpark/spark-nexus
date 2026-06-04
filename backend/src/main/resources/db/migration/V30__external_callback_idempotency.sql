alter table external_callback_receipt add column idempotency_key varchar(160) null;
alter table external_callback_receipt add column process_status varchar(32) not null default 'PROCESSED';

create index idx_external_callback_idempotency on external_callback_receipt(idempotency_key, process_status);
