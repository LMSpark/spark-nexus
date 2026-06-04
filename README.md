# 领码科技 SPARK Nexus 城市物业治理中枢

按方案搭建的城市物业治理三端一体项目骨架，包含 Spring Boot 后端、Vue 管理端、uni-app 业主端和本地 Docker 依赖。

## 工程结构

- `backend/`：Spring Boot 3 + Java 17 + Flyway + MySQL，提供 `/api` REST 接口。
- `web-admin/`：Vue 3 + Vite + TypeScript + Element Plus 管理端和监管大屏。
- `owner-miniapp/`：uni-app 业主端，可运行 H5，也可构建微信小程序。
- `deploy/`：MySQL、Redis、MinIO 的 Docker Compose 和环境变量模板。

## 本地启动

1. 启动依赖：

```powershell
cd D:\物业管理\deploy
docker compose up -d
```

2. 启动后端（MySQL 模式）：

```powershell
cd D:\物业管理\backend
mvn spring-boot:run
```

如只想脱离 Docker 快速演示，可改用内存库：

```powershell
cd D:\物业管理\backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

3. 启动管理端：

```powershell
cd D:\物业管理\web-admin
npm install
npm run dev
```

访问 `http://localhost:5683`。

4. 启动业主端 H5：

```powershell
cd D:\物业管理\owner-miniapp
npm install
npm run dev:h5
```

访问 `http://localhost:5684`，微信小程序可用 `npm run build:mp-weixin` 后导入微信开发者工具。

## GitHub Codespaces 运行

在 GitHub 仓库页面点击 `Code` -> `Codespaces` -> `Create codespace on main`。

Codespaces 创建完成后，依赖会自动安装，并会启动 MySQL、Redis、MinIO。然后在 Codespaces 终端执行：

```bash
./scripts/codespaces-start.sh
```

等待服务启动后访问端口：

- 管理端：`5683`
- 业主端 H5：`5684`
- 后端 API：`8580`

也可以执行：

```bash
./scripts/codespaces-check.sh
```

检查后端和两个前端代理是否可用。若页面打不开，在 Codespaces 的 `Ports` 面板中确认 `5683`、`5684`、`8580` 已转发并设置为可见。

## 演示账号

密码均为 `admin123`：

- `admin`：系统管理员
- `gov`：政府监管员
- `street`：街道审核员
- `committee`：业委会
- `property`：物业人员
- `owner`：业主端账号

## 已实现的首版能力

- 登录 token、接口鉴权、角色信息、组织/小区数据隔离字段。
- 小区档案、房屋业主、账单收费、微信支付模拟、银行流水同步。
- 公共收益专户、物业服务账户、支出单、审批通过/驳回、业主实名表决、驳回触发银行退回模拟。
- 投票问卷、通知公告、报修投诉、财务凭证、审计日志哈希链展示。
- 政府监管驾驶舱：指标、收支趋势、辖区小区排名、风险预警、待审批支出。
- 大屏专题分析：监管大屏新增资金异常、缴费风险、审批超时、信用评价四类专题切片，可返回风险等级、证据和建议动作。
- 外部接口适配器配置位：微信支付、银行直连、短信、实名认证；默认 `dev` 模式可完整演示。

## 当前迭代新增能力

