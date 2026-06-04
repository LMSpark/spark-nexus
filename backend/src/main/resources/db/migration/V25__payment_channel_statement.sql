create table payment_channel_statement (
    id bigint primary key auto_increment,
    community_id bigint not null,
    provider varchar(32) not null,
    statement_date date not null,
    trade_type varchar(32) not null,
    bill_id bigint not null,
    order_no varchar(96) not null,
    channel_trade_no varchar(128) not null,
    amount decimal(14,2) not null,
    status varchar(32) not null,
    synced_at datetime not null,
    created_at datetime not null,
    unique(provider, statement_date, trade_type, channel_trade_no)
);

create index idx_payment_statement_community on payment_channel_statement(community_id, provider, statement_date);
