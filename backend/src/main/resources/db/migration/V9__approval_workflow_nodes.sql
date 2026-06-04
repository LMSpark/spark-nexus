create table approval_workflow_node (
  id bigint primary key auto_increment,
  expense_id bigint not null,
  node_code varchar(40) not null,
  node_name varchar(64) not null,
  role_code varchar(32) not null,
  sort_no int not null,
  status varchar(24) not null,
  due_at datetime null,
  operator varchar(64) null,
  decision varchar(32) null,
  comment varchar(500) null,
  operated_at datetime null,
  created_at datetime not null
);

create index idx_approval_workflow_node_expense on approval_workflow_node(expense_id, sort_no);
create index idx_approval_workflow_node_status on approval_workflow_node(status, due_at);

insert into approval_workflow_node(expense_id, node_code, node_name, role_code, sort_no, status, due_at, operator, decision, comment, operated_at, created_at)
select id, 'SUBMIT', '提交申请', 'COMMITTEE', 1, 'APPROVED', created_at, null, 'SUBMITTED', '历史支出补录提交节点', created_at, created_at
from expense_order;

insert into approval_workflow_node(expense_id, node_code, node_name, role_code, sort_no, status, due_at, created_at)
select id, 'COMMITTEE_REVIEW', '业委会审批', 'COMMITTEE', 2,
       case when status in ('PENDING', 'WARNING') and current_node = '业委会审批' then 'ACTIVE'
            when status = 'APPROVED' then 'APPROVED'
            when status in ('REJECTED', 'RETURNED') then status
            else 'SKIPPED' end,
       due_at, created_at
from expense_order;

insert into approval_workflow_node(expense_id, node_code, node_name, role_code, sort_no, status, due_at, created_at)
select id, 'STREET_REVIEW', '社区/街道审批', 'STREET', 3,
       case when status in ('PENDING', 'WARNING') and current_node = '社区/街道审批' then 'ACTIVE'
            when status = 'APPROVED' then 'APPROVED'
            when status in ('REJECTED', 'RETURNED') then status
            else 'WAITING' end,
       due_at, created_at
from expense_order
where current_node in ('社区/街道审批', '业主大会表决', '流程结束', '银行退回') or amount >= 30000;

insert into approval_workflow_node(expense_id, node_code, node_name, role_code, sort_no, status, due_at, created_at)
select id, 'OWNER_VOTE', '业主大会表决', 'OWNER', 4,
       case when status in ('PENDING', 'WARNING') and current_node = '业主大会表决' then 'ACTIVE'
            when status = 'APPROVED' then 'APPROVED'
            when status in ('REJECTED', 'RETURNED') then status
            else 'WAITING' end,
       due_at, created_at
from expense_order
where need_vote = 'YES';

insert into approval_workflow_node(expense_id, node_code, node_name, role_code, sort_no, status, due_at, created_at)
select id, 'BANK_PAYMENT', '银行放款', 'BANK', 5,
       case when status = 'APPROVED' then 'APPROVED'
            when status in ('REJECTED', 'RETURNED') then status
            else 'WAITING' end,
       due_at, created_at
from expense_order;
