create table tax_invoice_request (
    id bigint primary key auto_increment,
    invoice_request_no varchar(80) not null unique,
    receipt_id bigint not null,
    bill_id bigint not null,
    payment_order_id bigint not null,
    community_id bigint not null,
    buyer_name varchar(120) not null,
    buyer_tax_no varchar(60) not null,
    invoice_item varchar(120) not null,
    tax_category_code varchar(60) not null,
    tax_rate decimal(8,4) not null,
    amount decimal(18,2) not null,
    status varchar(32) not null,
    tax_invoice_no varchar(80),
    tax_platform_code varchar(80),
    pdf_url varchar(300),
    checksum varchar(128),
    fail_reason varchar(300),
    issued_by varchar(64) not null,
    issued_at datetime,
    red_original_id bigint,
    red_at datetime,
    created_at datetime not null
);

create index idx_tax_invoice_request_receipt on tax_invoice_request(receipt_id);
create index idx_tax_invoice_request_community on tax_invoice_request(community_id, created_at);
create index idx_tax_invoice_request_status on tax_invoice_request(status, created_at);