- 多租户注册制：机构注册、审核通过自动创建租户、管理员和用户租户关系。
- 小区多方协同：政府监管、物业服务、银行代收/监管、业委会治理关系表落地。
- 数据授权隔离：按租户、小区、数据范围、操作动作过滤后台列表和详情。
- 住户多房屋：实名注册、房屋绑定申请、审核通过、默认房屋、房屋切换。
- 大屏专题导出：接口 `/api/supervision/screen/topics` 和 `/api/supervision/screen/topics/export` 支持专题分析查询与 `SCREEN_TOPIC` 导出留档，并纳入政府监管闭环验收。
- 银行对账：小区银行服务配置、银行流水导入、对账记录、差异明细。
- 银行流水台账：管理端银行模块可按授权范围查看代收、退款、放款和导入流水，并导出 `BANK_FLOW` 审计留档。
- 多银行直连适配：银行模块新增 `bank_adapter_profile` / `bank_adapter_health`，可维护汉口银行、建行等不同接口契约，展示签名算法、回调验签、对账模式和放款模式，支持健康检查与 `BANK_ADAPTER_HEALTH` 导出留档。
- 支付退款：微信/支付宝 dev 模式退款、退款单、自动生成退款渠道账单、负向银行流水、短信通知和审计日志。
- 渠道账单：微信/支付宝支付与退款账单同步，形成 `payment_channel_statement` 渠道日切证据。
- 电子缴费票据：住户缴费确认或回调入账后自动开具 `electronic_payment_receipt`，支持按账单补开、PDF 下载、CSV 台账导出和 `ELECTRONIC_RECEIPT` 审计留档。
- 账单批量导出：物业收费页可导出账单明细 CSV，包含小区、房号、费用类型、账期、应收、已缴、欠费、状态和截止日，并写入 `BILLING_BILL` 导出审计。
- 税务开票适配：电子缴费票据可提交税务开票申请，形成 `tax_invoice_request` 数电票台账，当前 dev 模式模拟乐企/税务平台返回税票号、平台流水和 PDF 地址，并支持数电票 PDF 下载、红冲、`TAX_DIGITAL` 调用日志、`TAX_INVOICE` 与 `TAX_INVOICE_PDF` 导出留档。
- 对账差异处理：银行对账明细匹配项自动关闭，差异项支持处理/忽略，全部差异闭环并导出后对账单进入 `RESOLVED`，导出写入 `BANK_RECONCILIATION` 留档。
- 外部接口安全体检：密钥、适配器配置、日志脱敏和生产配置风险自动检查，后台可运行、查看结果并导出整改清单。
- 外部配置同步：后台可将当前环境变量同步到 `integration_config` 台账，形成微信、支付宝、银行、短信、实名的上线配置证据。
- 部署运行就绪检查：审计页新增 MySQL/Flyway、Redis、MinIO、CORS 的运行探测与整改动作，接口为 `/api/integrations/deployment-readiness`，支持导出 `DEPLOYMENT_READINESS` 留档。
- 外部回调验签：支付回调和银行放款回执免平台登录态，使用各渠道回调密钥验签，验签结果写入 `external_callback_receipt` 并支持导出；支付回调和银行回执签名载荷统一按字段名升序生成 `key=value&key2=value2`，签名基串为 `业务编号|canonicalPayload`。
- 回调联调诊断：管理端审计页提供“回调验签联调”，后台接口 `/api/integrations/callbacks/signature-diagnostics` 可在不入账、不占用 nonce 的情况下校验渠道签名并展示 canonical payload。
- 实名认证联调：管理端审计页提供“实名认证核验”，后台接口 `/api/integrations/identity/test` 按姓名、手机号、身份证号与房屋档案掩码核验，并把核验结果写入 `IDENTITY / VERIFY_OWNER` 调用日志。
- 外部回调防重放：生产模式要求回调携带时间戳和 nonce，超时或重复 nonce 会拒绝，已处理业务重复推送会标记 `DUPLICATE` 并保持幂等。
- 敏感字段保护审计：手机号、身份证号等字段的脱敏/核验行为写入 `sensitive_field_audit`，后台可查看并导出 `SENSITIVE_FIELD_AUDIT` 留档。
- 住户服务闭环：业主端消息支持未读/已读回执，问卷提交写入 `survey_response`，报修/投诉处理完成后必须形成服务评价证据。
- 数据导出审计：验收清单、欠费台账、财务报表 CSV/PDF、凭证 PDF、审计日志等导出写入 `data_export_log`，后台可追溯导出人、租户、模块、数据范围、筛选摘要、文件和行数。
- 注册准入留档：注册审核台账和数据授权台账支持 CSV 导出，分别写入 `REGISTRATION_APPLICATION`、`DATA_AUTHORIZATION` 审计模块。
- 业主端联调配置：业主端支持 `VITE_API_BASE` 配置后端地址，H5/小程序真机联调不再依赖本地代理。
- 银行放款验收：达到表决阈值的公共收益支出需完成业主实名投票，审批通过后自动生成银行放款指令，银行处理后写入放款流水并回填银行流水号。
- 公共收益财务验收：公共收益支出纳入发票/合同资料核验、支出凭证、凭证明细分录、记账状态、总分类账/现金流量表 PDF 和凭证 PDF 导出留档，验收演练可自动补齐这些证据。
- 手动财务凭证：财务页支持手工新增凭证，并对多张凭证批量审核、批量记账，记账后自动生成借贷明细进入账簿。
- 支出资料附件：发票、合同、验收单等资料支持附件 URL 登记、核验、PDF 凭据下载与 `EXPENSE_DOCUMENT` 导出审计留档，生产可将 URL 指向 MinIO 或国产对象存储。
- 模板化审批工作流：公共收益支出审批节点沉淀为 `workflow_template` / `workflow_template_node`，管理端可维护节点编码、角色、条件分支、排序和超时小时，审批动作与模板维护写入 `workflow_event`，后台可查看节点轨迹并导出事件台账。
- 验收演练补证：`/api/acceptance/drill/run` 会补齐公共收益投票、银行放款、对账自动闭合、外部回调回执、nonce、防重放导出、实名认证与敏感字段审计证据，便于验收中心复跑后直接核验 DONE。
- 权限边界加固：住户端按已审核房屋关系访问门户，传入未绑定房屋 ID 会返回 403。
- 授权能力菜单：`/api/auth/capabilities` 按当前租户授权返回数据范围、权限、小区数量和菜单键，管理端登录/切换机构后按真实授权渲染菜单。
- 写操作按钮授权：账单收费页按 `BILLING WRITE`、`PAYMENT WRITE` 能力展示配置、生成、缴费、退款入口，监管账号保留查看能力但不暴露越权操作。
- 关系停用闭环：停用小区机构关系会同步撤销该机构在该小区的 ACTIVE 数据授权，避免服务关系失效后仍保留数据权限。
- 支付回调幂等：同一支付订单回调重复到达时只生成一条缴费银行流水，验收中心同步核验已支付账单和缴费入账流水。
- 银行回执幂等：银行放款回执到达时优先更新既有放款流水号，不因回执二次确认重复生成 OUT 流水；验收中心同步核验已放款回执和放款流水。
- 监管留档导出：监管预警台账和小区信用评分支持 CSV 导出，写入 `SUPERVISION_ALERT`、`COMMUNITY_CREDIT_SCORE` 审计模块并纳入验收中心证据。
- 信用评价因子：小区信用评分同步生成缴费率、未关闭预警、欠费账单、审计链完整性四类 `community_credit_factor` 扣分因子，后台可查看并导出 `COMMUNITY_CREDIT_FACTOR` 留档。
- 信用规则版本：新增 `credit_score_rule` 和 `credit_score_run`，信用评分按版本化规则、阈值、权重和扣分单位生成，监管端可查看评分规则、运行批次并导出 `CREDIT_SCORE_RULE` 留档。
- 业主多房屋容错：业主端先加载已审核房屋并校验本地缓存房屋 ID，再请求门户数据，避免旧缓存房屋导致 403 后页面无法恢复。
- 业主多房屋动作隔离：问卷、实名投票、报修提交和工单评价按已审核房屋关系校验所属小区，不再依赖登录 token 的默认小区，第二套房屋也可独立完成服务闭环。
- 跨库兼容加固：审批截止、预警扫描、业主表决截止时间改为 Java 侧计算，避免依赖 MySQL `date_add` 方言。
- 数据交换平台：审计页可生成面向政府/银行/物业的数据交换包，覆盖账单、银行流水、公共收益、风险预警、信用评分和导出日志，形成记录数、校验摘要、构建日志和 `DATA_EXCHANGE` 导出留档。
- API 网关状态：新增 `/api/gateway/status`，集中展示 `/api` 统一前缀、公开路径、路由分组、JWT/RBAC/回调验签/CORS 策略，管理端审计页可视化查看。
- 验收中心：管理端新增“验收中心”，通过 `/api/acceptance/checklist` 按系统数据自动核验功能落地状态，显示缺口摘要、证据标签和下一步动作，并支持导出 CSV。
- 验收演练：管理员可一键运行 `/api/acceptance/drill/run`，自动补齐支付、退款、渠道账单、银行对账、消息回执、安全体检等演练证据。
- 银行对账留档：对账明细支持 CSV 导出，导出记录写入 `BANK_RECONCILIATION` 审计台账，便于政府、物业、银行三方留痕核验。
- 自动化验收：`MultiTenantAcceptanceTest` 覆盖物业注册授权、银行服务对账、支付退款、渠道账单、公共收益银行放款、住户多房屋。

## 国产信创与真实对接预留

- 数据库访问集中在 Spring JDBC/Flyway 层，后续替换达梦、人大金仓等国产库时优先处理方言、分页、时间函数和迁移脚本。
- 审计页提供“国产数据库兼容矩阵”，接口为 `/api/integrations/database-compatibility`，可导出 MySQL、达梦、人大金仓、openGauss 的 JDBC 前缀、驱动类、Flyway 方言和迁移动作清单。
- 外部能力集中在 `IntegrationService` 与 `/api/integrations/status`，真实 SDK 或 HTTP 签名客户端应替换当前 `dev` 模拟分支。
- 生产环境必须设置 `JWT_SECRET`、`SENSITIVE_ENCRYPTION_KEY`、`CORS_ALLOWED_ORIGINS`、微信/支付宝/银行/短信/实名环境变量，以及 `WECHAT_CALLBACK_SECRET`、`ALIPAY_CALLBACK_SECRET`、`BANK_CALLBACK_SECRET`，并把 `INTEGRATION_MODE` 切到真实对接模式；回调需传 `X-Callback-Timestamp`/`X-Callback-Nonce` 或银行侧 `X-Bank-Timestamp`/`X-Bank-Nonce`，完整模板见 `deploy/.env.example`。
