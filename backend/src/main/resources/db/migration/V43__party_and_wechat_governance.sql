create table neighborhood_party_activity (
  id bigint primary key auto_increment,
  community_id bigint not null,
  activity_no varchar(64) not null unique,
  activity_type varchar(32) not null,
  party_branch varchar(128) not null,
  title varchar(128) not null,
  organizer varchar(64),
  participant_count int not null default 0,
  party_member_count int not null default 0,
  activity_at datetime not null,
  status varchar(32) not null,
  summary varchar(800) not null,
  created_by varchar(64) not null,
  created_at datetime not null,
  updated_at datetime not null,
  index idx_neighborhood_party_community_status(community_id, status),
  index idx_neighborhood_party_type(activity_type)
);

insert into integration_config(adapter_code, provider_name, mode, endpoint_url, merchant_no, status, updated_at)
select 'WECHAT_MESSAGE', '微信小程序/公众号社区通知', 'dev', 'https://api.weixin.qq.com', null, 'ACTIVE', now()
where not exists (select 1 from integration_config where adapter_code = 'WECHAT_MESSAGE');

insert into message_notice(community_id, receiver_role, title, content, channel, status, created_at) values
(1, 'OWNER', '社区治理微信通知演示', '【阳光社区居民委员会】党建引领社区治理，物业服务提升联席会结果将在业主端同步公示。', 'WECHAT_GOV', 'SENT', now());

insert into integration_call_log(adapter_code, operation, request_summary, response_summary, status, trace_no, created_at)
select 'WECHAT_MESSAGE', 'SEND_COMMUNITY_NOTICE', 'communityId=1, receiverRole=OWNER, sponsor=阳光社区居民委员会', 'WECHAT_MESSAGE_DEV_SENT', 'SUCCESS', concat('WECHAT_MESSAGE-', replace(uuid(), '-', '')), now()
where not exists (
  select 1 from integration_call_log
  where adapter_code = 'WECHAT_MESSAGE' and operation = 'SEND_COMMUNITY_NOTICE'
);

insert into neighborhood_party_activity(community_id, activity_no, activity_type, party_branch, title, organizer,
                                        participant_count, party_member_count, activity_at, status, summary,
                                        created_by, created_at, updated_at) values
(1, 'NPA-202606-0001', 'PARTY_BRANCH_MEETING', '阳光社区党委第一党支部', '物业服务提升党建联席会', '居委会李主任', 28, 12, date_sub(now(), interval 3 day), 'DONE', '组织居委会、物业、业委会、党员楼栋长和居民代表围绕物业费公示、报修响应、公共收益公开形成三项整改清单。', 'neighborhood', now(), now()),
(1, 'NPA-202606-0002', 'PARTY_MEMBER_SERVICE', '阳光社区党委第二党支部', '党员先锋岗助老敲门行动', '网格员陈晨', 36, 18, date_add(now(), interval 2 day), 'PLANNED', '党员志愿者联合社区卫生服务站，对独居老人和困难家庭开展走访、健康提醒、餐食配送和用电安全检查。', 'neighborhood', now(), now()),
(1, 'NPA-202606-0003', 'RED_COUNCIL', '阳光社区大党委', '红色议事厅服务商准入评议', '社工周青', 22, 9, date_add(now(), interval 5 day), 'PLANNED', '围绕助餐、家政、维修等本地生活服务，引入党员居民代表、物业和市场监管资源，对服务商服务范围和退出机制进行评议。', 'neighborhood', now(), now());
