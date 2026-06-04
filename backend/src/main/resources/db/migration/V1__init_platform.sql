create table app_user (
  id bigint primary key auto_increment,
  username varchar(64) not null unique,
  password_hash varchar(128) not null,
  display_name varchar(64) not null,
  role varchar(32) not null,
  community_id bigint not null default 1
);

create table community (
  id bigint primary key auto_increment,
  district varchar(64) not null,
  street varchar(64) not null,
  neighborhood varchar(64) not null,
  name varchar(128) not null,
  households int not null,
  occupancy_rate decimal(8,2) not null,
  approval_threshold decimal(14,2) not null
);

create table house (
  id bigint primary key auto_increment,
  community_id bigint not null,
  building varchar(32) not null,
  unit_no varchar(32) not null,
  room_no varchar(32) not null,
  area decimal(10,2) not null,
  owner_name varchar(64) not null,
  owner_phone varchar(32) not null,
  status varchar(32) not null
);

create table owner (
  id bigint primary key auto_increment,
  username varchar(64) not null,
  house_id bigint not null,
  name varchar(64) not null,
  phone varchar(32) not null,
  identity_mask varchar(32) not null
);

create table fund_account (
  id bigint primary key auto_increment,
  community_id bigint not null,
  account_type varchar(32) not null,
  account_name varchar(128) not null,
  bank_name varchar(128) not null,
  account_no_mask varchar(64) not null,
  balance decimal(14,2) not null
);

create table bill (
  id bigint primary key auto_increment,
  house_id bigint not null,
  bill_type varchar(32) not null,
  period varchar(16) not null,
  amount decimal(14,2) not null,
  paid_amount decimal(14,2) not null default 0,
  status varchar(24) not null,
  due_date date not null
);

create table billing_summary (
  id bigint primary key auto_increment,
  community_id bigint not null,
  payment_rate decimal(8,2) not null
);

create table bank_flow (
  id bigint primary key auto_increment,
  community_id bigint not null,
  account_id bigint not null,
  direction varchar(8) not null,
  amount decimal(14,2) not null,
  counterparty varchar(128) not null,
  summary varchar(255) not null,
  occurred_at datetime not null,
  trace_no varchar(64) not null
);

create table expense_order (
  id bigint primary key auto_increment,
  community_id bigint not null,
  order_type varchar(24) not null,
  title varchar(128) not null,
  amount decimal(14,2) not null,
  status varchar(24) not null,
  current_node varchar(64) not null,
  invoice_no varchar(64),
  contract_no varchar(64),
  created_at datetime not null,
  due_at datetime not null
);

create table approval_log (
  id bigint primary key auto_increment,
  expense_id bigint not null,
  node_name varchar(64) not null,
  operator varchar(64) not null,
  decision varchar(32) not null,
  comment varchar(255) not null,
  operated_at datetime not null
);

create table vote (
  id bigint primary key auto_increment,
  community_id bigint not null,
  title varchar(128) not null,
  vote_type varchar(32) not null,
  status varchar(24) not null,
  start_at datetime not null,
  end_at datetime not null,
  participation_rate decimal(8,2) not null,
  agree_rate decimal(8,2) not null
);

create table work_order (
  id bigint primary key auto_increment,
  house_id bigint not null,
  order_type varchar(24) not null,
  title varchar(128) not null,
  description varchar(500) not null,
  status varchar(24) not null,
  created_at datetime not null,
  reply varchar(500)
);

create table announcement (
  id bigint primary key auto_increment,
  community_id bigint not null,
  title varchar(128) not null,
  category varchar(32) not null,
  content varchar(1000) not null,
  published_at datetime not null
);

create table finance_voucher (
  id bigint primary key auto_increment,
  community_id bigint not null,
  voucher_no varchar(64) not null,
  source_type varchar(32) not null,
  debit_subject varchar(128) not null,
  credit_subject varchar(128) not null,
  amount decimal(14,2) not null,
  status varchar(24) not null,
  booked_at datetime
);

create table dashboard_metric (
  id bigint primary key auto_increment,
  label varchar(64) not null,
  metric_value decimal(14,2) not null,
  unit varchar(16) not null,
  trend varchar(32) not null,
  sort_no int not null
);

create table revenue_trend (
  id bigint primary key auto_increment,
  period_month varchar(16) not null,
  income decimal(14,2) not null,
  expense decimal(14,2) not null
);

create table risk_alert (
  id bigint primary key auto_increment,
  community_name varchar(128) not null,
  level varchar(24) not null,
  title varchar(128) not null,
  description varchar(500) not null,
  status varchar(24) not null,
  created_at datetime not null
);

create table audit_log (
  id bigint primary key auto_increment,
  actor varchar(64) not null,
  action varchar(128) not null,
  target_type varchar(64) not null,
  target_id bigint not null,
  hash varchar(128) not null,
  previous_hash varchar(128) not null,
  created_at datetime not null
);

