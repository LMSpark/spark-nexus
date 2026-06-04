create table community_credit_score (
  id bigint primary key auto_increment,
  community_id bigint not null unique,
  score decimal(8,2) not null,
  risk_grade varchar(24) not null,
  payment_rate decimal(8,2) not null,
  open_alert_count int not null,
  overdue_bill_count int not null,
  audit_status varchar(24) not null,
  summary varchar(500) not null,
  generated_at datetime not null
);

insert into community_credit_score(community_id, score, risk_grade, payment_rate, open_alert_count, overdue_bill_count, audit_status, summary, generated_at)
select c.id,
       greatest(60, 100 - coalesce((select count(*) * 5 from risk_alert r where r.community_name = c.name and r.status <> 'CLOSED'), 0)
                    - coalesce((select count(*) from bill b join house h on h.id = b.house_id where h.community_id = c.id and b.status in ('UNPAID', 'OVERDUE')), 0)),
       'A',
       coalesce((select payment_rate from billing_summary bs where bs.community_id = c.id limit 1), 0),
       coalesce((select count(*) from risk_alert r where r.community_name = c.name and r.status <> 'CLOSED'), 0),
       coalesce((select count(*) from bill b join house h on h.id = b.house_id where h.community_id = c.id and b.status in ('UNPAID', 'OVERDUE')), 0),
       'VALID',
       '初始化信用评分',
       now()
from community c;
