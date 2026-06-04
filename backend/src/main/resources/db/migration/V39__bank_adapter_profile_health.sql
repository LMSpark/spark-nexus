create table bank_adapter_profile (
    id bigint primary key auto_increment,
    bank_code varchar(64) not null unique,
    bank_name varchar(120) not null,
    bank_tenant_id bigint,
    api_base_url varchar(255) not null,
    sign_algorithm varchar(64) not null,
    callback_algorithm varchar(64) not null,
    statement_mode varchar(64) not null,
    disbursement_mode varchar(64) not null,
    status varchar(32) not null,
    created_at datetime not null,
    updated_at datetime not null
);

create table bank_adapter_health (
    id bigint primary key auto_increment,
    bank_code varchar(64) not null,
    check_item varchar(120) not null,
    check_result varchar(32) not null,
    latency_ms int not null,
    evidence varchar(500) not null,
    checked_at datetime not null
);

create index idx_bank_adapter_health_code on bank_adapter_health(bank_code, checked_at);

insert into bank_adapter_profile(bank_code, bank_name, bank_tenant_id, api_base_url, sign_algorithm,
                                 callback_algorithm, statement_mode, disbursement_mode, status, created_at, updated_at) values
('HANKOU_BANK', '汉口银行监管直连', 5, 'mock://bank/hankou', 'HMAC_SHA256', 'HMAC_SHA256', 'SFTP_STATEMENT', 'API_DISBURSEMENT', 'ACTIVE', now(), now()),
('CCB_WUHAN', '建设银行武汉分行直连', null, 'mock://bank/ccb-wuhan', 'RSA2', 'RSA2', 'API_STATEMENT', 'API_DISBURSEMENT', 'READY', now(), now());
