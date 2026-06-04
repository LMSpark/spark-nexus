alter table payment_order add column pay_channel varchar(32) not null default 'WECHAT_PAY';
alter table payment_order add column provider_order_no varchar(96) null;
alter table payment_order add column callback_payload varchar(1000) null;
alter table payment_order add column callback_at datetime null;

create index idx_payment_order_channel_status on payment_order(pay_channel, status, created_at);

insert into integration_config(adapter_code, provider_name, mode, endpoint_url, merchant_no, status, updated_at)
select 'ALIPAY', '支付宝支付', 'dev', 'https://openapi.alipay.com/gateway.do', null, 'ACTIVE', now()
where not exists (select 1 from integration_config where adapter_code = 'ALIPAY');
