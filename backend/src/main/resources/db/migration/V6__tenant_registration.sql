create table tenant (
  id bigint primary key auto_increment,
  tenant_code varchar(64) not null unique,
  tenant_name varchar(128) not null,
  tenant_type varchar(32) not null,
  parent_id bigint,
  unified_credit_code varchar(64),
  contact_name varchar(64),
  contact_phone varchar(32),
  status varchar(32) not null,
  certification_status varchar(32) not null,
  created_at datetime not null,
  updated_at datetime not null
);

create table user_tenant_relation (
  id bigint primary key auto_increment,
  user_id bigint not null,
  tenant_id bigint not null,
  role_code varchar(64) not null,
  status varchar(32) not null,
  is_default tinyint not null default 0,
  created_at datetime not null,
  unique key uk_user_tenant_role(user_id, tenant_id, role_code)
);

create table tenant_community_relation (
  id bigint primary key auto_increment,
  tenant_id bigint not null,
  community_id bigint not null,
  relation_type varchar(32) not null,
  status varchar(32) not null,
  start_date date,
  end_date date,
  created_at datetime not null,
  unique key uk_tenant_community_relation(tenant_id, community_id, relation_type)
);

create table data_authorization (
  id bigint primary key auto_increment,
  grantor_tenant_id bigint not null,
  grantee_tenant_id bigint not null,
  community_id bigint not null,
  data_scope varchar(64) not null,
  permission_code varchar(32) not null,
  status varchar(32) not null,
  effective_from datetime not null,
  effective_to datetime,
  created_at datetime not null
);

create table registration_application (
  id bigint primary key auto_increment,
  application_no varchar(64) not null unique,
  applicant_type varchar(32) not null,
  applicant_name varchar(128) not null,
  applicant_phone varchar(32) not null,
  target_type varchar(32) not null,
  target_id bigint,
  payload_json text not null,
  status varchar(32) not null,
  submitted_at datetime not null,
  reviewed_by varchar(64),
  reviewed_at datetime,
  review_comment varchar(500)
);

create table resident_house_relation (
  id bigint primary key auto_increment,
  user_id bigint not null,
  house_id bigint not null,
  resident_type varchar(32) not null,
  verification_status varchar(32) not null,
  is_primary tinyint not null default 0,
  start_date date,
  end_date date,
  created_at datetime not null,
  unique key uk_user_house_type(user_id, house_id, resident_type)
);

insert into tenant(tenant_code, tenant_name, tenant_type, parent_id, unified_credit_code, contact_name, contact_phone, status, certification_status, created_at, updated_at) values
('PLATFORM-DSYG', '鼎盛阳光平台运营方', 'PLATFORM', null, null, '平台管理员', '027-00000000', 'ACTIVE', 'APPROVED', now(), now()),
('GOV-HONGSHAN', '洪山区住建局', 'GOVERNMENT', null, null, '政府监管员', '027-11111111', 'ACTIVE', 'APPROVED', now(), now()),
('GOV-GUANSHAN', '关山街道办事处', 'GOVERNMENT', 2, null, '街道审核员', '027-22222222', 'ACTIVE', 'APPROVED', now(), now()),
('PROPERTY-DSYG', '鼎盛阳光物业服务有限公司', 'PROPERTY', null, '91420100DEMO000001', '物业经理', '138****8888', 'ACTIVE', 'APPROVED', now(), now()),
('BANK-HANKOU', '汉口银行光谷支行', 'BANK', null, '91420100BANK000001', '银行客户经理', '027-33333333', 'ACTIVE', 'APPROVED', now(), now()),
('COMMITTEE-DSYG-1', '鼎盛阳光一期业主委员会', 'COMMITTEE', null, null, '业委会主任', '139****0001', 'ACTIVE', 'APPROVED', now(), now());

insert into user_tenant_relation(user_id, tenant_id, role_code, status, is_default, created_at)
select id, 1, 'ADMIN', 'ACTIVE', 1, now() from app_user where username = 'admin';

insert into user_tenant_relation(user_id, tenant_id, role_code, status, is_default, created_at)
select id, 2, 'GOVERNMENT', 'ACTIVE', 1, now() from app_user where username = 'gov';

insert into user_tenant_relation(user_id, tenant_id, role_code, status, is_default, created_at)
select id, 3, 'STREET', 'ACTIVE', 1, now() from app_user where username = 'street';

insert into user_tenant_relation(user_id, tenant_id, role_code, status, is_default, created_at)
select id, 6, 'COMMITTEE', 'ACTIVE', 1, now() from app_user where username = 'committee';

insert into user_tenant_relation(user_id, tenant_id, role_code, status, is_default, created_at)
select id, 4, 'PROPERTY', 'ACTIVE', 1, now() from app_user where username = 'property';

insert into user_tenant_relation(user_id, tenant_id, role_code, status, is_default, created_at)
select id, 6, 'OWNER', 'ACTIVE', 1, now() from app_user where username = 'owner';