insert into app_user(username, password_hash, display_name, role, community_id) values
('admin', 'admin123', '系统管理员', 'ADMIN', 1),
('gov', 'admin123', '政府监管员', 'GOVERNMENT', 1),
('street', 'admin123', '街道审核员', 'STREET', 1),
('committee', 'admin123', '业委会主任', 'COMMITTEE', 1),
('property', 'admin123', '物业经理', 'PROPERTY', 1),
('owner', 'admin123', '业主张女士', 'OWNER', 1);

insert into community(district, street, neighborhood, name, households, occupancy_rate, approval_threshold) values
('洪山区', '关山街道', '阳光社区', '鼎盛阳光一期', 1860, 96.20, 50000.00),
('洪山区', '关山街道', '湖畔社区', '鼎盛阳光二期', 1320, 91.80, 30000.00),
('东湖高新区', '佛祖岭街道', '星河社区', '星河湾花园', 980, 88.50, 20000.00);

insert into house(community_id, building, unit_no, room_no, area, owner_name, owner_phone, status) values
(1, '1栋', '1单元', '1101', 118.50, '张女士', '138****1024', 'OWNER_OCCUPIED'),
(1, '2栋', '2单元', '0802', 96.30, '李先生', '139****2088', 'OWNER_OCCUPIED'),
(2, '5栋', '1单元', '0601', 89.20, '王女士', '137****3311', 'RENTED'),
(3, '3栋', '3单元', '1203', 126.70, '陈先生', '136****7788', 'VACANT');

insert into owner(username, house_id, name, phone, identity_mask) values
('owner', 1, '张女士', '138****1024', '4201**********2381');

insert into fund_account(community_id, account_type, account_name, bank_name, account_no_mask, balance) values
(1, 'PUBLIC_REVENUE', '鼎盛阳光一期业委会公共收益专户', '汉口银行光谷支行', '6222 **** **** 9011', 1286400.35),
(1, 'PROPERTY_SERVICE', '鼎盛阳光一期物业服务账户', '建设银行关山支行', '6217 **** **** 3309', 526800.20),
(2, 'PUBLIC_REVENUE', '鼎盛阳光二期业委会公共收益专户', '汉口银行光谷支行', '6222 **** **** 6188', 846200.00),
(3, 'PUBLIC_REVENUE', '星河湾花园业委会公共收益专户', '工商银行东湖支行', '6222 **** **** 7102', 392500.80);

insert into bill(house_id, bill_type, period, amount, paid_amount, status, due_date) values
(1, '物业费', '2026-06', 355.50, 0, 'UNPAID', '2026-06-30'),
(1, '停车费', '2026-06', 280.00, 280.00, 'PAID', '2026-06-10'),
(2, '物业费', '2026-06', 288.90, 0, 'OVERDUE', '2026-05-31'),
(3, '公共收益分摊', '2026-Q2', 120.00, 120.00, 'PAID', '2026-06-15');

insert into billing_summary(community_id, payment_rate) values
(1, 83.60), (2, 78.40), (3, 69.20);

insert into bank_flow(community_id, account_id, direction, amount, counterparty, summary, occurred_at, trace_no) values
(1, 1, 'IN', 36000.00, '电梯广告运营商', '二季度电梯广告收益', '2026-05-28 10:20:00', 'BK20260528001'),
(1, 1, 'OUT', 48500.00, '武汉安防工程有限公司', '小区门禁改造付款', '2026-05-29 15:40:00', 'BK20260529002'),
(1, 2, 'IN', 280.00, '张女士', '停车费缴费', '2026-05-30 09:12:00', 'WX20260530001'),
(2, 3, 'IN', 22000.00, '场地租赁商户', '公共场地租赁收益', '2026-05-31 14:02:00', 'BK20260531003');

insert into expense_order(community_id, order_type, title, amount, status, current_node, invoice_no, contract_no, created_at, due_at) values
(1, 'PAYMENT', '小区门禁系统改造尾款', 48500.00, 'PENDING', '社区/街道审批', 'INV-2026-0091', 'HT-DSYG-2026-031', '2026-05-31 08:30:00', '2026-06-01 08:30:00'),
(2, 'REIMBURSEMENT', '消防设施年度检测报销', 12600.00, 'APPROVED', '流程结束', 'INV-2026-0068', null, '2026-05-20 11:00:00', '2026-05-21 11:00:00'),
(3, 'PAYMENT', '公共照明节能改造预付款', 28500.00, 'WARNING', '超时待处理', null, 'HT-XHW-2026-018', '2026-05-30 09:00:00', '2026-05-31 09:00:00');

