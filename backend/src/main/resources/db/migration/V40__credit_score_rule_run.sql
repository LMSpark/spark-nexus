create table credit_score_rule (
    id bigint primary key auto_increment,
    rule_version varchar(32) not null,
    factor_code varchar(64) not null,
    factor_name varchar(120) not null,
    weight decimal(8,2) not null,
    threshold_value decimal(12,2) not null,
    deduction_unit decimal(12,2) not null,
    evidence_template varchar(500) not null,
    status varchar(32) not null,
    created_at datetime not null,
    unique(rule_version, factor_code)
);

create table credit_score_run (
    id bigint primary key auto_increment,
    rule_version varchar(32) not null,
    generated_count int not null,
    operator varchar(64) not null,
    status varchar(32) not null,
    summary varchar(500) not null,
    created_at datetime not null
);

create index idx_credit_score_rule_status on credit_score_rule(status, rule_version);
create index idx_credit_score_run_created on credit_score_run(created_at);

insert into credit_score_rule(rule_version, factor_code, factor_name, weight, threshold_value, deduction_unit, evidence_template, status, created_at) values
('2026-A', 'PAYMENT_RATE', '缴费率', 40.00, 90.00, 0.40, '低于90%的缴费率按差额*0.4扣分', 'ACTIVE', now()),
('2026-A', 'OPEN_ALERT', '未关闭预警', 25.00, 0.00, 4.00, '每条未关闭预警扣4分', 'ACTIVE', now()),
('2026-A', 'OVERDUE_BILL', '欠费账单', 20.00, 0.00, 0.80, '每笔欠费账单扣0.8分', 'ACTIVE', now()),
('2026-A', 'AUDIT_CHAIN', '审计链完整性', 15.00, 0.00, 20.00, '每个审计断链点扣20分', 'ACTIVE', now());