insert into tenant_community_relation(tenant_id, community_id, relation_type, status, start_date, end_date, created_at) values
(2, 1, 'SUPERVISION', 'ACTIVE', '2026-01-01', null, now()),
(2, 2, 'SUPERVISION', 'ACTIVE', '2026-01-01', null, now()),
(2, 3, 'SUPERVISION', 'ACTIVE', '2026-01-01', null, now()),
(3, 1, 'JURISDICTION', 'ACTIVE', '2026-01-01', null, now()),
(3, 2, 'JURISDICTION', 'ACTIVE', '2026-01-01', null, now()),
(4, 1, 'PROPERTY_SERVICE', 'ACTIVE', '2026-01-01', null, now()),
(4, 2, 'PROPERTY_SERVICE', 'ACTIVE', '2026-01-01', null, now()),
(5, 1, 'BANK_COLLECTION', 'ACTIVE', '2026-01-01', null, now()),
(5, 1, 'BANK_SUPERVISION', 'ACTIVE', '2026-01-01', null, now()),
(5, 2, 'BANK_SUPERVISION', 'ACTIVE', '2026-01-01', null, now()),
(6, 1, 'COMMITTEE_GOVERN', 'ACTIVE', '2026-01-01', null, now());

insert into data_authorization(grantor_tenant_id, grantee_tenant_id, community_id, data_scope, permission_code, status, effective_from, effective_to, created_at)
select 1, 2, id, scope_name, permission_code, 'ACTIVE', now(), null, now()
from community
cross join (
  select 'COMMUNITY_PROFILE' scope_name union all select 'HOUSE' union all select 'RESIDENT' union all
  select 'BILLING' union all select 'PAYMENT' union all select 'BANK_FLOW' union all
  select 'PUBLIC_REVENUE' union all select 'EXPENSE' union all select 'COMPLAINT' union all
  select 'REPAIR' union all select 'VOTE' union all select 'AUDIT'
) scopes
cross join (
  select 'READ' permission_code union all select 'EXPORT'
) permissions;

insert into data_authorization(grantor_tenant_id, grantee_tenant_id, community_id, data_scope, permission_code, status, effective_from, effective_to, created_at)
select 1, 3, id, scope_name, permission_code, 'ACTIVE', now(), null, now()
from community
cross join (
  select 'COMMUNITY_PROFILE' scope_name union all select 'HOUSE' union all select 'BILLING' union all
  select 'BANK_FLOW' union all select 'PUBLIC_REVENUE' union all select 'EXPENSE' union all
  select 'COMPLAINT' union all select 'REPAIR' union all select 'VOTE'
) scopes
cross join (
  select 'READ' permission_code union all select 'APPROVE'
) permissions
where id in (1, 2);

insert into data_authorization(grantor_tenant_id, grantee_tenant_id, community_id, data_scope, permission_code, status, effective_from, effective_to, created_at)
select 1, 4, id, scope_name, permission_code, 'ACTIVE', now(), null, now()
from community
cross join (
  select 'COMMUNITY_PROFILE' scope_name union all select 'HOUSE' union all select 'RESIDENT' union all
  select 'BILLING' union all select 'PAYMENT' union all select 'REPAIR' union all select 'COMPLAINT'
) scopes
cross join (
  select 'READ' permission_code union all select 'WRITE'
) permissions
where id in (1, 2);

insert into data_authorization(grantor_tenant_id, grantee_tenant_id, community_id, data_scope, permission_code, status, effective_from, effective_to, created_at)
select 1, 5, id, scope_name, permission_code, 'ACTIVE', now(), null, now()
from community
cross join (
  select 'PAYMENT' scope_name union all select 'BANK_FLOW'
) scopes
cross join (
  select 'READ' permission_code union all select 'WRITE'
) permissions
where id in (1, 2);

insert into data_authorization(grantor_tenant_id, grantee_tenant_id, community_id, data_scope, permission_code, status, effective_from, effective_to, created_at)
select 1, 6, 1, scope_name, permission_code, 'ACTIVE', now(), null, now()
from (
  select 'COMMUNITY_PROFILE' scope_name union all select 'PUBLIC_REVENUE' union all select 'EXPENSE' union all
  select 'VOTE' union all select 'REPAIR' union all select 'COMPLAINT' union all select 'BANK_FLOW'
) scopes
cross join (
  select 'READ' permission_code union all select 'APPROVE'
) permissions;

insert into resident_house_relation(user_id, house_id, resident_type, verification_status, is_primary, start_date, end_date, created_at)
select u.id, o.house_id, 'OWNER', 'APPROVED', 1, '2026-01-01', null, now()
from app_user u
join owner o on o.username = u.username
where u.username = 'owner';

insert into registration_application(application_no, applicant_type, applicant_name, applicant_phone, target_type, target_id, payload_json, status, submitted_at, reviewed_by, reviewed_at, review_comment) values
('REG-20260601-0001', 'PROPERTY', '武汉新城物业服务有限公司', '13800000001', 'TENANT', null, '{"tenantType":"PROPERTY","tenantName":"武汉新城物业服务有限公司","unifiedCreditCode":"91420100NEW000001"}', 'PENDING', now(), null, null, null),
('REG-20260601-0002', 'BANK', '建设银行关山支行', '13800000002', 'TENANT', null, '{"tenantType":"BANK","tenantName":"建设银行关山支行","unifiedCreditCode":"91420100BANK000002"}', 'PENDING', now(), null, null, null),
('REG-20260601-0003', 'OWNER', '张女士', '13800001024', 'RESIDENT_HOUSE', 1, '{"communityName":"鼎盛阳光一期","building":"1栋","roomNo":"1101","residentType":"OWNER"}', 'APPROVED', now(), 'admin', now(), '演示数据已审核');
