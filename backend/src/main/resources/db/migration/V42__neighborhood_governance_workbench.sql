create table neighborhood_case (
  id bigint primary key auto_increment,
  community_id bigint not null,
  case_no varchar(64) not null unique,
  case_type varchar(32) not null,
  source varchar(64) not null,
  title varchar(128) not null,
  description varchar(800) not null,
  grid_name varchar(64),
  location varchar(128),
  target_party varchar(64),
  priority varchar(24) not null,
  status varchar(32) not null,
  handler varchar(64),
  due_at datetime,
  closed_at datetime,
  created_by varchar(64) not null,
  created_at datetime not null,
  updated_at datetime not null,
  index idx_neighborhood_case_community_status(community_id, status),
  index idx_neighborhood_case_type(case_type)
);

create table neighborhood_case_event (
  id bigint primary key auto_increment,
  case_id bigint not null,
  event_type varchar(32) not null,
  operator varchar(64) not null,
  event_summary varchar(500) not null,
  created_at datetime not null,
  index idx_neighborhood_case_event_case(case_id, created_at)
);

create table neighborhood_care_visit (
  id bigint primary key auto_increment,
  community_id bigint not null,
  person_name varchar(64) not null,
  person_type varchar(32) not null,
  phone_mask varchar(32),
  building_room varchar(64),
  care_need varchar(300) not null,
  risk_level varchar(24) not null,
  last_visit_at datetime,
  next_visit_at datetime,
  status varchar(32) not null,
  handler varchar(64),
  created_at datetime not null,
  updated_at datetime not null,
  index idx_neighborhood_care_community_status(community_id, status)
);

create table neighborhood_resource (
  id bigint primary key auto_increment,
  community_id bigint not null,
  resource_type varchar(32) not null,
  organization_name varchar(128) not null,
  contact_name varchar(64),
  contact_phone varchar(32),
  service_scope varchar(500) not null,
  status varchar(32) not null,
  created_at datetime not null,
  updated_at datetime not null,
  index idx_neighborhood_resource_community(community_id, resource_type)
);

insert into app_user(username, password_hash, display_name, role, community_id)
select 'neighborhood', 'admin123', '阳光社区居委会主任', 'NEIGHBORHOOD', 1
where not exists (select 1 from app_user where username = 'neighborhood');

insert into tenant(tenant_code, tenant_name, tenant_type, parent_id, unified_credit_code,
                   contact_name, contact_phone, contact_phone_cipher, status, certification_status, created_at, updated_at)
select 'NEIGHBORHOOD-YANGGUANG', '阳光社区居民委员会', 'NEIGHBORHOOD', 3, null,
       '居委会经办人', '027-55550001', null, 'ACTIVE', 'APPROVED', now(), now()
where not exists (select 1 from tenant where tenant_code = 'NEIGHBORHOOD-YANGGUANG');

insert into user_tenant_relation(user_id, tenant_id, role_code, status, is_default, created_at)
select u.id, t.id, 'NEIGHBORHOOD', 'ACTIVE', 1, now()
from app_user u
join tenant t on t.tenant_code = 'NEIGHBORHOOD-YANGGUANG'
where u.username = 'neighborhood'
  and not exists (
    select 1 from user_tenant_relation r
    where r.user_id = u.id and r.tenant_id = t.id and r.role_code = 'NEIGHBORHOOD'
  );

insert into tenant_community_relation(tenant_id, community_id, relation_type, status, start_date, end_date, created_at)
select t.id, 1, 'NEIGHBORHOOD_GOVERN', 'ACTIVE', '2026-01-01', null, now()
from tenant t
where t.tenant_code = 'NEIGHBORHOOD-YANGGUANG'
  and not exists (
    select 1 from tenant_community_relation r
    where r.tenant_id = t.id and r.community_id = 1 and r.relation_type = 'NEIGHBORHOOD_GOVERN'
  );

insert into data_authorization(grantor_tenant_id, grantee_tenant_id, community_id, data_scope, permission_code, status, effective_from, effective_to, created_at)
select 1, t.id, 1, scope_name, permission_code, 'ACTIVE', now(), null, now()
from tenant t
cross join (
  select 'COMMUNITY_GOVERNANCE' scope_name union all select 'COMMUNITY_PROFILE' union all
  select 'HOUSE' union all select 'RESIDENT' union all select 'REPAIR' union all
  select 'COMPLAINT' union all select 'VOTE'
) scopes
cross join (
  select 'READ' permission_code union all select 'WRITE' union all select 'APPROVE' union all select 'EXPORT'
) permissions
where t.tenant_code = 'NEIGHBORHOOD-YANGGUANG'
  and not exists (
    select 1 from data_authorization a
    where a.grantee_tenant_id = t.id
      and a.community_id = 1
      and a.data_scope = scopes.scope_name
      and a.permission_code = permissions.permission_code
      and a.status = 'ACTIVE'
  );

