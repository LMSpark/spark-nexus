create table supervision_alert_rule (
  id bigint primary key auto_increment,
  rule_code varchar(40) not null unique,
  rule_name varchar(128) not null,
  level varchar(24) not null,
  threshold_amount decimal(14,2) null,
  threshold_days int null,
  status varchar(24) not null,
  created_at datetime not null
);

alter table risk_alert add column rule_code varchar(40) null;
alter table risk_alert add column target_type varchar(40) null;
alter table risk_alert add column target_id bigint null;
alter table risk_alert add column handled_by varchar(64) null;
alter table risk_alert add column resolved_at datetime null;

create index idx_risk_alert_rule_target on risk_alert(rule_code, target_type, target_id, status);

insert into supervision_alert_rule(rule_code, rule_name, level, threshold_amount, threshold_days, status, created_at) values
('LARGE_EXPENSE', '大额支出预警', 'HIGH', 50000.00, null, 'ACTIVE', now()),
('NO_PUBLIC_ANNOUNCEMENT', '长期未公示预警', 'MEDIUM', null, 30, 'ACTIVE', now()),
('LOW_PAYMENT_RATE', '缴费率偏低预警', 'MEDIUM', 80.00, null, 'ACTIVE', now());