insert into approval_log(expense_id, node_name, operator, decision, comment, operated_at) values
(2, '业委会初审', 'committee', 'APPROVED', '票据齐全', '2026-05-20 12:00:00'),
(2, '社区/街道审批', 'street', 'APPROVED', '同意支出', '2026-05-20 14:30:00');

insert into vote(community_id, title, vote_type, status, start_at, end_at, participation_rate, agree_rate) values
(1, '关于使用公共收益进行门禁改造的表决', 'VOTE', 'OPEN', '2026-05-25 09:00:00', '2026-06-05 18:00:00', 62.30, 88.40),
(2, '业主满意度问卷调查', 'SURVEY', 'OPEN', '2026-05-28 09:00:00', '2026-06-08 18:00:00', 45.10, 0.00),
(3, '公共照明节能改造方案表决', 'VOTE', 'CLOSED', '2026-05-01 09:00:00', '2026-05-10 18:00:00', 71.90, 82.00);

insert into work_order(house_id, order_type, title, description, status, created_at, reply) values
(1, 'REPAIR', '单元门禁无法刷卡', '1栋1单元门禁读卡失败，影响出入。', 'PROCESSING', '2026-05-31 19:20:00', '物业已派工程人员检修。'),
(2, 'COMPLAINT', '地库照明不足', '地下车库B区照明较暗，存在安全隐患。', 'PENDING', '2026-05-30 20:10:00', null),
(3, 'REPAIR', '楼道感应灯不亮', '5栋6楼楼道灯损坏。', 'DONE', '2026-05-29 08:15:00', '已更换灯具。');

insert into announcement(community_id, title, category, content, published_at) values
(1, '2026年5月公共收益收支公示', '财务公示', '本月公共收益收入36,000元，支出48,500元，明细可在银行流水中查看。', '2026-05-31 18:00:00'),
(1, '端午节物业服务安排', '通知公告', '节日期间客服中心正常值班，紧急报修电话保持畅通。', '2026-05-30 09:00:00'),
(2, '业主满意度问卷邀请', '问卷调查', '请业主在小程序中完成满意度问卷。', '2026-05-28 10:00:00');

insert into finance_voucher(community_id, voucher_no, source_type, debit_subject, credit_subject, amount, status, booked_at) values
(1, 'PZ-202605-001', 'PUBLIC_REVENUE', '银行存款-公共收益专户', '公共收益-广告收入', 36000.00, 'BOOKED', '2026-05-28 18:00:00'),
(1, 'PZ-202605-002', 'EXPENSE', '公共设施维护支出', '银行存款-公共收益专户', 48500.00, 'REVIEWING', null),
(2, 'PZ-202605-003', 'PROPERTY_FEE', '银行存款-物业服务账户', '物业服务收入', 2880.00, 'BOOKED', '2026-05-29 18:00:00');

insert into dashboard_metric(label, metric_value, unit, trend, sort_no) values
('公共收益总余额', 2525101.15, '元', '+8.6%', 1),
('本月收入', 61280.00, '元', '+12.4%', 2),
('本月支出', 89600.00, '元', '-3.1%', 3),
('平均缴费率', 77.10, '%', '+4.8%', 4),
('待审批支出', 2, '笔', '需处理', 5),
('风险预警', 3, '条', '高关注', 6);

insert into revenue_trend(period_month, income, expense) values
('2026-01', 48200, 31200),
('2026-02', 52600, 22800),
('2026-03', 57800, 49600),
('2026-04', 69400, 38500),
('2026-05', 61280, 89600),
('2026-06', 23800, 12600);

insert into risk_alert(community_name, level, title, description, status, created_at) values
('星河湾花园', 'HIGH', '大额支出审批超时', '公共照明节能改造预付款已超过24小时未审批，需核查是否触发银行退回。', 'OPEN', '2026-06-01 09:10:00'),
('鼎盛阳光一期', 'MEDIUM', '支出金额接近阈值', '门禁改造尾款48,500元接近社区审批阈值50,000元。', 'OPEN', '2026-05-31 10:00:00'),
('鼎盛阳光二期', 'LOW', '财务公示临近到期', '5月收支公示将在24小时后超期。', 'TRACKING', '2026-05-31 08:00:00');

insert into audit_log(actor, action, target_type, target_id, hash, previous_hash, created_at) values
('system', '生成5月公共收益凭证', 'finance_voucher', 1, 'b5a1b2c3d4e5', 'GENESIS', '2026-05-28 18:00:00'),
('committee', '提交门禁改造付款单', 'expense_order', 1, 'c6b2c3d4e5f6', 'b5a1b2c3d4e5', '2026-05-31 08:30:00'),
('gov', '查看辖区资金穿透数据', 'supervision', 1, 'd7c3d4e5f6a7', 'c6b2c3d4e5f6', '2026-06-01 09:15:00');