insert into data_authorization(grantor_tenant_id, grantee_tenant_id, community_id, data_scope, permission_code, status, effective_from, effective_to, created_at)
select 1, r.tenant_id, r.community_id, 'COMMUNITY_GOVERNANCE', permission_code, 'ACTIVE', now(), null, now()
from tenant_community_relation r
cross join (
  select 'READ' permission_code union all select 'WRITE' union all select 'APPROVE' union all select 'EXPORT'
) permissions
where r.tenant_id in (2, 3)
  and not exists (
    select 1 from data_authorization a
    where a.grantee_tenant_id = r.tenant_id
      and a.community_id = r.community_id
      and a.data_scope = 'COMMUNITY_GOVERNANCE'
      and a.permission_code = permissions.permission_code
      and a.status = 'ACTIVE'
  );

insert into neighborhood_case(community_id, case_no, case_type, source, title, description,
                              grid_name, location, target_party, priority, status, handler,
                              due_at, closed_at, created_by, created_at, updated_at) values
(1, 'NHC-202606-0001', 'PUBLIC_OPINION', '居民随手拍', '东门早高峰非机动车乱停', '居民反映东门早高峰非机动车占用消防通道，需要物业、交警协同整治并公开反馈。', '阳光社区一网格', '鼎盛阳光一期东门', '物业/交警', 'HIGH', 'PROCESSING', '网格员王敏', date_add(now(), interval 18 hour), null, 'neighborhood', now(), now()),
(1, 'NHC-202606-0002', 'PROPERTY_MEDIATION', '居民议事群', '物业费公示争议调解', '部分业主对物业费使用公示口径有疑问，居委会组织物业、业委会和居民代表进行协商说明。', '阳光社区二网格', '党群服务中心议事室', '物业/业委会', 'NORMAL', 'CLOSED', '居委会李主任', date_add(now(), interval 2 day), now(), 'neighborhood', now(), now()),
(1, 'NHC-202606-0003', 'CARE_SERVICE', '网格巡查', '独居老人高温走访', '高温天气下对独居老人开展敲门行动，联动社区卫生服务站完成健康提醒和用电安全排查。', '阳光社区三网格', '1栋1单元1101', '社区卫生服务站/志愿队', 'HIGH', 'PENDING', '网格员陈晨', date_add(now(), interval 1 day), null, 'neighborhood', now(), now()),
(1, 'NHC-202606-0004', 'RESOURCE_LINKAGE', '居委会排查', '暑期托管公益资源对接', '链接辖区学校、志愿队和商户，为双职工家庭组织暑期托管报名与场地支持。', '阳光社区一网格', '社区活动室', '学校/志愿队/商户', 'NORMAL', 'PROCESSING', '居委会社工周青', date_add(now(), interval 3 day), null, 'neighborhood', now(), now());

insert into neighborhood_case_event(case_id, event_type, operator, event_summary, created_at)
select id, 'CREATE', created_by, concat('事项建档：', title), created_at from neighborhood_case
where case_no in ('NHC-202606-0001', 'NHC-202606-0002', 'NHC-202606-0003', 'NHC-202606-0004');

insert into neighborhood_case_event(case_id, event_type, operator, event_summary, created_at)
select id, 'COORDINATE', handler, '已纳入居委会多方协同台账，等待责任方反馈。', now() from neighborhood_case
where case_no in ('NHC-202606-0001', 'NHC-202606-0003', 'NHC-202606-0004');

insert into neighborhood_case_event(case_id, event_type, operator, event_summary, created_at)
select id, 'CLOSE', handler, '已组织协商说明并形成公示口径，居民代表确认闭环。', now() from neighborhood_case
where case_no = 'NHC-202606-0002';

insert into neighborhood_care_visit(community_id, person_name, person_type, phone_mask, building_room,
                                    care_need, risk_level, last_visit_at, next_visit_at, status, handler, created_at, updated_at) values
(1, '张阿姨', '独居老人', '138****1024', '1栋1单元1101', '高温走访、慢病用药提醒、燃气用电安全检查', 'HIGH', date_sub(now(), interval 5 day), date_add(now(), interval 2 day), 'FOLLOWING', '网格员陈晨', now(), now()),
(1, '李同学', '困境儿童', '139****2088', '2栋2单元0802', '暑期托管报名、心理关爱、公益课程对接', 'MEDIUM', date_sub(now(), interval 10 day), date_add(now(), interval 7 day), 'PLANNED', '社工周青', now(), now());

insert into neighborhood_resource(community_id, resource_type, organization_name, contact_name, contact_phone,
                                  service_scope, status, created_at, updated_at) values
(1, 'MEDICAL', '阳光社区卫生服务站', '刘医生', '027-66550001', '慢病随访、老人健康评估、义诊活动、应急转介', 'ACTIVE', now(), now()),
(1, 'POLICE', '关山派出所社区警务室', '赵警官', '027-66550002', '矛盾调解、反诈宣传、流动人口协查、治安联动', 'ACTIVE', now(), now()),
(1, 'VOLUNTEER', '阳光志愿服务队', '周队长', '138****6601', '助老探访、暑期托管、文明劝导、应急支援', 'ACTIVE', now(), now()),
(1, 'MERCHANT', '邻里便民服务联盟', '何经理', '138****6602', '助餐、家政、维修、团购和公益折扣服务', 'ACTIVE', now(), now());
