alter table owner add column phone_cipher text null;
alter table owner add column identity_cipher text null;

alter table house add column owner_phone_cipher text null;

alter table registration_application add column applicant_phone_cipher text null;
alter table registration_application add column payload_cipher text null;

alter table tenant add column contact_phone_cipher text null;

create table sensitive_field_audit (
    id bigint primary key auto_increment,
    target_table varchar(64) not null,
    target_id bigint not null,
    field_name varchar(64) not null,
    protection varchar(32) not null,
    created_at datetime not null
);

insert into sensitive_field_audit(target_table, target_id, field_name, protection, created_at)
select 'owner', id, 'phone', 'MASKED_LEGACY', now() from owner;

insert into sensitive_field_audit(target_table, target_id, field_name, protection, created_at)
select 'owner', id, 'identity', 'MASKED_LEGACY', now() from owner;
