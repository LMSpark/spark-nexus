alter table work_order add column handler varchar(64) null;
alter table work_order add column handled_at datetime null;

create table message_notice (
  id bigint primary key auto_increment,
  community_id bigint not null,
  receiver_role varchar(32) not null,
  title varchar(128) not null,
  content varchar(500) not null,
  channel varchar(32) not null,
  status varchar(24) not null,
  created_at datetime not null
);

insert into message_notice(community_id, receiver_role, title, content, channel, status, created_at) values
(1, 'OWNER', '缴费提醒', '您有2026-06物业费待缴，请及时处理。', 'SMS_DEV', 'SENT', now()),
(1, 'COMMITTEE', '审批提醒', '门禁系统改造尾款等待审批。', 'IN_APP', 'SENT', now());
