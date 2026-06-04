create table community_bank_config (
  id bigint primary key auto_increment,
  community_id bigint not null,
  bank_tenant_id bigint not null,
  service_type varchar(32) not null,
  fund_account_id bigint,
  merchant_no varchar(64) not null,
  status varchar(32) not null,
  created_at datetime not null,
  updated_at datetime not null,
  unique key uk_community_bank_service(community_id, bank_tenant_id, service_type)
);

create table payment_order (
  id bigint primary key auto_increment,
  bill_id bigint not null,
  community_id bigint not null,
  bank_config_id bigint,
  order_no varchar(64) not null unique,
  amount decimal(14,2) not null,
  status varchar(32) not null,
  prepay_id varchar(64),
  paid_at datetime,
  created_at datetime not null
);

create table reconciliation_record (
  id bigint primary key auto_increment,
  community_id bigint not null,
  bank_config_id bigint,
  reconcile_date date not null,
  system_amount decimal(14,2) not null,
  bank_amount decimal(14,2) not null,
  diff_amount decimal(14,2) not null,
  matched_count int not null,
  unmatched_count int not null,
  status varchar(32) not null,
  created_at datetime not null
);

insert into app_user(username, password_hash, display_name, role, community_id)
select 'bank', 'admin123', '银行对账员', 'BANK', 1
where not exists (select 1 from app_user where username = 'bank');

insert into user_tenant_relation(user_id, tenant_id, role_code, status, is_default, created_at)
select id, 5, 'BANK', 'ACTIVE', 1, now() from app_user where username = 'bank'
on duplicate key update status = values(status);

insert into community_bank_config(community_id, bank_tenant_id, service_type, fund_account_id, merchant_no, status, created_at, updated_at)
select 1, 5, 'COLLECTION', id, 'MCH-DSYG-001', 'ACTIVE', now(), now()
from fund_account where community_id = 1 and account_type = 'PROPERTY_SERVICE'
on duplicate key update status = values(status);

insert into community_bank_config(community_id, bank_tenant_id, service_type, fund_account_id, merchant_no, status, created_at, updated_at)
select 1, 5, 'SUPERVISION', id, 'SUP-DSYG-001', 'ACTIVE', now(), now()
from fund_account where community_id = 1 and account_type = 'PUBLIC_REVENUE'
on duplicate key update status = values(status);

insert into community_bank_config(community_id, bank_tenant_id, service_type, fund_account_id, merchant_no, status, created_at, updated_at)
select 2, 5, 'SUPERVISION', id, 'SUP-DSYG-002', 'ACTIVE', now(), now()
from fund_account where community_id = 2 and account_type = 'PUBLIC_REVENUE'
on duplicate key update status = values(status);

insert into data_authorization(grantor_tenant_id, grantee_tenant_id, community_id, data_scope, permission_code, status, effective_from, effective_to, created_at)
select 1, 5, community_id, 'PAYMENT', permission_code, 'ACTIVE', now(), null, now()
from community_bank_config
cross join (select 'READ' permission_code union all select 'WRITE') p
where not exists (
  select 1 from data_authorization a
  where a.grantee_tenant_id = 5 and a.community_id = community_bank_config.community_id
    and a.data_scope = 'PAYMENT' and a.permission_code = p.permission_code
);

insert into data_authorization(grantor_tenant_id, grantee_tenant_id, community_id, data_scope, permission_code, status, effective_from, effective_to, created_at)
select 1, 5, community_id, 'BANK_FLOW', permission_code, 'ACTIVE', now(), null, now()
from community_bank_config
cross join (select 'READ' permission_code union all select 'WRITE') p
where not exists (
  select 1 from data_authorization a
  where a.grantee_tenant_id = 5 and a.community_id = community_bank_config.community_id
    and a.data_scope = 'BANK_FLOW' and a.permission_code = p.permission_code
);
