create table integration_config (
  id bigint primary key auto_increment,
  adapter_code varchar(40) not null unique,
  provider_name varchar(80) not null,
  mode varchar(24) not null,
  endpoint_url varchar(255) null,
  merchant_no varchar(80) null,
  status varchar(24) not null,
  updated_at datetime not null
);

create table integration_call_log (
  id bigint primary key auto_increment,
  adapter_code varchar(40) not null,
  operation varchar(80) not null,
  request_summary varchar(1000) not null,
  response_summary varchar(1000) not null,
  status varchar(24) not null,
  trace_no varchar(80) not null,
  created_at datetime not null
);

create index idx_integration_call_log_adapter on integration_call_log(adapter_code, created_at);

insert into integration_config(adapter_code, provider_name, mode, endpoint_url, merchant_no, status, updated_at) values
('WECHAT_PAY', '微信支付商户平台', 'dev', 'https://api.mch.weixin.qq.com', 'MCH-DEV-DSYG', 'ACTIVE', now()),
('BANK_DIRECT', '银行直连监管接口', 'dev', 'https://bank-gateway.example.local', 'BANK-DEV-DSYG', 'ACTIVE', now()),
('SMS', '短信通知网关', 'dev', 'https://sms-gateway.example.local', null, 'ACTIVE', now()),
('IDENTITY', '实名认证核验服务', 'dev', 'https://identity.example.local', null, 'ACTIVE', now());
