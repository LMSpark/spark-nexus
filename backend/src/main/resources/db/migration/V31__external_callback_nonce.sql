create table external_callback_nonce (
    id bigint primary key auto_increment,
    adapter_code varchar(40) not null,
    event_type varchar(64) not null,
    business_no varchar(96) not null,
    nonce_value varchar(128) not null,
    callback_timestamp bigint not null,
    received_at datetime not null,
    unique key uk_external_callback_nonce(adapter_code, event_type, nonce_value)
);

create index idx_external_callback_nonce_business on external_callback_nonce(adapter_code, event_type, business_no);
create index idx_external_callback_nonce_received on external_callback_nonce(received_at);
