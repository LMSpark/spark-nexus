create table expense_document (
    id bigint primary key auto_increment,
    expense_id bigint not null,
    document_type varchar(32) not null,
    document_no varchar(80) not null,
    issuer varchar(128) null,
    amount decimal(14,2) null,
    issue_date date null,
    file_url varchar(500) null,
    verification_status varchar(24) not null,
    review_comment varchar(500) null,
    verified_by varchar(64) null,
    verified_at datetime null,
    created_at datetime not null,
    constraint fk_expense_document_expense foreign key (expense_id) references expense_order(id)
);

create index idx_expense_document_expense on expense_document(expense_id, document_type, verification_status);

insert into expense_document(expense_id, document_type, document_no, issuer, amount, issue_date, file_url, verification_status, review_comment, created_at)
select id, 'INVOICE', invoice_no, '示例供应商', amount, date(created_at), concat('mock://invoice/', invoice_no), 'PENDING', '历史支出发票待核验', now()
from expense_order
where invoice_no is not null and invoice_no <> '';

insert into expense_document(expense_id, document_type, document_no, issuer, amount, issue_date, file_url, verification_status, review_comment, created_at)
select id, 'CONTRACT', contract_no, '示例承包方', amount, date(created_at), concat('mock://contract/', contract_no), 'PENDING', '历史支出合同待核验', now()
from expense_order
where contract_no is not null and contract_no <> '';
