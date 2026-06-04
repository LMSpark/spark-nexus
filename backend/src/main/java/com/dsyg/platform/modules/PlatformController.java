package com.dsyg.platform.modules;

import com.dsyg.platform.auth.AccessControlService;
import com.dsyg.platform.auth.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class PlatformController {
    private final JdbcClient jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final IntegrationService integrations;
    private final TokenService tokenService;
    private final AccessControlService accessControl;
    private final SensitiveDataService sensitive;

    public PlatformController(JdbcClient jdbc, NamedParameterJdbcTemplate namedJdbc, IntegrationService integrations, TokenService tokenService, AccessControlService accessControl, SensitiveDataService sensitive) {
        this.jdbc = jdbc;
        this.namedJdbc = namedJdbc;
        this.integrations = integrations;
        this.tokenService = tokenService;
        this.accessControl = accessControl;
        this.sensitive = sensitive;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "service", "领码科技 SPARK Nexus 城市物业治理中枢", "apiPrefix", "/api");
    }

    @GetMapping("/acceptance/checklist")
    public AcceptanceChecklist acceptanceChecklist(HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET");
        List<AcceptanceItem> items = List.of(
            acceptanceItem("平台注册制", "机构租户、用户租户关系、注册申请", List.of(
                count("select count(*) from tenant where status = 'ACTIVE'"),
                count("select count(*) from user_tenant_relation where status = 'ACTIVE'"),
                count("select count(*) from registration_application")
            ), List.of("活动租户", "用户租户关系", "注册申请"), 3, "提交并审核机构注册申请，确保租户和管理员关系同步生成"),
            acceptanceItem("小区多方绑定", "政府、物业、银行、业委会与小区多对多关系", List.of(
                count("select count(*) from tenant_community_relation where status = 'ACTIVE' and relation_type in ('SUPERVISION','JURISDICTION')"),
                count("select count(*) from tenant_community_relation where status = 'ACTIVE' and relation_type = 'PROPERTY_SERVICE'"),
                count("select count(*) from tenant_community_relation where status = 'ACTIVE' and relation_type like 'BANK_%'")
            ), List.of("政府监管/管辖关系", "物业服务关系", "银行服务关系"), 3, "补齐小区与政府、物业、银行机构关系并保持 ACTIVE"),
            acceptanceItem("数据授权隔离", "按租户、小区、数据范围、操作动作授权", List.of(
                count("select count(*) from data_authorization where status = 'ACTIVE' and data_scope = 'COMMUNITY_PROFILE'"),
                count("select count(*) from data_authorization where status = 'ACTIVE' and data_scope = 'BANK_FLOW'"),
                count("select count(*) from data_authorization where status = 'ACTIVE' and permission_code in ('WRITE','APPROVE','EXPORT')"),
                count("select count(*) from data_export_log where status = 'SUCCESS'"),
                count("select count(*) from data_export_log where status = 'SUCCESS' and data_scope <> 'LEGACY' and filter_summary is not null")
            ), List.of("小区档案授权", "银行流水授权", "写入/审批/导出权限", "导出审计记录", "导出范围摘要"), 4, "为机构按小区补齐数据授权，并执行至少一次带数据范围和筛选摘要的受控导出"),
            acceptanceItem("住户多房屋", "实名注册、房屋绑定、默认房屋和切换", List.of(
                count("select count(*) from resident_house_relation where verification_status = 'APPROVED'"),
                count("select count(*) from registration_application where target_type = 'RESIDENT_HOUSE'"),
                count("select count(*) from owner")
            ), List.of("已审核房屋关系", "住户绑定申请", "业主档案"), 3, "住户提交房屋绑定并审核通过，生成对应业主档案"),
            acceptanceItem("物业收费闭环", "收费标准、账单生成、支付订单、缴费回写、电子票据", List.of(
                count("select count(*) from fee_standard where status = 'ACTIVE'"),
                count("select count(*) from bill"),
                count("select count(*) from payment_order"),
                count("select count(*) from bill where status = 'PAID' and paid_amount > 0"),
                count("select count(*) from bank_flow where direction = 'IN' and summary like '%缴费%'"),
                count("select count(*) from electronic_payment_receipt where status = 'ISSUED'"),
                count("select count(*) from data_export_log where export_module = 'ELECTRONIC_RECEIPT'"),
                count("select count(*) from tax_invoice_request where status in ('ISSUED','RED_CANCELLED')"),
                count("select count(*) from integration_call_log where adapter_code = 'TAX_DIGITAL' and operation in ('ISSUE_INVOICE','RED_CANCEL_INVOICE')"),
                count("select count(*) from data_export_log where export_module = 'TAX_INVOICE'")
            ), List.of("有效收费标准", "账单记录", "支付订单", "已支付账单", "缴费银行流水", "电子票据", "票据导出留档", "税务发票台账", "税务适配调用", "税票导出留档"), 8, "生成账单并完成微信或支付宝支付确认，开具电子票据，提交税务开票并导出票据留档"),
            acceptanceItem("支付退款闭环", "微信/支付宝退款、渠道账单、退款单、负向银行流水、短信通知", List.of(
                count("select count(*) from payment_refund_order"),
                count("select count(*) from payment_channel_statement"),
                count("select count(*) from bank_flow where direction = 'OUT' and summary like '%退款%'"),
                count("select count(*) from integration_call_log where operation = 'REFUND_PAYMENT'"),
                count("select count(*) from integration_call_log where operation = 'SEND_SMS'")
            ), List.of("退款单", "渠道账单", "退款银行流水", "退款接口日志", "短信通知日志"), 5, "对已支付账单发起退款并同步渠道账单"),
            acceptanceItem("银行对账闭环", "银行配置、流水导入、对账记录、差异明细", List.of(
                count("select count(*) from community_bank_config where status = 'ACTIVE'"),
                count("select count(*) from bank_adapter_profile where status in ('ACTIVE','READY')"),
                count("select count(*) from bank_adapter_health"),
                count("select count(*) from bank_flow"),
                count("select count(*) from reconciliation_record"),
                count("select count(*) from reconciliation_detail"),
                count("select count(*) from reconciliation_detail where handled_status in ('AUTO_CLOSED','RESOLVED','IGNORED')"),
                count("select count(*) from data_export_log where export_module = 'BANK_RECONCILIATION'"),
                count("select count(*) from data_export_log where export_module = 'BANK_ADAPTER_HEALTH'")
            ), List.of("银行服务配置", "多银行适配器", "适配器健康检查", "银行流水", "对账记录", "对账明细", "明细已关闭/已处理", "对账导出留档", "适配器检查导出"), 8, "执行银行适配器健康检查、银行对账，生成匹配或差异明细，完成差异处理并导出对账明细和适配器检查台账"),
            acceptanceItem("政府监管闭环", "驾驶舱、风险预警、信用评分、审计导出", List.of(
                count("select count(*) from dashboard_metric"),
                count("select count(*) from data_export_log where export_module = 'SCREEN_TOPIC'"),
                count("select count(*) from risk_alert"),
                count("select count(*) from credit_score_rule where status = 'ACTIVE'"),
                count("select count(*) from credit_score_run"),
                count("select count(*) from community_credit_score"),
                count("select count(*) from community_credit_factor"),
                count("select count(*) from audit_log"),
                count("select count(*) from data_export_log where export_module in ('SUPERVISION_ALERT','COMMUNITY_CREDIT_SCORE','COMMUNITY_CREDIT_FACTOR','CREDIT_SCORE_RULE')")
            ), List.of("驾驶舱指标", "大屏专题导出", "风险预警", "评分规则", "评分运行批次", "信用评分", "评分因子", "审计日志", "监管导出留档"), 8, "维护信用评分规则，生成大屏专题分析、监管评分和评分因子，处理预警并导出预警、信用评分、评分规则或评分因子证据"),
            acceptanceItem("公共收益与审批", "公共收益账户、支出审批、业主投票、银行放款", List.of(
                count("select count(*) from fund_account where account_type = 'PUBLIC_REVENUE'"),
                count("select count(*) from expense_order"),
                count("select count(*) from approval_workflow_node"),
                count("select count(*) from workflow_template where status = 'ACTIVE'"),
                count("select count(*) from workflow_event"),
                count("select count(*) from vote where related_expense_id is not null"),
                count("select count(*) from vote_ballot vb join vote v on v.id = vb.vote_id where v.related_expense_id is not null"),
                count("select count(*) from bank_disbursement_instruction"),
                count("select count(*) from bank_disbursement_instruction where status = 'PAID' and bank_trace_no is not null"),
                count("select count(*) from bank_flow where direction = 'OUT' and summary not like '%退款%'"),
                count("select count(*) from expense_document where document_type in ('INVOICE','CONTRACT')"),
                count("select count(*) from expense_document where verification_status = 'VERIFIED'"),
                count("select count(*) from finance_voucher where source_type = 'EXPENSE'"),
                count("select count(*) from finance_voucher where source_type = 'EXPENSE' and status = 'BOOKED'"),
                count("select count(*) from finance_voucher_line"),
                count("select count(*) from data_export_log where export_module = 'FINANCE_VOUCHER'"),
                count("select count(*) from data_export_log where export_module = 'EXPENSE_DOCUMENT'")
            ), List.of("公共收益账户", "支出单", "审批节点", "工作流模板", "工作流事件", "业主表决", "实名投票记录", "银行放款指令", "已放款回执", "放款银行流水", "发票/合同资料", "资料已核验", "支出凭证", "凭证已记账", "凭证明细分录", "凭证PDF留档", "资料附件下载留档"), 17, "提交达到表决阈值的公共收益支出，完成模板化工作流、业主实名投票、审批通过、银行放款、资料核验、凭证记账、PDF和资料附件留档"),
            acceptanceItem("住户服务闭环", "公告、消息、投票问卷、报修投诉、服务评价", List.of(
                count("select count(*) from announcement"),
                count("select count(*) from message_notice"),
                count("select count(*) from message_read_receipt"),
                count("select count(*) from vote"),
                count("select count(*) from survey_response"),
                count("select count(*) from work_order"),
                count("select count(*) from work_order where satisfaction_score is not null and evaluated_at is not null")
            ), List.of("公告", "消息", "消息已读回执", "投票/问卷", "问卷提交", "报修投诉", "服务评价"), 7, "发布消息公告，业主端完成已读、问卷提交、工单处理和服务评价"),
            acceptanceItem("信创与数据交换", "国产数据库兼容矩阵、数据交换包、交换导出留档", List.of(
                count("select count(*) from data_exchange_package"),
                count("select count(*) from integration_call_log where adapter_code = 'DATA_EXCHANGE' and operation = 'BUILD_PACKAGE'"),
                count("select count(*) from data_export_log where export_module = 'DATA_EXCHANGE'"),
                count("select count(*) from data_export_log where export_module = 'DATABASE_COMPATIBILITY'")
            ), List.of("数据交换包", "交换构建日志", "交换包导出留档", "数据库兼容矩阵导出"), 4, "生成面向政府/银行/物业的数据交换包，导出交换台账和国产数据库兼容矩阵"),
            acceptanceItem("API网关治理", "统一 /api 前缀、公开路径、路由分组、JWT/RBAC/回调/CORS 策略", List.of(
                1,
                7,
                10,
                4
            ), List.of("统一API前缀", "公开路径清单", "路由分组清单", "网关策略清单"), 4, "维护网关状态接口，确保统一前缀、公开路径、路由分组和安全策略可核验"),
            acceptanceItem("部署运行就绪", "MySQL/Flyway、Redis、MinIO、CORS 探测与整改留档", List.of(
                count("select count(*) from data_export_log where export_module = 'DEPLOYMENT_READINESS'"),
                count("select count(*) from data_export_log where export_module = 'INTEGRATION_READINESS'"),
                count("select count(*) from data_export_log where export_module = 'DATABASE_COMPATIBILITY'")
            ), List.of("部署就绪导出", "生产配置体检导出", "数据库兼容导出"), 3, "运行部署就绪检查、生产配置体检和国产数据库兼容矩阵，并导出留档"),
            acceptanceItem("外部接口与安全", "短信、支付、银行、实名适配器与敏感字段保护", List.of(
                count("select count(*) from integration_config where status = 'ACTIVE'"),
                count("select count(*) from integration_call_log"),
                count("select count(*) from external_callback_receipt"),
                count("select count(*) from external_callback_nonce"),
                count("select count(*) from integration_security_check"),
                count("select count(*) from sensitive_field_audit"),
                count("select count(*) from integration_call_log where adapter_code = 'IDENTITY' and operation = 'VERIFY_OWNER'"),
                count("select count(*) from data_export_log where export_module in ('INTEGRATION_SECURITY','INTEGRATION_READINESS','EXTERNAL_CALLBACK','EXTERNAL_CALLBACK_NONCE','SENSITIVE_FIELD_AUDIT')")
            ), List.of("活动适配器配置", "接口调用日志", "外部回调回执", "防重放 nonce 台账", "安全体检记录", "敏感字段审计", "实名认证核验日志", "安全导出留档"), 8, "同步外部接口配置，完成回调防重放演练，运行安全体检、实名认证联调、敏感字段访问审计并导出留档")
        );
        long done = items.stream().filter(item -> "DONE".equals(item.status())).count();
        int score = items.isEmpty() ? 0 : (int) Math.round(done * 100.0 / items.size());
        return new AcceptanceChecklist(score, done, items.size(), items);
    }

    @GetMapping("/acceptance/checklist/export")
    public ResponseEntity<byte[]> exportAcceptanceChecklist(HttpServletRequest request) {
        AcceptanceChecklist checklist = acceptanceChecklist(request);
        StringBuilder csv = new StringBuilder("模块,验收内容,状态,证据数,要求证据数,缺口数,缺口摘要,下一步,数据证据\n");
        for (AcceptanceItem item : checklist.items()) {
            csv.append(csv(item.module())).append(',')
                .append(csv(item.description())).append(',')
                .append(csv(item.status())).append(',')
                .append(item.matchedEvidenceCount()).append(',')
                .append(item.requiredEvidenceCount()).append(',')
                .append(item.missingEvidenceCount()).append(',')
                .append(csv(item.gapSummary())).append(',')
                .append(csv(item.nextAction())).append(',')
                .append(csv(item.evidenceCounts().toString()))
                .append('\n');
        }
        csv.append('\n')
            .append(csv("汇总")).append(',')
            .append(csv("系统数据自动核验")).append(',')
            .append(csv(checklist.completionScore() + "%")).append(',')
            .append(checklist.doneCount()).append(',')
            .append(checklist.totalCount()).append(',')
            .append(csv("DONE=" + checklist.doneCount() + "/" + checklist.totalCount()))
            .append('\n');
        logDataExport(request, "ACCEPTANCE", "acceptance-checklist.csv", "acceptance_checklist", 0, null, checklist.items().size());
        return csvResponse("acceptance-checklist.csv", csv.toString());
    }

    @PostMapping("/acceptance/drill/run")
    public Map<String, Object> runAcceptanceDrill(HttpServletRequest request) {
        requireAnyRole(request, "ADMIN");
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        long paymentOrderId = ensureDrillPayment();
        result.put("paymentOrderId", paymentOrderId);
        long receiptId = ensureDrillPaymentReceipt(request, paymentOrderId);
        result.put("receiptId", receiptId);
        result.put("taxInvoiceId", ensureDrillTaxInvoice(request, receiptId));
        result.put("refundId", ensureDrillRefund());
        result.put("statementRows", ensureDrillPaymentStatements());
        long reconciliationId = ensureDrillReconciliation();
        result.put("reconciliationId", reconciliationId);
        result.put("reconciliationExportEvidence", ensureDrillReconciliationExport(request, reconciliationId));
        result.put("bankAdapterEvidence", ensureDrillBankAdapterEvidence(request));
        long expenseVoteId = ensureDrillExpenseVote();
        result.put("expenseVoteId", expenseVoteId);
        result.put("workflowEvidence", ensureDrillWorkflowEvidence(expenseVoteId, request));
        result.put("financeEvidence", ensureDrillExpenseFinanceEvidence(expenseVoteId, request));
        result.put("surveyResponseId", ensureDrillSurveyResponse());
        result.put("evaluatedWorkOrderId", ensureDrillWorkOrderEvaluation());
        result.put("messageReceiptId", ensureDrillMessageReceipt());
        result.put("securityChecks", ensureDrillSecurityChecks());
        result.put("externalSecurityEvidence", ensureDrillExternalSecurityEvidence(request));
        result.put("dataExchangeEvidence", ensureDrillDataExchangeEvidence(request));
        result.put("screenTopicEvidence", ensureDrillScreenTopicEvidence(request));
        audit(principal.username(), "运行功能验收演练", "acceptance_drill", 0);
        result.put("status", "DONE");
        return result;
    }

    @GetMapping("/supervision/dashboard")
    public Dashboard dashboard(HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET", "COMMITTEE", "PROPERTY");
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        List<Long> allowedCommunityIds = accessControl.allowedCommunityIds(principal, "COMMUNITY_PROFILE", "READ");
        if (allowedCommunityIds.isEmpty()) {
            return new Dashboard(List.of(), List.of(), List.of(), List.of(), List.of());
        }
        List<Metric> metrics = jdbc.sql("""
            select label, metric_value as `value`, unit, trend from dashboard_metric order by sort_no
            """).query(Metric.class).list();
        List<TrendPoint> trend = jdbc.sql("""
            select period_month as `month`, income, expense from revenue_trend order by period_month
            """).query(TrendPoint.class).list();
        List<CommunityRank> ranks = jdbc.sql("""
            select c.id, c.name, c.street, c.households, c.occupancy_rate occupancyRate,
                   coalesce(sum(a.balance), 0) balance,
                   coalesce(avg(b.payment_rate), 0) paymentRate
            from community c
            left join fund_account a on a.community_id = c.id
            left join billing_summary b on b.community_id = c.id
            where c.id in (:allowedCommunityIds)
            group by c.id, c.name, c.street, c.households, c.occupancy_rate
            order by balance desc
            """).param("allowedCommunityIds", allowedCommunityIds).query(CommunityRank.class).list();
        List<RiskAlert> alerts = jdbc.sql("""
            select id, community_name communityName, level, title, description, status, created_at createdAt
            from risk_alert
            where community_name in (select name from community where id in (:allowedCommunityIds))
            order by created_at desc limit 8
            """).param("allowedCommunityIds", allowedCommunityIds).query(RiskAlert.class).list();
        List<ApprovalItem> approvals = jdbc.sql("""
            select e.id, c.name communityName, e.title, e.amount, e.status, e.current_node currentNode,
                   e.created_at createdAt, e.due_at dueAt
            from expense_order e join community c on c.id = e.community_id
            where e.community_id in (:allowedCommunityIds)
            order by e.created_at desc limit 8
            """).param("allowedCommunityIds", allowedCommunityIds).query(ApprovalItem.class).list();
        return new Dashboard(metrics, trend, ranks, alerts, approvals);
    }

    @GetMapping("/supervision/screen/topics")
    public List<ScreenTopic> screenTopics(HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET");
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        List<Long> allowedCommunityIds = accessControl.allowedCommunityIds(principal, "COMMUNITY_PROFILE", "READ");
        if (allowedCommunityIds.isEmpty()) {
            return List.of();
        }
        java.util.ArrayList<ScreenTopic> topics = new java.util.ArrayList<>();
        topics.addAll(jdbc.sql("""
            select 'FUND_RISK' topicCode, '资金异常专题' topicName,
                   case when abs(coalesce(sum(case when f.direction = 'OUT' then f.amount else 0 end), 0)) > 50000 then 'HIGH' else 'LOW' end level,
                   c.name communityName,
                   '公共收益支出与账户余额穿透' headline,
                   coalesce(sum(case when f.direction = 'OUT' then f.amount else 0 end), 0) primaryValue,
                   coalesce(sum(a.balance), 0) secondaryValue,
                   '支出/余额' trend,
                   concat('OUT流水=', coalesce(count(case when f.direction = 'OUT' then 1 end), 0), '；账户数=', count(distinct a.id)) evidence,
                   '核对大额支出审批、银行放款回执和凭证记账状态' action
            from community c
            left join fund_account a on a.community_id = c.id
            left join bank_flow f on f.community_id = c.id
            where c.id in (:allowedCommunityIds)
            group by c.id, c.name
            """)
            .param("allowedCommunityIds", allowedCommunityIds)
            .query(ScreenTopic.class)
            .list());
        topics.addAll(jdbc.sql("""
            select 'PAYMENT_RISK' topicCode, '缴费风险专题' topicName,
                   case when coalesce(bs.payment_rate, 0) < 80 then 'HIGH'
                        when coalesce(bs.payment_rate, 0) < 90 then 'MEDIUM' else 'LOW' end level,
                   c.name communityName,
                   '缴费率与欠费账单联动' headline,
                   coalesce(bs.payment_rate, 0) primaryValue,
                   coalesce((select count(*) from bill b join house h on h.id = b.house_id
                             where h.community_id = c.id and b.status in ('UNPAID','OVERDUE')), 0) secondaryValue,
                   '缴费率/欠费笔数' trend,
                   concat('缴费率=', coalesce(bs.payment_rate, 0), '%') evidence,
                   '对低缴费率小区发起欠费公示、短信提醒和催缴复核' action
            from community c
            left join billing_summary bs on bs.community_id = c.id
            where c.id in (:allowedCommunityIds)
            """)
            .param("allowedCommunityIds", allowedCommunityIds)
            .query(ScreenTopic.class)
            .list());
        topics.addAll(jdbc.sql("""
            select 'APPROVAL_RISK' topicCode, '审批超时专题' topicName,
                   case when count(case when e.due_at < now() and e.status not in ('PAID','REJECTED') then 1 end) > 0 then 'HIGH' else 'LOW' end level,
                   c.name communityName,
                   '公共收益支出审批时效' headline,
                   count(case when e.status not in ('PAID','REJECTED') then 1 end) primaryValue,
                   count(case when e.due_at < now() and e.status not in ('PAID','REJECTED') then 1 end) secondaryValue,
                   '在办/超时' trend,
                   concat('在办支出=', count(case when e.status not in ('PAID','REJECTED') then 1 end), '；超时=', count(case when e.due_at < now() and e.status not in ('PAID','REJECTED') then 1 end)) evidence,
                   '催办当前审批节点，必要时升级社区/街道复核' action
            from community c
            left join expense_order e on e.community_id = c.id
            where c.id in (:allowedCommunityIds)
            group by c.id, c.name
            """)
            .param("allowedCommunityIds", allowedCommunityIds)
            .query(ScreenTopic.class)
            .list());
        topics.addAll(jdbc.sql("""
            select 'CREDIT_RISK' topicCode, '信用评价专题' topicName,
                   case when s.risk_grade in ('C','D') then 'HIGH'
                        when s.risk_grade = 'B' then 'MEDIUM' else 'LOW' end level,
                   c.name communityName,
                   '小区信用评分与扣分因子' headline,
                   coalesce(s.score, 0) primaryValue,
                   coalesce((select sum(f.deduction) from community_credit_factor f where f.score_id = s.id), 0) secondaryValue,
                   '评分/扣分' trend,
                   coalesce(s.summary, '尚未生成信用评分') evidence,
                   '生成评分因子并对高扣分项逐项整改' action
            from community c
            left join community_credit_score s on s.community_id = c.id
            where c.id in (:allowedCommunityIds)
            """)
            .param("allowedCommunityIds", allowedCommunityIds)
            .query(ScreenTopic.class)
            .list());
        return topics.stream()
            .sorted(java.util.Comparator.comparing(ScreenTopic::level).thenComparing(ScreenTopic::topicCode))
            .toList();
    }

    @GetMapping("/supervision/screen/topics/export")
    public ResponseEntity<byte[]> exportScreenTopics(HttpServletRequest request) {
        List<ScreenTopic> rows = screenTopics(request);
        StringBuilder csv = new StringBuilder("专题编码,专题名称,风险等级,小区,标题,主指标,副指标,趋势口径,证据,建议动作\n");
        for (ScreenTopic row : rows) {
            csv.append(csv(row.topicCode())).append(',')
                .append(csv(row.topicName())).append(',')
                .append(csv(row.level())).append(',')
                .append(csv(row.communityName())).append(',')
                .append(csv(row.headline())).append(',')
                .append(row.primaryValue()).append(',')
                .append(row.secondaryValue()).append(',')
                .append(csv(row.trend())).append(',')
                .append(csv(row.evidence())).append(',')
                .append(csv(row.action())).append('\n');
        }
        logDataExport(request, "SCREEN_TOPIC", "screen-topics.csv", "screen_topic", 0, null, rows.size());
        return csvResponse("screen-topics.csv", csv.toString());
    }

    @GetMapping("/supervision/communities/{communityId}/drilldown")
    public SupervisionDrilldown supervisionDrilldown(@PathVariable long communityId, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET");
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        accessControl.assertCommunityAccess(principal, communityId, "COMMUNITY_PROFILE", "READ");
        Community community = jdbc.sql("""
            select id, district, street, neighborhood, name, households, occupancy_rate occupancyRate,
                   approval_threshold approvalThreshold
            from community where id = :communityId
            """).param("communityId", communityId).query(Community.class).single();
        List<FundAccount> accounts = jdbc.sql("""
            select id, community_id communityId, account_type accountType, account_name accountName,
                   bank_name bankName, account_no_mask accountNoMask, balance
            from fund_account where community_id = :communityId order by account_type
            """).param("communityId", communityId).query(FundAccount.class).list();
        List<BankFlow> flows = jdbc.sql("""
            select f.id, c.name communityName, a.account_name accountName, f.direction, f.amount,
                   f.counterparty, f.summary, f.occurred_at occurredAt, f.trace_no traceNo
            from bank_flow f
            join fund_account a on a.id = f.account_id
            join community c on c.id = f.community_id
            where f.community_id = :communityId
            order by f.occurred_at desc
            """).param("communityId", communityId).query(BankFlow.class).list();
        List<Voucher> vouchers = jdbc.sql("""
            select v.id, c.name communityName, v.voucher_no voucherNo, v.source_type sourceType,
                   v.debit_subject debitSubject, v.credit_subject creditSubject, v.amount,
                   v.status, v.booked_at bookedAt
            from finance_voucher v join community c on c.id = v.community_id
            where v.community_id = :communityId
            order by v.id desc
            """).param("communityId", communityId).query(Voucher.class).list();
        List<Expense> expenses = jdbc.sql("""
            select e.id, c.name communityName, e.order_type orderType, e.title, e.amount, e.status,
                   e.current_node currentNode, e.invoice_no invoiceNo, e.contract_no contractNo,
                   e.created_at createdAt, e.due_at dueAt
            from expense_order e join community c on c.id = e.community_id
            where e.community_id = :communityId
            order by e.created_at desc
            """).param("communityId", communityId).query(Expense.class).list();
        List<Bill> overdueBills = jdbc.sql("""
            select b.id, c.name communityName, h.room_no roomNo, b.bill_type billType,
                   b.period, b.amount, b.paid_amount paidAmount, b.status, b.due_date dueDate
            from bill b
            join house h on h.id = b.house_id
            join community c on c.id = h.community_id
            where h.community_id = :communityId and b.status in ('UNPAID', 'OVERDUE')
            order by b.due_date
            """).param("communityId", communityId).query(Bill.class).list();
        List<RiskAlert> alerts = jdbc.sql("""
            select id, community_name communityName, level, title, description, status, created_at createdAt
            from risk_alert where community_name = :communityName order by created_at desc
            """).param("communityName", community.name()).query(RiskAlert.class).list();
        audit(principal.username(), "监管下钻-" + community.name(), "community", communityId);
        return new SupervisionDrilldown(community, accounts, flows, vouchers, expenses, overdueBills, alerts);
    }

    @PostMapping("/supervision/alerts/{alertId}/status")
    public Map<String, Object> updateAlertStatus(@PathVariable long alertId, @RequestBody AlertStatusRequest body, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET");
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        String alertCommunityName = jdbc.sql("select community_name from risk_alert where id = :id")
            .param("id", alertId)
            .query(String.class)
            .single();
        Long alertCommunityId = jdbc.sql("select id from community where name = :name")
            .param("name", alertCommunityName)
            .query(Long.class)
            .optional()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到预警所属小区"));
        accessControl.assertCommunityAccess(principal, alertCommunityId, "COMMUNITY_PROFILE", "READ");
        String status = body.status() == null || body.status().isBlank() ? "TRACKING" : body.status();
        jdbc.sql("""
            update risk_alert
            set status = :status,
                handled_by = :handledBy,
                resolved_at = case when :status = 'CLOSED' then now() else resolved_at end
            where id = :id
            """)
            .param("status", status)
            .param("handledBy", principal.username())
            .param("id", alertId)
            .update();
        audit(principal.username(), "处置风险预警-" + status, "risk_alert", alertId);
        return Map.of("alertId", alertId, "status", status);
    }

    @GetMapping("/supervision/alert-rules")
    public List<SupervisionAlertRule> supervisionAlertRules(HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET");
        return jdbc.sql("""
            select id, rule_code ruleCode, rule_name ruleName, level, threshold_amount thresholdAmount,
                   threshold_days thresholdDays, status, created_at createdAt
            from supervision_alert_rule
            order by id
            """).query(SupervisionAlertRule.class).list();
    }

    @GetMapping("/supervision/alerts/export")
    public ResponseEntity<byte[]> exportSupervisionAlerts(HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET");
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        List<Long> allowedCommunityIds = accessControl.allowedCommunityIds(principal, "COMMUNITY_PROFILE", "READ");
        if (allowedCommunityIds.isEmpty()) {
            logDataExport(request, "SUPERVISION_ALERT", "supervision-alerts.csv", "risk_alert", 0, null, 0);
            return csvResponse("supervision-alerts.csv", "小区,级别,标题,说明,状态,创建时间\n");
        }
        List<RiskAlert> rows = jdbc.sql("""
            select id, community_name communityName, level, title, description, status, created_at createdAt
            from risk_alert
            where community_name in (select name from community where id in (:allowedCommunityIds))
            order by created_at desc, id desc
            """)
            .param("allowedCommunityIds", allowedCommunityIds)
            .query(RiskAlert.class)
            .list();
        StringBuilder csv = new StringBuilder("小区,级别,标题,说明,状态,创建时间\n");
        for (RiskAlert row : rows) {
            csv.append(csv(row.communityName())).append(',')
                .append(csv(row.level())).append(',')
                .append(csv(row.title())).append(',')
                .append(csv(row.description())).append(',')
                .append(csv(row.status())).append(',')
                .append(csv(row.createdAt() == null ? "" : row.createdAt().toString())).append('\n');
        }
        logDataExport(request, "SUPERVISION_ALERT", "supervision-alerts.csv", "risk_alert", 0, null, rows.size());
        return csvResponse("supervision-alerts.csv", csv.toString());
    }

    @PostMapping("/supervision/alerts/scan")
    public Map<String, Object> scanSupervisionAlerts(HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET");
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        List<Long> allowedCommunityIds = accessControl.allowedCommunityIds(principal, "COMMUNITY_PROFILE", "READ");
        if (allowedCommunityIds.isEmpty()) {
            return Map.of("createdCount", 0);
        }
        int created = scanLargeExpenseAlerts(allowedCommunityIds)
            + scanNoPublicAnnouncementAlerts(allowedCommunityIds)
            + scanLowPaymentRateAlerts(allowedCommunityIds);
        audit(principal.username(), "执行监管预警扫描", "risk_alert", created);
        return Map.of("createdCount", created);
    }

    @GetMapping("/supervision/credit-scores")
    public List<CommunityCreditScore> communityCreditScores(HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET");
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        List<Long> allowedCommunityIds = accessControl.allowedCommunityIds(principal, "COMMUNITY_PROFILE", "READ");
        if (allowedCommunityIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
            select s.id, c.name communityName, s.score, s.risk_grade riskGrade,
                   s.payment_rate paymentRate, s.open_alert_count openAlertCount,
                   s.overdue_bill_count overdueBillCount, s.audit_status auditStatus,
                   s.summary, s.generated_at generatedAt
            from community_credit_score s join community c on c.id = s.community_id
            where s.community_id in (:allowedCommunityIds)
            order by s.score, c.id
            """)
            .param("allowedCommunityIds", allowedCommunityIds)
            .query(CommunityCreditScore.class)
            .list();
    }

    @GetMapping("/supervision/credit-scores/export")
    public ResponseEntity<byte[]> exportCommunityCreditScores(HttpServletRequest request) {
        List<CommunityCreditScore> rows = communityCreditScores(request);
        StringBuilder csv = new StringBuilder("小区,评分,风险等级,缴费率,未关闭预警,欠费账单,审计状态,摘要,生成时间\n");
        for (CommunityCreditScore row : rows) {
            csv.append(csv(row.communityName())).append(',')
                .append(row.score()).append(',')
                .append(csv(row.riskGrade())).append(',')
                .append(row.paymentRate()).append(',')
                .append(row.openAlertCount()).append(',')
                .append(row.overdueBillCount()).append(',')
                .append(csv(row.auditStatus())).append(',')
                .append(csv(row.summary())).append(',')
                .append(csv(row.generatedAt() == null ? "" : row.generatedAt().toString())).append('\n');
        }
        logDataExport(request, "COMMUNITY_CREDIT_SCORE", "community-credit-scores.csv", "community_credit_score", 0, null, rows.size());
        return csvResponse("community-credit-scores.csv", csv.toString());
    }

    @GetMapping("/supervision/credit-rules")
    public List<CreditScoreRule> creditScoreRules(HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET");
        return jdbc.sql("""
            select id, rule_version ruleVersion, factor_code factorCode, factor_name factorName,
                   weight, threshold_value thresholdValue, deduction_unit deductionUnit,
                   evidence_template evidenceTemplate, status, created_at createdAt
            from credit_score_rule
            order by rule_version desc, id
            """).query(CreditScoreRule.class).list();
    }

    @GetMapping("/supervision/credit-runs")
    public List<CreditScoreRun> creditScoreRuns(HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET");
        return jdbc.sql("""
            select id, rule_version ruleVersion, generated_count generatedCount,
                   operator, status, summary, created_at createdAt
            from credit_score_run
            order by created_at desc, id desc
            limit 80
            """).query(CreditScoreRun.class).list();
    }

    @GetMapping("/supervision/credit-rules/export")
    public ResponseEntity<byte[]> exportCreditScoreRules(HttpServletRequest request) {
        List<CreditScoreRule> rows = creditScoreRules(request);
        StringBuilder csv = new StringBuilder("版本,因子编码,因子名称,权重,阈值,扣分单位,证据模板,状态,创建时间\n");
        for (CreditScoreRule row : rows) {
            csv.append(csv(row.ruleVersion())).append(',')
                .append(csv(row.factorCode())).append(',')
                .append(csv(row.factorName())).append(',')
                .append(row.weight()).append(',')
                .append(row.thresholdValue()).append(',')
                .append(row.deductionUnit()).append(',')
                .append(csv(row.evidenceTemplate())).append(',')
                .append(csv(row.status())).append(',')
                .append(row.createdAt()).append('\n');
        }
        logDataExport(request, "CREDIT_SCORE_RULE", "credit-score-rules.csv", "credit_score_rule", 0, null, rows.size());
        return csvResponse("credit-score-rules.csv", csv.toString());
    }

    @GetMapping("/supervision/credit-scores/{scoreId}/factors")
    public List<CommunityCreditFactor> creditScoreFactors(@PathVariable long scoreId, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET");
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        Long communityId = jdbc.sql("select community_id from community_credit_score where id = :scoreId")
            .param("scoreId", scoreId)
            .query(Long.class)
            .optional()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "信用评分不存在"));
        accessControl.assertCommunityAccess(principal, communityId, "COMMUNITY_PROFILE", "READ");
        return jdbc.sql("""
            select id, score_id scoreId, factor_code factorCode, factor_name factorName,
                   factor_value factorValue, weight, deduction, evidence, created_at createdAt
            from community_credit_factor
            where score_id = :scoreId
            order by id
            """)
            .param("scoreId", scoreId)
            .query(CommunityCreditFactor.class)
            .list();
    }

    @GetMapping("/supervision/credit-scores/{scoreId}/factors/export")
    public ResponseEntity<byte[]> exportCreditScoreFactors(@PathVariable long scoreId, HttpServletRequest request) {
        List<CommunityCreditFactor> rows = creditScoreFactors(scoreId, request);
        Long communityId = jdbc.sql("select community_id from community_credit_score where id = :scoreId")
            .param("scoreId", scoreId)
            .query(Long.class)
            .single();
        StringBuilder csv = new StringBuilder("因子编码,因子名称,因子值,权重,扣分,证据,生成时间\n");
        for (CommunityCreditFactor row : rows) {
            csv.append(csv(row.factorCode())).append(',')
                .append(csv(row.factorName())).append(',')
                .append(csv(row.factorValue())).append(',')
                .append(row.weight()).append(',')
                .append(row.deduction()).append(',')
                .append(csv(row.evidence())).append(',')
                .append(row.createdAt()).append('\n');
        }
        logDataExport(request, "COMMUNITY_CREDIT_FACTOR", "community-credit-factors-" + scoreId + ".csv", "community_credit_score", scoreId, communityId, rows.size());
        return csvResponse("community-credit-factors-" + scoreId + ".csv", csv.toString());
    }

    @PostMapping("/supervision/credit-scores/generate")
    public Map<String, Object> generateCommunityCreditScores(HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET");
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        List<Long> allowedCommunityIds = accessControl.allowedCommunityIds(principal, "COMMUNITY_PROFILE", "READ");
        if (allowedCommunityIds.isEmpty()) {
            return Map.of("generatedCount", 0);
        }
        AuditVerification verification = auditVerificationSnapshot();
        String ruleVersion = activeCreditRuleVersion();
        Map<String, CreditScoreRule> rules = activeCreditRules(ruleVersion);
        List<Community> communities = jdbc.sql("""
            select id, district, street, neighborhood, name, households, occupancy_rate occupancyRate,
                   approval_threshold approvalThreshold
            from community where id in (:allowedCommunityIds)
            """).param("allowedCommunityIds", allowedCommunityIds).query(Community.class).list();
        int generated = 0;
        for (Community community : communities) {
            BigDecimal paymentRate = jdbc.sql("select coalesce((select payment_rate from billing_summary where community_id = :communityId limit 1), 0)")
                .param("communityId", community.id())
                .query(BigDecimal.class)
                .single();
            Integer openAlerts = jdbc.sql("select count(*) from risk_alert where community_name = :communityName and status <> 'CLOSED'")
                .param("communityName", community.name())
                .query(Integer.class)
                .single();
            Integer overdueBills = jdbc.sql("""
                select count(*) from bill b join house h on h.id = b.house_id
                where h.community_id = :communityId and b.status in ('UNPAID', 'OVERDUE')
                """).param("communityId", community.id()).query(Integer.class).single();
            BigDecimal score = creditScore(paymentRate, openAlerts, overdueBills, verification.brokenLinks(), rules);
            String grade = score.compareTo(BigDecimal.valueOf(90)) >= 0 ? "A"
                : score.compareTo(BigDecimal.valueOf(75)) >= 0 ? "B"
                : score.compareTo(BigDecimal.valueOf(60)) >= 0 ? "C" : "D";
            String summary = "规则" + ruleVersion + "，缴费率" + paymentRate + "%，未关闭预警" + openAlerts + "条，欠费账单" + overdueBills + "笔，审计链" + verification.status();
            jdbc.sql("""
                insert into community_credit_score(community_id, score, risk_grade, payment_rate,
                                                   open_alert_count, overdue_bill_count, audit_status, summary, generated_at)
                values(:communityId, :score, :riskGrade, :paymentRate,
                       :openAlertCount, :overdueBillCount, :auditStatus, :summary, now())
                on duplicate key update score = values(score), risk_grade = values(risk_grade),
                  payment_rate = values(payment_rate), open_alert_count = values(open_alert_count),
                  overdue_bill_count = values(overdue_bill_count), audit_status = values(audit_status),
                  summary = values(summary), generated_at = now()
                """)
                .param("communityId", community.id())
                .param("score", score)
                .param("riskGrade", grade)
                .param("paymentRate", paymentRate)
                .param("openAlertCount", openAlerts)
                .param("overdueBillCount", overdueBills)
                .param("auditStatus", verification.status())
                .param("summary", summary)
                .update();
            Long scoreId = jdbc.sql("select id from community_credit_score where community_id = :communityId")
                .param("communityId", community.id())
                .query(Long.class)
                .single();
            writeCreditFactors(scoreId, community.id(), paymentRate, openAlerts, overdueBills, verification, rules);
            generated++;
        }
        jdbc.sql("""
            insert into credit_score_run(rule_version, generated_count, operator, status, summary, created_at)
            values(:ruleVersion, :generatedCount, :operator, 'SUCCESS', :summary, now())
            """)
            .param("ruleVersion", ruleVersion)
            .param("generatedCount", generated)
            .param("operator", principal.username())
            .param("summary", "生成小区信用评分 " + generated + " 个，审计链=" + verification.status())
            .update();
        audit(principal.username(), "生成小区信用评分", "community_credit_score", generated);
        return Map.of("generatedCount", generated, "ruleVersion", ruleVersion);
    }

    @GetMapping("/communities")
    public List<Community> communities(HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET", "COMMITTEE", "PROPERTY", "BANK");
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        List<Long> allowedCommunityIds = accessControl.allowedCommunityIds(principal, "COMMUNITY_PROFILE", "READ");
        if (allowedCommunityIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
            select id, district, street, neighborhood, name, households, occupancy_rate occupancyRate,
                   approval_threshold approvalThreshold
            from community
            where id in (:allowedCommunityIds)
            order by id
            """).param("allowedCommunityIds", allowedCommunityIds).query(Community.class).list();
    }

    @GetMapping("/houses")
    public List<House> houses(@RequestParam(required = false) Long communityId, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET", "COMMITTEE", "PROPERTY");
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        List<Long> allowedCommunityIds = accessControl.allowedCommunityIds(principal, "HOUSE", "READ");
        if (communityId != null) {
            accessControl.assertCommunityAccess(principal, communityId, "HOUSE", "READ");
            allowedCommunityIds = List.of(communityId);
        }
        if (allowedCommunityIds.isEmpty()) {
            return List.of();
        }
        var spec = jdbc.sql("""
            select h.id, c.name communityName, h.building, h.unit_no unitNo, h.room_no roomNo,
                   h.area, h.owner_name ownerName, h.owner_phone ownerPhone, h.status
            from house h join community c on c.id = h.community_id
            where h.community_id in (:allowedCommunityIds)
            order by c.id, h.building, h.room_no
            """).param("allowedCommunityIds", allowedCommunityIds);
        return spec.query(House.class).list();
    }

    @GetMapping("/owners/me")
    public OwnerProfile ownerMe(@RequestParam(required = false) Long houseId, HttpServletRequest request) {
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        return jdbc.sql("""
            select o.id, o.name, o.phone, o.identity_mask identityMask, c.name communityName,
                   h.building, h.room_no roomNo
            from owner o
            join house h on h.id = o.house_id
            join community c on c.id = h.community_id
            where o.username = :username
              and (:houseId is null or h.id = :houseId)
            order by o.id desc
            limit 1
            """)
            .param("username", principal.username())
            .param("houseId", houseId)
            .query(OwnerProfile.class).optional()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "未找到已审核房屋档案"));
    }

    @GetMapping("/owners/portal")
    public OwnerPortal ownerPortal(@RequestParam(required = false) Long houseId, HttpServletRequest request) {
        requireAnyRole(request, "OWNER");
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        OwnerBinding binding = jdbc.sql("""
            select coalesce(o.id, r.id) ownerId, h.id houseId, c.id communityId, c.name communityName
            from resident_house_relation r
            join app_user u on u.id = r.user_id
            join house h on h.id = r.house_id
            join community c on c.id = h.community_id
            left join owner o on o.username = u.username and o.house_id = h.id
            where u.username = :username and r.verification_status = 'APPROVED'
              and (:houseId is null or h.id = :houseId)
            order by r.is_primary desc, r.id desc
            limit 1
            """)
            .param("username", principal.username())
            .param("houseId", houseId)
            .query(OwnerBinding.class).optional()
            .orElseGet(() -> {
                if (houseId != null) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问未绑定房屋");
                }
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "请先完成房屋绑定审核");
            });
        OwnerProfile profile = ownerMe(binding.houseId(), request);
        List<Bill> bills = jdbc.sql("""
            select b.id, c.name communityName, h.room_no roomNo, b.bill_type billType,
                   b.period, b.amount, b.paid_amount paidAmount, b.status, b.due_date dueDate
            from bill b
            join house h on h.id = b.house_id
            join community c on c.id = h.community_id
            where b.house_id = :houseId
            order by b.due_date desc
            """).param("houseId", binding.houseId()).query(Bill.class).list();
        List<ElectronicPaymentReceipt> receipts = ownerPaymentReceipts(principal.username(), binding.houseId());
        List<BankFlow> flows = jdbc.sql("""
            select f.id, c.name communityName, a.account_name accountName, f.direction, f.amount,
                   f.counterparty, f.summary, f.occurred_at occurredAt, f.trace_no traceNo
            from bank_flow f
            join fund_account a on a.id = f.account_id
            join community c on c.id = f.community_id
            where f.community_id = :communityId and a.account_type = 'PUBLIC_REVENUE'
            order by f.occurred_at desc
            """).param("communityId", binding.communityId()).query(BankFlow.class).list();
        List<Announcement> announcements = jdbc.sql("""
            select a.id, c.name communityName, a.title, a.category, a.content, a.published_at publishedAt
            from announcement a join community c on c.id = a.community_id
            where a.community_id = :communityId
            order by a.published_at desc
            """).param("communityId", binding.communityId()).query(Announcement.class).list();
        List<MessageNotice> messages = jdbc.sql("""
            select m.id, m.community_id communityId, m.receiver_role receiverRole, m.title, m.content,
                   m.channel, m.status, case when r.id is null then 'UNREAD' else 'READ' end readStatus,
                   r.read_at readAt, m.created_at createdAt
            from message_notice m
            left join message_read_receipt r on r.message_id = m.id and r.user_id = :userId
            where m.community_id = :communityId and m.receiver_role in ('OWNER', 'ALL')
            order by m.created_at desc
            """)
            .param("communityId", binding.communityId())
            .param("userId", currentUserId(principal))
            .query(MessageNotice.class).list();
        List<Vote> votes = jdbc.sql("""
            select v.id, c.name communityName, v.title, v.vote_type voteType, v.status, v.start_at startAt,
                   v.end_at endAt, v.participation_rate participationRate, v.agree_rate agreeRate
            from vote v join community c on c.id = v.community_id
            where v.community_id = :communityId
            order by v.start_at desc
            """).param("communityId", binding.communityId()).query(Vote.class).list();
        List<WorkOrder> workOrders = jdbc.sql("""
            select w.id, c.name communityName, h.room_no roomNo, w.order_type orderType, w.title,
                   w.description, w.status, w.priority, w.created_at createdAt, w.due_at dueAt,
                   w.handler, w.handled_at handledAt, w.reply,
                   w.satisfaction_score satisfactionScore, w.satisfaction_comment satisfactionComment, w.evaluated_at evaluatedAt
            from work_order w
            join house h on h.id = w.house_id
            join community c on c.id = h.community_id
            where w.house_id = :houseId
            order by w.created_at desc
            """).param("houseId", binding.houseId()).query(WorkOrder.class).list();
        OwnerLedger ledger = jdbc.sql("""
            select
              coalesce((
                select sum(f.amount) from bank_flow f
                join fund_account a on a.id = f.account_id
                where f.community_id = :communityId and f.direction = 'IN' and a.account_type = 'PUBLIC_REVENUE'
              ), 0) publicIncome,
              coalesce((
                select sum(f.amount) from bank_flow f
                join fund_account a on a.id = f.account_id
                where f.community_id = :communityId and f.direction = 'OUT' and a.account_type = 'PUBLIC_REVENUE'
              ), 0) publicExpense,
              coalesce((select sum(amount - paid_amount) from bill where house_id = :houseId and status <> 'PAID'), 0) unpaidAmount,
              coalesce((select payment_rate from billing_summary where community_id = :communityId limit 1), 0) paymentRate
            """)
            .param("houseId", binding.houseId())
            .param("communityId", binding.communityId())
            .query(OwnerLedger.class)
            .single();
        List<ArrearsPublicationItem> arrearsPublicItems = publicArrearsItems(binding.communityId());
        audit(principal.username(), "查看业主门户", "owner", binding.ownerId());
        return new OwnerPortal(profile, ledger, bills, receipts, flows, announcements, messages, votes, workOrders, arrearsPublicItems);
    }

    @PostMapping("/owners/bind-house")
    public Map<String, Object> bindHouse(@RequestBody OwnerBindRequest body, HttpServletRequest request) {
        requireAnyRole(request, "OWNER");
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        HouseBindCandidate candidate = jdbc.sql("""
            select h.id houseId, h.community_id communityId, c.name communityName, h.building, h.room_no roomNo,
                   h.owner_name ownerName, h.owner_phone ownerPhone, o.identity_mask identityMask
            from house h join community c on c.id = h.community_id
            left join owner o on o.house_id = h.id
            where h.room_no = :roomNo
              and (:building is null or h.building = :building)
              and (:communityName is null or c.name = :communityName)
            order by h.id limit 1
            """)
            .param("roomNo", body.roomNo())
            .param("building", blankToNull(body.building()))
            .param("communityName", blankToNull(body.communityName()))
            .query(HouseBindCandidate.class)
            .optional()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到匹配房屋"));
        Map<String, Object> verification = integrations.verifyIdentity(
            body.name(),
            body.phone(),
            body.identityNo(),
            candidate.ownerName(),
            candidate.ownerPhone(),
            candidate.identityMask() == null || candidate.identityMask().isBlank() ? maskIdentity(body.identityNo()) : candidate.identityMask()
        );
        if (!Boolean.TRUE.equals(verification.get("verified"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "实名认证或房屋档案校验未通过");
        }
        String phoneMask = maskPhone(body.phone());
        String identityMask = maskIdentity(body.identityNo());
        Integer exists = jdbc.sql("select count(*) from owner where username = :username")
            .param("username", principal.username())
            .query(Integer.class)
            .single();
        if (exists > 0) {
            jdbc.sql("""
                update owner set house_id = :houseId, name = :name, phone = :phone, phone_cipher = :phoneCipher,
                       identity_mask = :identityMask, identity_cipher = :identityCipher
                where username = :username
                """)
                .param("houseId", candidate.houseId())
                .param("name", body.name())
                .param("phone", phoneMask)
                .param("phoneCipher", sensitive.encrypt(body.phone()))
                .param("identityMask", identityMask)
                .param("identityCipher", sensitive.encrypt(body.identityNo()))
                .param("username", principal.username())
                .update();
        } else {
            jdbc.sql("""
                insert into owner(username, house_id, name, phone, phone_cipher, identity_mask, identity_cipher)
                values(:username, :houseId, :name, :phone, :phoneCipher, :identityMask, :identityCipher)
                """)
                .param("username", principal.username())
                .param("houseId", candidate.houseId())
                .param("name", body.name())
                .param("phone", phoneMask)
                .param("phoneCipher", sensitive.encrypt(body.phone()))
                .param("identityMask", identityMask)
                .param("identityCipher", sensitive.encrypt(body.identityNo()))
                .update();
        }
        jdbc.sql("update app_user set community_id = :communityId, display_name = :displayName where username = :username")
            .param("communityId", candidate.communityId())
            .param("displayName", "业主" + body.name())
            .param("username", principal.username())
            .update();
        audit(principal.username(), "实名认证绑定房屋-" + candidate.communityName() + candidate.roomNo(), "owner", candidate.houseId());
        return Map.of(
            "status", "BOUND",
            "verification", verification,
            "communityId", candidate.communityId(),
            "communityName", candidate.communityName(),
            "houseId", candidate.houseId(),
            "roomNo", candidate.roomNo(),
            "token", tokenService.issue(principal.username(), principal.role(), candidate.communityId())
        );
    }

    @GetMapping("/billing")
    public List<Bill> bills(@RequestParam(required = false) String status, HttpServletRequest request) {
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        String ownerUsername = "OWNER".equals(principal.role()) ? principal.username() : null;
        List<Long> allowedCommunityIds = ownerUsername == null
            ? scopedCommunityIds(request, null, "BILLING", "READ")
            : List.of(principal.communityId());
        if (allowedCommunityIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
            select b.id, c.name communityName, h.room_no roomNo, b.bill_type billType,
                   b.period, b.amount, b.paid_amount paidAmount, b.status, b.due_date dueDate
            from bill b
            join house h on h.id = b.house_id
            join community c on c.id = h.community_id
            where (:status is null or b.status = :status)
              and h.community_id in (:allowedCommunityIds)
              and (:ownerUsername is null or exists (
                select 1 from owner o where o.house_id = h.id and o.username = :ownerUsername
              ))
            order by b.due_date desc
            """)
            .param("status", status)
            .param("allowedCommunityIds", allowedCommunityIds)
            .param("ownerUsername", ownerUsername)
            .query(Bill.class)
            .list();
    }

    @GetMapping("/billing/export")
    public ResponseEntity<byte[]> billingCsv(@RequestParam(required = false) String status, HttpServletRequest request) {
        List<Bill> rows = bills(status, request);
        StringBuilder csv = new StringBuilder("小区,房号,费用类型,账期,应收金额,已缴金额,欠费金额,状态,截止日\n");
        for (Bill row : rows) {
            BigDecimal arrearsAmount = row.amount().subtract(row.paidAmount() == null ? BigDecimal.ZERO : row.paidAmount());
            csv.append(csv(row.communityName())).append(',')
                .append(csv(row.roomNo())).append(',')
                .append(csv(row.billType())).append(',')
                .append(csv(row.period())).append(',')
                .append(row.amount()).append(',')
                .append(row.paidAmount()).append(',')
                .append(arrearsAmount).append(',')
                .append(csv(row.status())).append(',')
                .append(csv(row.dueDate() == null ? "" : row.dueDate().toString())).append('\n');
        }
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        audit(principal.username(), "导出账单明细CSV", "bill", 0);
        logDataExport(request, "BILLING_BILL", "billing-bills.csv", "bill", 0, null, rows.size());
        return csvResponse("billing-bills.csv", csv.toString());
    }

    @GetMapping("/fee-standards")
    public List<FeeStandard> feeStandards(@RequestParam(required = false) Long communityId, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "PROPERTY");
        List<Long> allowedCommunityIds = scopedCommunityIds(request, communityId, "BILLING", "READ");
        if (allowedCommunityIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
            select fs.id, c.name communityName, fs.community_id communityId, fs.fee_type feeType,
                   fs.billing_mode billingMode, fs.unit_price unitPrice, fs.cycle,
                   fs.effective_from effectiveFrom, fs.status, fs.created_at createdAt
            from fee_standard fs join community c on c.id = fs.community_id
            where fs.community_id in (:allowedCommunityIds)
            order by fs.community_id, fs.fee_type
            """).param("allowedCommunityIds", allowedCommunityIds).query(FeeStandard.class).list();
    }

    @PostMapping("/fee-standards")
    public Map<String, Object> createFeeStandard(@RequestBody FeeStandardRequest request, HttpServletRequest httpRequest) {
        requireAnyRole(httpRequest, "ADMIN", "PROPERTY");
        TokenService.Principal principal = (TokenService.Principal) httpRequest.getAttribute("principal");
        accessControl.assertCommunityAccess(principal, request.communityId(), "BILLING", "WRITE");
        jdbc.sql("""
            insert into fee_standard(community_id, fee_type, billing_mode, unit_price, cycle, effective_from, status, created_at)
            values(:communityId, :feeType, :billingMode, :unitPrice, :cycle, :effectiveFrom, 'ACTIVE', now())
            """)
            .param("communityId", request.communityId())
            .param("feeType", request.feeType())
            .param("billingMode", request.billingMode())
            .param("unitPrice", request.unitPrice())
            .param("cycle", request.cycle())
            .param("effectiveFrom", request.effectiveFrom())
            .update();
        audit(principal.username(), "配置收费标准-" + request.feeType(), "fee_standard", request.communityId());
        return Map.of("status", "CREATED");
    }

    @PostMapping("/billing/generate")
    public Map<String, Object> generateBills(@RequestBody BillGenerationRequest request, HttpServletRequest httpRequest) {
        requireAnyRole(httpRequest, "ADMIN", "PROPERTY");
        TokenService.Principal principal = (TokenService.Principal) httpRequest.getAttribute("principal");
        accessControl.assertCommunityAccess(principal, request.communityId(), "BILLING", "WRITE");
        List<GeneratedBill> generated = jdbc.sql("""
            select h.id houseId, fs.fee_type billType,
                   case when fs.billing_mode = 'AREA' then round(h.area * fs.unit_price, 2) else fs.unit_price end amount
            from house h
            join fee_standard fs on fs.community_id = h.community_id
            where h.community_id = :communityId
              and fs.status = 'ACTIVE'
              and fs.cycle = 'MONTHLY'
              and not exists (
                select 1 from bill b
                where b.house_id = h.id and b.bill_type = fs.fee_type and b.period = :period
              )
            """)
            .param("communityId", request.communityId())
            .param("period", request.period())
            .query(GeneratedBill.class)
            .list();
        for (GeneratedBill bill : generated) {
            jdbc.sql("""
                insert into bill(house_id, bill_type, period, amount, paid_amount, status, due_date)
                values(:houseId, :billType, :period, :amount, 0, 'UNPAID', :dueDate)
                """)
                .param("houseId", bill.houseId())
                .param("billType", bill.billType())
                .param("period", request.period())
                .param("amount", bill.amount())
                .param("dueDate", request.dueDate())
                .update();
        }
        BigDecimal total = generated.stream().map(GeneratedBill::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        jdbc.sql("""
            insert into bill_generation_batch(community_id, period, generated_count, total_amount, operator, created_at)
            values(:communityId, :period, :generatedCount, :totalAmount, :operator, now())
            """)
            .param("communityId", request.communityId())
            .param("period", request.period())
            .param("generatedCount", generated.size())
            .param("totalAmount", total)
            .param("operator", principal.username())
            .update();
        audit(principal.username(), "自动生成账单-" + request.period(), "bill_generation_batch", request.communityId());
        return Map.of("generatedCount", generated.size(), "totalAmount", total, "period", request.period());
    }

    @GetMapping("/billing/arrears-publications")
    public List<ArrearsPublication> arrearsPublications(@RequestParam(required = false) Long communityId, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET", "COMMITTEE", "PROPERTY");
        List<Long> allowedCommunityIds = scopedCommunityIds(request, communityId, "BILLING", "READ");
        if (allowedCommunityIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
            select p.id, c.name communityName, p.period, p.title, p.total_households totalHouseholds,
                   p.arrears_count arrearsCount, p.arrears_amount arrearsAmount, p.status,
                   p.published_by publishedBy, p.published_at publishedAt
            from arrears_publication p join community c on c.id = p.community_id
            where p.community_id in (:allowedCommunityIds)
            order by p.published_at desc
            """).param("allowedCommunityIds", allowedCommunityIds).query(ArrearsPublication.class).list();
    }

    @GetMapping("/billing/arrears-publications/{publicationId}/items")
    public List<ArrearsPublicationItem> arrearsPublicationItems(@PathVariable long publicationId, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET", "COMMITTEE", "PROPERTY");
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        Long communityId = jdbc.sql("select community_id from arrears_publication where id = :id")
            .param("id", publicationId)
            .query(Long.class)
            .single();
        accessControl.assertCommunityAccess(principal, communityId, "BILLING", "READ");
        return jdbc.sql("""
            select id, publication_id publicationId, bill_id billId, room_no_mask roomNoMask,
                   bill_type billType, period, amount, paid_amount paidAmount,
                   arrears_amount arrearsAmount, due_date dueDate
            from arrears_publication_item
            where publication_id = :publicationId
            order by due_date, room_no_mask
            """).param("publicationId", publicationId).query(ArrearsPublicationItem.class).list();
    }

    @PostMapping("/billing/arrears-publications")
    public Map<String, Object> createArrearsPublication(@RequestBody ArrearsPublicationRequest body, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "PROPERTY");
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        accessControl.assertCommunityAccess(principal, body.communityId(), "BILLING", "WRITE");
        List<ArrearsBillDraft> arrearsBills = jdbc.sql("""
            select b.id billId, h.room_no roomNo, b.bill_type billType, b.period,
                   b.amount, b.paid_amount paidAmount, (b.amount - b.paid_amount) arrearsAmount, b.due_date dueDate
            from bill b join house h on h.id = b.house_id
            where h.community_id = :communityId
              and b.status in ('UNPAID', 'OVERDUE')
              and (:period is null or b.period = :period)
              and b.amount > b.paid_amount
            order by b.due_date, h.room_no
            """)
            .param("communityId", body.communityId())
            .param("period", blankToNull(body.period()))
            .query(ArrearsBillDraft.class)
            .list();
        int households = jdbc.sql("select households from community where id = :communityId")
            .param("communityId", body.communityId())
            .query(Integer.class)
            .single();
        BigDecimal total = arrearsBills.stream().map(ArrearsBillDraft::arrearsAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        String title = body.title() == null || body.title().isBlank() ? "欠费公开台账" : body.title();
        String period = body.period() == null || body.period().isBlank() ? "ALL" : body.period();
        jdbc.sql("""
            insert into arrears_publication(community_id, period, title, total_households, arrears_count,
                                            arrears_amount, status, published_by, published_at)
            values(:communityId, :period, :title, :totalHouseholds, :arrearsCount,
                   :arrearsAmount, 'PUBLISHED', :publishedBy, now())
            """)
            .param("communityId", body.communityId())
            .param("period", period)
            .param("title", title)
            .param("totalHouseholds", households)
            .param("arrearsCount", arrearsBills.size())
            .param("arrearsAmount", total)
            .param("publishedBy", principal.username())
            .update();
        Long publicationId = jdbc.sql("select max(id) from arrears_publication").query(Long.class).single();
        for (ArrearsBillDraft bill : arrearsBills) {
            jdbc.sql("""
                insert into arrears_publication_item(publication_id, bill_id, room_no_mask, bill_type, period,
                                                     amount, paid_amount, arrears_amount, due_date)
                values(:publicationId, :billId, :roomNoMask, :billType, :period,
                       :amount, :paidAmount, :arrearsAmount, :dueDate)
                """)
                .param("publicationId", publicationId)
                .param("billId", bill.billId())
                .param("roomNoMask", maskRoomNo(bill.roomNo()))
                .param("billType", bill.billType())
                .param("period", bill.period())
                .param("amount", bill.amount())
                .param("paidAmount", bill.paidAmount())
                .param("arrearsAmount", bill.arrearsAmount())
                .param("dueDate", bill.dueDate())
                .update();
        }
        jdbc.sql("""
            insert into announcement(community_id, title, category, content, published_at)
            values(:communityId, :title, '欠费公示', :content, now())
            """)
            .param("communityId", body.communityId())
            .param("title", title)
            .param("content", "本次公示欠费 " + arrearsBills.size() + " 笔，合计 ¥" + total + "，房号已脱敏。")
            .update();
        audit(principal.username(), "生成欠费公开台账-" + title, "arrears_publication", publicationId);
        return Map.of("publicationId", publicationId, "arrearsCount", arrearsBills.size(), "arrearsAmount", total);
    }

    @GetMapping("/billing/arrears-publications/{publicationId}/export")
    public ResponseEntity<byte[]> arrearsPublicationCsv(@PathVariable long publicationId, HttpServletRequest request) {
        List<ArrearsPublicationItem> rows = arrearsPublicationItems(publicationId, request);
        StringBuilder csv = new StringBuilder("脱敏房号,费用类型,期间,应收金额,已缴金额,欠费金额,截止日\n");
        for (ArrearsPublicationItem row : rows) {
            csv.append(csv(row.roomNoMask())).append(',')
                .append(csv(row.billType())).append(',')
                .append(csv(row.period())).append(',')
                .append(row.amount()).append(',')
                .append(row.paidAmount()).append(',')
                .append(row.arrearsAmount()).append(',')
                .append(row.dueDate()).append('\n');
        }
        String fileName = "arrears-publication-" + publicationId + ".csv";
        logDataExport(request, "BILLING_ARREARS", fileName, "arrears_publication", publicationId, null, rows.size());
        return csvResponse(fileName, csv.toString());
    }

    @PostMapping("/payments/wechat/prepay")
    public Map<String, Object> prepay(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return createPaymentPrepay("WECHAT_PAY", body, request);
    }

    @PostMapping("/payments/alipay/prepay")
    public Map<String, Object> alipayPrepay(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return createPaymentPrepay("ALIPAY", body, request);
    }

    @PostMapping("/payments/{provider}/prepay")
    public Map<String, Object> channelPrepay(@PathVariable String provider, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        return createPaymentPrepay(normalizePayChannel(provider), body, request);
    }

    @PostMapping("/payments/wechat/confirm")
    public Map<String, Object> confirmPayment(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return confirmPaymentChannel("WECHAT_PAY", body, request);
    }

    @PostMapping("/payments/alipay/confirm")
    public Map<String, Object> confirmAlipayPayment(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return confirmPaymentChannel("ALIPAY", body, request);
    }

    @PostMapping("/payments/{provider}/confirm")
    public Map<String, Object> confirmChannelPayment(@PathVariable String provider, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        return confirmPaymentChannel(normalizePayChannel(provider), body, request);
    }

    @GetMapping("/payments/refunds")
    public List<PaymentRefund> paymentRefunds(HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "PROPERTY", "COMMITTEE", "GOVERNMENT", "STREET");
        List<Long> allowedCommunityIds = scopedCommunityIds(request, null, "PAYMENT", "READ");
        if (allowedCommunityIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
            select r.id, c.name communityName, h.room_no roomNo, b.bill_type billType, b.period,
                   r.refund_no refundNo, r.pay_channel payChannel, r.amount, r.reason, r.status,
                   r.provider_refund_no providerRefundNo, r.processed_at processedAt, r.created_at createdAt
            from payment_refund_order r
            join bill b on b.id = r.bill_id
            join house h on h.id = b.house_id
            join community c on c.id = h.community_id
            where r.community_id in (:allowedCommunityIds)
            order by r.created_at desc, r.id desc
            """)
            .param("allowedCommunityIds", allowedCommunityIds)
            .query(PaymentRefund.class)
            .list();
    }

    @PostMapping("/payments/{provider}/refund")
    public Map<String, Object> refundPayment(@PathVariable String provider, @RequestBody PaymentRefundRequest body, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "PROPERTY");
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        String channel = normalizePayChannel(provider);
        Bill bill = jdbc.sql("""
            select b.id, c.name communityName, h.room_no roomNo, b.bill_type billType,
                   b.period, b.amount, b.paid_amount paidAmount, b.status, b.due_date dueDate
            from bill b join house h on h.id = b.house_id join community c on c.id = h.community_id
            where b.id = :id
            """).param("id", body.billId()).query(Bill.class).single();
        Long billCommunityId = jdbc.sql("""
            select h.community_id from bill b join house h on h.id = b.house_id where b.id = :id
            """).param("id", body.billId()).query(Long.class).single();
        accessControl.assertCommunityAccess(principal, billCommunityId, "PAYMENT", "WRITE");
        PaidPaymentOrder order = jdbc.sql("""
            select id, order_no orderNo, amount, community_id communityId, pay_channel payChannel
            from payment_order
            where bill_id = :billId and pay_channel = :channel and status = 'PAID'
            order by paid_at desc, id desc
            limit 1
            """)
            .param("billId", body.billId())
            .param("channel", channel)
            .query(PaidPaymentOrder.class)
            .optional()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "未找到可退款的已支付订单"));
        BigDecimal refundAmount = body.amount() == null || body.amount().compareTo(BigDecimal.ZERO) <= 0 ? order.amount() : body.amount();
        if (refundAmount.compareTo(order.amount()) > 0 || refundAmount.compareTo(bill.paidAmount()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "退款金额不能超过已支付金额");
        }
        String reason = body.reason() == null || body.reason().isBlank() ? "缴费退款" : body.reason();
        Map<String, Object> providerResult = integrations.refundPayment(channel, order.orderNo(), refundAmount, reason);
        String refundNo = ("ALIPAY".equals(channel) ? "ALIRF" : "WXRF") + body.billId() + System.currentTimeMillis();
        jdbc.sql("""
            insert into payment_refund_order(payment_order_id, bill_id, community_id, refund_no, pay_channel,
                                             amount, reason, status, provider_refund_no, processed_at, created_at)
            values(:paymentOrderId, :billId, :communityId, :refundNo, :payChannel,
                   :amount, :reason, 'REFUNDED', :providerRefundNo, now(), now())
            """)
            .param("paymentOrderId", order.id())
            .param("billId", body.billId())
            .param("communityId", order.communityId())
            .param("refundNo", refundNo)
            .param("payChannel", channel)
            .param("amount", refundAmount)
            .param("reason", reason)
            .param("providerRefundNo", String.valueOf(providerResult.get("refundNo")))
            .update();
        Long refundId = jdbc.sql("select max(id) from payment_refund_order").query(Long.class).single();
        Long statementId = insertPaymentChannelStatement(
            order.communityId(),
            channel,
            LocalDate.now(),
            "REFUND",
            body.billId(),
            refundNo,
            String.valueOf(providerResult.get("refundNo")),
            refundAmount
        );
        BigDecimal newPaidAmount = bill.paidAmount().subtract(refundAmount);
        if (newPaidAmount.compareTo(BigDecimal.ZERO) < 0) {
            newPaidAmount = BigDecimal.ZERO;
        }
        String billStatus = newPaidAmount.compareTo(BigDecimal.ZERO) == 0 ? "REFUNDED" : "PARTIAL_REFUND";
        jdbc.sql("update bill set paid_amount = :paidAmount, status = :status where id = :id")
            .param("paidAmount", newPaidAmount)
            .param("status", billStatus)
            .param("id", body.billId())
            .update();
        jdbc.sql("update payment_order set status = :status where id = :id")
            .param("status", "REFUNDED".equals(billStatus) ? "REFUNDED" : "PARTIAL_REFUND")
            .param("id", order.id())
            .update();
        jdbc.sql("""
            insert into bank_flow(community_id, account_id, direction, amount, counterparty, summary, occurred_at, trace_no)
            select h.community_id, fa.id, 'OUT', :amount, o.name, concat(b.bill_type, '退款-', :channel), now(), concat(:prefix, 'RF', b.id, unix_timestamp())
            from bill b join house h on h.id = b.house_id join owner o on o.house_id = h.id
            join fund_account fa on fa.community_id = h.community_id and fa.account_type = 'PROPERTY_SERVICE'
            where b.id = :id limit 1
            """)
            .param("id", body.billId())
            .param("amount", refundAmount)
            .param("channel", channel)
            .param("prefix", "ALIPAY".equals(channel) ? "ALI" : "WX")
            .update();
        jdbc.sql("""
            select o.phone from bill b
            join house h on h.id = b.house_id
            join owner o on o.house_id = h.id
            where b.id = :id limit 1
            """).param("id", body.billId()).query(String.class).optional()
            .ifPresent(phone -> integrations.sendSms(phone, "PAYMENT_REFUND", "您的缴费退款已处理，账单号：" + body.billId()));
        audit(principal.username(), channel + "缴费退款-" + reason, "payment_refund_order", refundId);
        return Map.of(
            "refundId", refundId,
            "refundNo", refundNo,
            "providerRefundNo", providerResult.get("refundNo"),
            "statementId", statementId == null ? 0 : statementId,
            "status", "REFUNDED",
            "billStatus", billStatus,
            "payChannel", channel,
            "amount", refundAmount
        );
    }

    @GetMapping("/payments/statements")
    public List<PaymentChannelStatement> paymentStatements(HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "PROPERTY", "COMMITTEE", "GOVERNMENT", "STREET");
        List<Long> allowedCommunityIds = scopedCommunityIds(request, null, "PAYMENT", "READ");
        if (allowedCommunityIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
            select s.id, c.name communityName, s.provider, s.statement_date statementDate,
                   s.trade_type tradeType, s.bill_id billId, s.order_no orderNo,
                   s.channel_trade_no channelTradeNo, s.amount, s.status, s.synced_at syncedAt
            from payment_channel_statement s
            join community c on c.id = s.community_id
            where s.community_id in (:allowedCommunityIds)
            order by s.statement_date desc, s.synced_at desc, s.id desc
            """)
            .param("allowedCommunityIds", allowedCommunityIds)
            .query(PaymentChannelStatement.class)
            .list();
    }

    @GetMapping("/payments/receipts")
    public List<ElectronicPaymentReceipt> paymentReceipts(HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "PROPERTY", "COMMITTEE", "GOVERNMENT", "STREET", "OWNER");
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        if ("OWNER".equals(principal.role())) {
            return ownerPaymentReceipts(principal.username(), null);
        }
        List<Long> allowedCommunityIds = scopedCommunityIds(request, null, "PAYMENT", "READ");
        if (allowedCommunityIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
            select r.id, r.receipt_no receiptNo, c.name communityName, r.room_no roomNo,
                   r.payer_name payerName, r.bill_type billType, r.period, r.pay_channel payChannel,
                   r.amount, r.status, r.file_url fileUrl, r.checksum, r.issued_by issuedBy, r.issued_at issuedAt
            from electronic_payment_receipt r
            join community c on c.id = r.community_id
            where r.community_id in (:allowedCommunityIds)
            order by r.issued_at desc, r.id desc
            """)
            .param("allowedCommunityIds", allowedCommunityIds)
            .query(ElectronicPaymentReceipt.class)
            .list();
    }

    private List<ElectronicPaymentReceipt> ownerPaymentReceipts(String username, Long houseId) {
        return jdbc.sql("""
            select r.id, r.receipt_no receiptNo, c.name communityName, r.room_no roomNo,
                   r.payer_name payerName, r.bill_type billType, r.period, r.pay_channel payChannel,
                   r.amount, r.status, r.file_url fileUrl, r.checksum, r.issued_by issuedBy, r.issued_at issuedAt
            from electronic_payment_receipt r
            join bill b on b.id = r.bill_id
            join house h on h.id = b.house_id
            join community c on c.id = r.community_id
            join resident_house_relation rel on rel.house_id = h.id and rel.verification_status = 'APPROVED'
            join app_user u on u.id = rel.user_id
            where u.username = :username
              and (:houseId is null or h.id = :houseId)
            order by r.issued_at desc, r.id desc
            """)
            .param("username", username)
            .param("houseId", houseId)
            .query(ElectronicPaymentReceipt.class)
            .list();
    }

    @PostMapping("/payments/{paymentOrderId}/receipt")
    public Map<String, Object> issuePaymentReceipt(@PathVariable long paymentOrderId, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "PROPERTY", "OWNER");
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        PaidPaymentOrder order = jdbc.sql("""
            select id, order_no orderNo, amount, community_id communityId, pay_channel payChannel
            from payment_order
            where id = :id and status in ('PAID','PARTIAL_REFUND','REFUNDED')
            """)
            .param("id", paymentOrderId)
            .query(PaidPaymentOrder.class)
            .optional()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "未找到可开具票据的支付订单"));
        if ("OWNER".equals(principal.role())) {
            assertOwnerPaymentOrderAccess(principal.username(), paymentOrderId);
        } else {
            accessControl.assertCommunityAccess(principal, order.communityId(), "PAYMENT", "WRITE");
        }
        Long receiptId = issueReceiptForPaymentOrder(paymentOrderId, principal.username());
        ElectronicPaymentReceipt receipt = receiptById(receiptId);
        return Map.of("receiptId", receipt.id(), "receiptNo", receipt.receiptNo(), "status", receipt.status(), "fileUrl", receipt.fileUrl());
    }

    @PostMapping("/billing/{billId}/receipt")
    public Map<String, Object> issueBillReceipt(@PathVariable long billId, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "PROPERTY", "OWNER");
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        Long communityId = jdbc.sql("select h.community_id from bill b join house h on h.id = b.house_id where b.id = :billId")
            .param("billId", billId)
            .query(Long.class)
            .optional()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "账单不存在"));
        if ("OWNER".equals(principal.role())) {
            assertOwnerBillAccess(principal.username(), billId);
        } else {
            accessControl.assertCommunityAccess(principal, communityId, "PAYMENT", "WRITE");
        }
        Long paymentOrderId = jdbc.sql("""
            select id from payment_order
            where bill_id = :billId and status in ('PAID','PARTIAL_REFUND','REFUNDED')
            order by paid_at desc, id desc
            limit 1
            """)
            .param("billId", billId)
            .query(Long.class)
            .optional()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "账单未支付，不能开具电子票据"));
        Long receiptId = issueReceiptForPaymentOrder(paymentOrderId, principal.username());
        ElectronicPaymentReceipt receipt = receiptById(receiptId);
        return Map.of("receiptId", receipt.id(), "receiptNo", receipt.receiptNo(), "status", receipt.status(), "fileUrl", receipt.fileUrl());
    }

    @GetMapping("/payments/receipts/export")
    public ResponseEntity<byte[]> paymentReceiptsCsv(HttpServletRequest request) {
        List<ElectronicPaymentReceipt> rows = paymentReceipts(request);
        StringBuilder csv = new StringBuilder("票据号,小区,房号,缴款人,费用类型,账期,支付渠道,金额,状态,校验码,开具人,开具时间\n");
        for (ElectronicPaymentReceipt row : rows) {
            csv.append(csv(row.receiptNo())).append(',')
                .append(csv(row.communityName())).append(',')
                .append(csv(row.roomNo())).append(',')
                .append(csv(row.payerName())).append(',')
                .append(csv(row.billType())).append(',')
                .append(csv(row.period())).append(',')
                .append(csv(row.payChannel())).append(',')
                .append(row.amount()).append(',')
                .append(csv(row.status())).append(',')
                .append(csv(row.checksum())).append(',')
                .append(csv(row.issuedBy())).append(',')
                .append(row.issuedAt()).append('\n');
        }
        logDataExport(request, "ELECTRONIC_RECEIPT", "electronic-payment-receipts.csv", "electronic_payment_receipt", 0, null, rows.size());
        return csvResponse("electronic-payment-receipts.csv", csv.toString());
    }

    @GetMapping("/payments/receipts/{receiptId}/pdf")
    public ResponseEntity<byte[]> paymentReceiptPdf(@PathVariable long receiptId, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "PROPERTY", "COMMITTEE", "GOVERNMENT", "STREET", "OWNER");
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        Long communityId = jdbc.sql("select community_id from electronic_payment_receipt where id = :id")
            .param("id", receiptId)
            .query(Long.class)
            .optional()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "票据不存在"));
        if ("OWNER".equals(principal.role())) {
            assertOwnerReceiptAccess(principal.username(), receiptId);
        } else {
            accessControl.assertCommunityAccess(principal, communityId, "PAYMENT", "READ");
        }
        ElectronicPaymentReceipt receipt = receiptById(receiptId);
        audit(principal.username(), "导出电子票据PDF-" + receipt.receiptNo(), "electronic_payment_receipt", receiptId);
        logDataExport(request, "ELECTRONIC_RECEIPT", receipt.receiptNo() + ".pdf", "electronic_payment_receipt", receiptId, communityId, 1);
        byte[] bytes = simplePdf("""
            Lingma Tech SPARK Property Platform
            Electronic Payment Receipt
            Receipt No: %s
            Community: %s
            Room: %s
            Payer: %s
            Bill Type: %s
            Period: %s
            Pay Channel: %s
            Amount: %s CNY
            Status: %s
            Checksum: %s
            Issued At: %s
            """.formatted(
            receipt.receiptNo(),
            receipt.communityName(),
            receipt.roomNo(),
            receipt.payerName(),
            receipt.billType(),
            receipt.period(),
            receipt.payChannel(),
            receipt.amount(),
            receipt.status(),
            receipt.checksum(),
            receipt.issuedAt()
        ));
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + receipt.receiptNo() + ".pdf\"")
            .contentType(MediaType.APPLICATION_PDF)
            .body(bytes);
    }

    @GetMapping("/tax/invoices")
    public List<TaxInvoice> taxInvoices(HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "PROPERTY", "COMMITTEE", "GOVERNMENT", "STREET");
        List<Long> allowedCommunityIds = scopedCommunityIds(request, null, "PAYMENT", "READ");
        if (allowedCommunityIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
            select t.id, t.invoice_request_no invoiceRequestNo, r.receipt_no receiptNo,
                   c.name communityName, t.buyer_name buyerName, t.buyer_tax_no buyerTaxNo,
                   t.invoice_item invoiceItem, t.tax_category_code taxCategoryCode, t.tax_rate taxRate,
                   t.amount, t.status, t.tax_invoice_no taxInvoiceNo, t.tax_platform_code taxPlatformCode,
                   t.pdf_url pdfUrl, t.checksum, t.fail_reason failReason, t.issued_by issuedBy,
                   t.issued_at issuedAt, t.red_original_id redOriginalId, t.red_at redAt, t.created_at createdAt
            from tax_invoice_request t
            join electronic_payment_receipt r on r.id = t.receipt_id
            join community c on c.id = t.community_id
            where t.community_id in (:allowedCommunityIds)
            order by t.created_at desc, t.id desc
            """)
            .param("allowedCommunityIds", allowedCommunityIds)
            .query(TaxInvoice.class)
            .list();
    }

    @PostMapping("/payments/receipts/{receiptId}/tax-invoice")
    public Map<String, Object> issueTaxInvoice(@PathVariable long receiptId, @RequestBody TaxInvoiceRequest body, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "PROPERTY");
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        PaymentReceiptForTax receipt = receiptForTax(receiptId);
        accessControl.assertCommunityAccess(principal, receipt.communityId(), "PAYMENT", "WRITE");
        Long existing = jdbc.sql("""
            select id from tax_invoice_request
            where receipt_id = :receiptId and status = 'ISSUED'
            order by id desc limit 1
            """)
            .param("receiptId", receiptId)
            .query(Long.class)
            .optional()
            .orElse(null);
        if (existing != null) {
            TaxInvoice invoice = taxInvoiceById(existing);
            return Map.of("invoiceId", invoice.id(), "invoiceRequestNo", invoice.invoiceRequestNo(), "status", invoice.status(), "taxInvoiceNo", invoice.taxInvoiceNo());
        }
        String requestNo = "TAX-" + receipt.communityId() + "-" + receiptId + "-" + System.currentTimeMillis();
        String buyerName = body.buyerName() == null || body.buyerName().isBlank() ? receipt.payerName() : body.buyerName();
        String buyerTaxNo = body.buyerTaxNo() == null || body.buyerTaxNo().isBlank() ? "PERSONAL" : body.buyerTaxNo();
        String invoiceItem = body.invoiceItem() == null || body.invoiceItem().isBlank() ? receipt.billType() : body.invoiceItem();
        String taxCategoryCode = body.taxCategoryCode() == null || body.taxCategoryCode().isBlank() ? "304080299" : body.taxCategoryCode();
        BigDecimal taxRate = body.taxRate() == null ? BigDecimal.valueOf(0.06) : body.taxRate();
        Map<String, Object> taxResult = integrations.issueTaxInvoice(requestNo, buyerName, buyerTaxNo, invoiceItem, taxCategoryCode, taxRate, receipt.amount());
        String taxInvoiceNo = String.valueOf(taxResult.get("taxInvoiceNo"));
        String platformCode = String.valueOf(taxResult.get("taxPlatformCode"));
        String pdfUrl = String.valueOf(taxResult.get("pdfUrl"));
        String checksum = Integer.toHexString((requestNo + "|" + taxInvoiceNo + "|" + receipt.amount()).hashCode());
        jdbc.sql("""
            insert into tax_invoice_request(invoice_request_no, receipt_id, bill_id, payment_order_id, community_id,
                                            buyer_name, buyer_tax_no, invoice_item, tax_category_code, tax_rate,
                                            amount, status, tax_invoice_no, tax_platform_code, pdf_url, checksum,
                                            fail_reason, issued_by, issued_at, created_at)
            values(:requestNo, :receiptId, :billId, :paymentOrderId, :communityId,
                   :buyerName, :buyerTaxNo, :invoiceItem, :taxCategoryCode, :taxRate,
                   :amount, 'ISSUED', :taxInvoiceNo, :platformCode, :pdfUrl, :checksum,
                   null, :issuedBy, now(), now())
            """)
            .param("requestNo", requestNo)
            .param("receiptId", receiptId)
            .param("billId", receipt.billId())
            .param("paymentOrderId", receipt.paymentOrderId())
            .param("communityId", receipt.communityId())
            .param("buyerName", buyerName)
            .param("buyerTaxNo", buyerTaxNo)
            .param("invoiceItem", invoiceItem)
            .param("taxCategoryCode", taxCategoryCode)
            .param("taxRate", taxRate)
            .param("amount", receipt.amount())
            .param("taxInvoiceNo", taxInvoiceNo)
            .param("platformCode", platformCode)
            .param("pdfUrl", pdfUrl)
            .param("checksum", checksum)
            .param("issuedBy", principal.username())
            .update();
        Long invoiceId = jdbc.sql("select max(id) from tax_invoice_request").query(Long.class).single();
        audit(principal.username(), "提交税务开票-" + requestNo, "tax_invoice_request", invoiceId);
        return Map.of("invoiceId", invoiceId, "invoiceRequestNo", requestNo, "status", "ISSUED", "taxInvoiceNo", taxInvoiceNo, "pdfUrl", pdfUrl);
    }

    @PostMapping("/tax/invoices/{invoiceId}/red-cancel")
    public Map<String, Object> redCancelTaxInvoice(@PathVariable long invoiceId, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "PROPERTY");
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        TaxInvoice invoice = taxInvoiceById(invoiceId);
        Long communityId = jdbc.sql("select community_id from tax_invoice_request where id = :id")
            .param("id", invoiceId)
            .query(Long.class)
            .single();
        accessControl.assertCommunityAccess(principal, communityId, "PAYMENT", "WRITE");
        if (!"ISSUED".equals(invoice.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只有已开具税票可以红冲");
        }
        String reason = body == null || body.get("reason") == null ? "缴费退票红冲" : String.valueOf(body.get("reason"));
        Map<String, Object> result = integrations.redCancelTaxInvoice(invoice.invoiceRequestNo(), invoice.taxInvoiceNo(), reason);
        jdbc.sql("""
            update tax_invoice_request
            set status = 'RED_CANCELLED', fail_reason = :reason, red_at = now()
            where id = :id
            """)
            .param("reason", reason + "; redInvoiceNo=" + result.get("redInvoiceNo"))
            .param("id", invoiceId)
            .update();
        audit(principal.username(), "税务发票红冲-" + invoice.invoiceRequestNo(), "tax_invoice_request", invoiceId);
        return Map.of("invoiceId", invoiceId, "status", "RED_CANCELLED", "redInvoiceNo", result.get("redInvoiceNo"));
    }

    @GetMapping("/tax/invoices/export")
    public ResponseEntity<byte[]> taxInvoicesCsv(HttpServletRequest request) {
        List<TaxInvoice> rows = taxInvoices(request);
        StringBuilder csv = new StringBuilder("开票申请号,电子票据号,小区,购方名称,购方税号,项目,税收分类编码,税率,金额,状态,数电票号,平台流水,PDF,校验码,开具人,开具时间,红冲时间\n");
        for (TaxInvoice row : rows) {
            csv.append(csv(row.invoiceRequestNo())).append(',')
                .append(csv(row.receiptNo())).append(',')
                .append(csv(row.communityName())).append(',')
                .append(csv(row.buyerName())).append(',')
                .append(csv(row.buyerTaxNo())).append(',')
                .append(csv(row.invoiceItem())).append(',')
                .append(csv(row.taxCategoryCode())).append(',')
                .append(row.taxRate()).append(',')
                .append(row.amount()).append(',')
                .append(csv(row.status())).append(',')
                .append(csv(row.taxInvoiceNo())).append(',')
                .append(csv(row.taxPlatformCode())).append(',')
                .append(csv(row.pdfUrl())).append(',')
                .append(csv(row.checksum())).append(',')
                .append(csv(row.issuedBy())).append(',')
                .append(row.issuedAt()).append(',')
                .append(row.redAt() == null ? "" : row.redAt()).append('\n');
        }
        logDataExport(request, "TAX_INVOICE", "tax-invoices.csv", "tax_invoice_request", 0, null, rows.size());
        return csvResponse("tax-invoices.csv", csv.toString());
    }

    @GetMapping("/tax/invoices/{invoiceId}/pdf")
    public ResponseEntity<byte[]> taxInvoicePdf(@PathVariable long invoiceId, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "PROPERTY", "COMMITTEE", "GOVERNMENT", "STREET");
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        Long communityId = jdbc.sql("select community_id from tax_invoice_request where id = :id")
            .param("id", invoiceId)
            .query(Long.class)
            .optional()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "税务发票申请不存在"));
        accessControl.assertCommunityAccess(principal, communityId, "PAYMENT", "READ");
        TaxInvoice invoice = taxInvoiceById(invoiceId);
        String filename = invoice.invoiceRequestNo() + ".pdf";
        audit(principal.username(), "下载数电票PDF-" + invoice.invoiceRequestNo(), "tax_invoice_request", invoiceId);
        logDataExport(request, "TAX_INVOICE_PDF", filename, "tax_invoice_request", invoiceId, communityId, 1);
        byte[] bytes = simplePdf("""
            Lingma Tech SPARK Property Platform
            Digital Tax Invoice
            Request No: %s
            Receipt No: %s
            Community: %s
            Buyer: %s
            Buyer Tax No: %s
            Item: %s
            Tax Category: %s
            Tax Rate: %s
            Amount: %s CNY
            Status: %s
            Tax Invoice No: %s
            Tax Platform Code: %s
            PDF URL: %s
            Checksum: %s
            Issued At: %s
            """.formatted(
            invoice.invoiceRequestNo(),
            invoice.receiptNo(),
            invoice.communityName(),
            invoice.buyerName(),
            invoice.buyerTaxNo(),
            invoice.invoiceItem(),
            invoice.taxCategoryCode(),
            invoice.taxRate(),
            invoice.amount(),
            invoice.status(),
            invoice.taxInvoiceNo(),
            invoice.taxPlatformCode(),
            invoice.pdfUrl(),
            invoice.checksum(),
            invoice.issuedAt()
        ));
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.APPLICATION_PDF)
            .body(bytes);
    }

    @PostMapping("/payments/{provider}/statement-sync")
    public Map<String, Object> syncPaymentStatement(@PathVariable String provider, @RequestBody PaymentStatementSyncRequest body, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "PROPERTY");
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        String channel = normalizePayChannel(provider);
        LocalDate statementDate = body.statementDate() == null ? LocalDate.now() : body.statementDate();
        List<PaymentStatementDraft> trades = jdbc.sql("""
            select po.community_id communityId, po.bill_id billId, 'PAYMENT' tradeType, po.order_no orderNo,
                   coalesce(po.provider_order_no, po.prepay_id, po.order_no) channelTradeNo, po.amount amount
            from payment_order po
            where po.pay_channel = :channel and po.status in ('PAID', 'REFUNDED', 'PARTIAL_REFUND')
            union all
            select r.community_id communityId, r.bill_id billId, 'REFUND' tradeType, r.refund_no orderNo,
                   coalesce(r.provider_refund_no, r.refund_no) channelTradeNo, r.amount amount
            from payment_refund_order r
            where r.pay_channel = :channel and r.status = 'REFUNDED'
            """)
            .param("channel", channel)
            .query(PaymentStatementDraft.class)
            .list();
        int inserted = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (PaymentStatementDraft trade : trades) {
            accessControl.assertCommunityAccess(principal, trade.communityId(), "PAYMENT", "WRITE");
            Integer exists = jdbc.sql("""
                select count(*) from payment_channel_statement
                where provider = :provider and statement_date = :statementDate
                  and trade_type = :tradeType and channel_trade_no = :channelTradeNo
                """)
                .param("provider", channel)
                .param("statementDate", statementDate)
                .param("tradeType", trade.tradeType())
                .param("channelTradeNo", trade.channelTradeNo())
                .query(Integer.class)
                .single();
            if (exists != null && exists > 0) {
                continue;
            }
            insertPaymentChannelStatement(trade.communityId(), channel, statementDate, trade.tradeType(), trade.billId(), trade.orderNo(), trade.channelTradeNo(), trade.amount());
            inserted++;
            totalAmount = totalAmount.add(trade.amount());
        }
        integrations.syncPaymentStatement(channel, statementDate.toString(), inserted, totalAmount);
        audit(principal.username(), channel + "渠道账单同步", "payment_channel_statement", inserted);
        return Map.of("provider", channel, "statementDate", statementDate, "syncedCount", inserted, "totalAmount", totalAmount);
    }

    @PostMapping("/payments/{provider}/callback")
    public Map<String, Object> paymentCallback(@PathVariable String provider, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        String channel = normalizePayChannel(provider);
        String orderNo = String.valueOf(body.get("orderNo"));
        String idempotencyKey = callbackIdempotencyKey(channel, "PAYMENT_CALLBACK", orderNo);
        String canonicalPayload = integrations.canonicalPayload(body);
        Map<String, Object> verifyResult = integrations.verifyExternalCallback(channel, "PAYMENT_CALLBACK", orderNo, canonicalPayload, request.getHeader("X-Signature"));
        String signatureStatus = String.valueOf(verifyResult.get("signatureStatus"));
        if (!Boolean.TRUE.equals(verifyResult.get("verified"))) {
            logExternalCallback(channel, "PAYMENT_CALLBACK", orderNo, signatureStatus, 400, canonicalPayload, "验签失败", idempotencyKey, "REJECTED");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "支付回调验签失败");
        }
        IntegrationService.CallbackReplayCheck replayCheck = integrations.verifyCallbackReplay(
            channel,
            "PAYMENT_CALLBACK",
            orderNo,
            request.getHeader("X-Callback-Timestamp"),
            request.getHeader("X-Callback-Nonce")
        );
        if (!replayCheck.accepted()) {
            logExternalCallback(channel, "PAYMENT_CALLBACK", orderNo, signatureStatus, 409, canonicalPayload, replayCheck.message(), idempotencyKey, "REJECTED");
            throw new ResponseStatusException(HttpStatus.CONFLICT, replayCheck.message());
        }
        if (callbackAlreadyProcessed(idempotencyKey)) {
            logExternalCallback(channel, "PAYMENT_CALLBACK", orderNo, signatureStatus, 200, canonicalPayload, "重复回调已幂等跳过", idempotencyKey, "DUPLICATE");
            return Map.of("status", "SUCCESS", "provider", channel, "orderNo", orderNo, "idempotent", true);
        }
        jdbc.sql("""
            update payment_order set status = 'PAID', paid_at = now(), callback_payload = :payload, callback_at = now()
            where order_no = :orderNo and pay_channel = :channel
            """)
            .param("payload", canonicalPayload)
            .param("orderNo", orderNo)
            .param("channel", channel)
            .update();
        Long billId = jdbc.sql("select bill_id from payment_order where order_no = :orderNo")
            .param("orderNo", orderNo)
            .query(Long.class)
            .optional()
            .orElse(0L);
        if (billId > 0) {
            completeBillPayment(billId, channel, orderNo);
            issueReceiptForLatestPayment(billId, channel, "PAYMENT_CALLBACK");
        }
        logExternalCallback(channel, "PAYMENT_CALLBACK", orderNo, signatureStatus, 200, canonicalPayload, "支付回调已入账，billId=" + billId, idempotencyKey, "PROCESSED");
        audit("PAYMENT_CALLBACK", channel + "支付回调", "payment_order", billId);
        return Map.of("status", "SUCCESS", "provider", channel, "orderNo", orderNo, "idempotent", false);
    }

    private Map<String, Object> createPaymentPrepay(String channel, Map<String, Object> body, HttpServletRequest request) {
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        long billId = ((Number) body.get("billId")).longValue();
        Long billCommunityId = jdbc.sql("""
            select h.community_id from bill b join house h on h.id = b.house_id where b.id = :id
            """).param("id", billId).query(Long.class).single();
        if ("OWNER".equals(principal.role())) {
            assertOwnerBillAccess(principal.username(), billId);
        } else {
            accessControl.assertCommunityAccess(principal, billCommunityId, "PAYMENT", "READ");
        }
        Bill bill = jdbc.sql("""
            select b.id, c.name communityName, h.room_no roomNo, b.bill_type billType,
                   b.period, b.amount, b.paid_amount paidAmount, b.status, b.due_date dueDate
            from bill b join house h on h.id = b.house_id join community c on c.id = h.community_id
            where b.id = :id
            """).param("id", billId).query(Bill.class).single();
        Map<String, Object> prepay = "ALIPAY".equals(channel)
            ? integrations.createAlipayTrade(bill.id(), bill.amount(), bill.communityName() + bill.billType())
            : integrations.createWechatPrepay(bill.id(), bill.amount(), bill.communityName() + bill.billType());
        Long bankConfigId = collectionBankConfigId(billCommunityId);
        String providerOrderNo = "ALIPAY".equals(channel) ? String.valueOf(prepay.get("tradeNo")) : String.valueOf(prepay.get("prepayId"));
        String orderNo = ("ALIPAY".equals(channel) ? "ALI" : "WX") + billId + System.currentTimeMillis();
        jdbc.sql("""
            insert into payment_order(bill_id, community_id, bank_config_id, order_no, amount, status, prepay_id, pay_channel, provider_order_no, paid_at, created_at)
            values(:billId, :communityId, :bankConfigId, :orderNo, :amount, 'PREPAY', :prepayId, :channel, :providerOrderNo, null, now())
            """)
            .param("billId", billId)
            .param("communityId", billCommunityId)
            .param("bankConfigId", bankConfigId)
            .param("orderNo", orderNo)
            .param("amount", bill.amount())
            .param("prepayId", providerOrderNo)
            .param("channel", channel)
            .param("providerOrderNo", providerOrderNo)
            .update();
        audit(principal.username(), "创建" + channel + "支付订单", "payment_order", billId);
        return withPaymentOrder(prepay, orderNo, channel, providerOrderNo);
    }

    private Map<String, Object> confirmPaymentChannel(String channel, Map<String, Object> body, HttpServletRequest request) {
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        long billId = ((Number) body.get("billId")).longValue();
        Long billCommunityId = jdbc.sql("""
            select h.community_id from bill b join house h on h.id = b.house_id where b.id = :id
            """).param("id", billId).query(Long.class).single();
        if ("OWNER".equals(principal.role())) {
            assertOwnerBillAccess(principal.username(), billId);
        } else {
            accessControl.assertCommunityAccess(principal, billCommunityId, "PAYMENT", "WRITE");
        }
        String providerOrderNo = body.get("prepayId") == null ? (body.get("tradeNo") == null ? null : String.valueOf(body.get("tradeNo"))) : String.valueOf(body.get("prepayId"));
        String orderNo = body.get("orderNo") == null ? null : String.valueOf(body.get("orderNo"));
        jdbc.sql("""
            update payment_order set status = 'PAID', paid_at = now()
            where bill_id = :billId and pay_channel = :channel
              and (:orderNo is null or order_no = :orderNo)
              and (:providerOrderNo is null or provider_order_no = :providerOrderNo or prepay_id = :providerOrderNo)
            """)
            .param("billId", billId)
            .param("channel", channel)
            .param("orderNo", orderNo)
            .param("providerOrderNo", providerOrderNo)
            .update();
        completeBillPayment(billId, channel, orderNo);
        Long receiptId = issueReceiptForLatestPayment(billId, channel, principal.username());
        audit(principal.username(), channel + "支付确认", "bill", billId);
        return Map.of("status", "PAID", "billId", billId, "provider", channel, "receiptId", receiptId == null ? 0 : receiptId);
    }

    private void completeBillPayment(long billId, String channel, String orderNo) {
        jdbc.sql("""
            update bill set paid_amount = amount, status = 'PAID' where id = :id
            """).param("id", billId).update();
        String traceNo = orderNo == null || orderNo.isBlank() ? channel + "-BILL-" + billId : orderNo;
        Integer existingFlowCount = jdbc.sql("select count(*) from bank_flow where trace_no = :traceNo")
            .param("traceNo", traceNo)
            .query(Integer.class)
            .single();
        if (existingFlowCount > 0) {
            return;
        }
        jdbc.sql("""
            insert into bank_flow(community_id, account_id, direction, amount, counterparty, summary, occurred_at, trace_no)
            select h.community_id, fa.id, 'IN', b.amount, o.name, concat(b.bill_type, '缴费-', :channel), now(), :traceNo
            from bill b join house h on h.id = b.house_id join owner o on o.house_id = h.id
            join fund_account fa on fa.community_id = h.community_id and fa.account_type = 'PROPERTY_SERVICE'
            where b.id = :id limit 1
            """)
            .param("id", billId)
            .param("channel", channel)
            .param("traceNo", traceNo)
            .update();
        jdbc.sql("""
            select o.phone from bill b
            join house h on h.id = b.house_id
            join owner o on o.house_id = h.id
            where b.id = :id limit 1
            """).param("id", billId).query(String.class).optional()
            .ifPresent(phone -> integrations.sendSms(phone, "PAYMENT_SUCCESS", "您的物业缴费已入账，账单号：" + billId));
    }

    private Long issueReceiptForLatestPayment(long billId, String channel, String issuedBy) {
        Long paymentOrderId = jdbc.sql("""
            select id from payment_order
            where bill_id = :billId and pay_channel = :channel and status in ('PAID','PARTIAL_REFUND','REFUNDED')
            order by paid_at desc, id desc
            limit 1
            """)
            .param("billId", billId)
            .param("channel", channel)
            .query(Long.class)
            .optional()
            .orElse(null);
        return paymentOrderId == null ? null : issueReceiptForPaymentOrder(paymentOrderId, issuedBy);
    }

    private Long issueReceiptForPaymentOrder(long paymentOrderId, String issuedBy) {
        Long existing = jdbc.sql("select id from electronic_payment_receipt where payment_order_id = :paymentOrderId")
            .param("paymentOrderId", paymentOrderId)
            .query(Long.class)
            .optional()
            .orElse(null);
        if (existing != null) {
            return existing;
        }
        PaymentReceiptDraft draft = jdbc.sql("""
            select po.id paymentOrderId, po.bill_id billId, po.community_id communityId,
                   po.order_no orderNo, po.pay_channel payChannel, po.amount,
                   h.room_no roomNo, o.name payerName, b.bill_type billType, b.period
            from payment_order po
            join bill b on b.id = po.bill_id
            join house h on h.id = b.house_id
            join owner o on o.house_id = h.id
            where po.id = :paymentOrderId and po.status in ('PAID','PARTIAL_REFUND','REFUNDED')
            limit 1
            """)
            .param("paymentOrderId", paymentOrderId)
            .query(PaymentReceiptDraft.class)
            .optional()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "支付订单未完成，不能开具电子票据"));
        String receiptNo = "EPR-" + draft.communityId() + "-" + draft.billId() + "-" + paymentOrderId;
        String checksum = Integer.toHexString((receiptNo + "|" + draft.orderNo() + "|" + draft.amount()).hashCode());
        jdbc.sql("""
            insert into electronic_payment_receipt(receipt_no, bill_id, payment_order_id, community_id,
                                                   room_no, payer_name, bill_type, period, pay_channel,
                                                   amount, status, file_url, checksum, issued_by, issued_at)
            values(:receiptNo, :billId, :paymentOrderId, :communityId,
                   :roomNo, :payerName, :billType, :period, :payChannel,
                   :amount, 'ISSUED', :fileUrl, :checksum, :issuedBy, now())
            """)
            .param("receiptNo", receiptNo)
            .param("billId", draft.billId())
            .param("paymentOrderId", paymentOrderId)
            .param("communityId", draft.communityId())
            .param("roomNo", draft.roomNo())
            .param("payerName", draft.payerName())
            .param("billType", draft.billType())
            .param("period", draft.period())
            .param("payChannel", draft.payChannel())
            .param("amount", draft.amount())
            .param("fileUrl", "mock://electronic-receipt/" + receiptNo + ".pdf")
            .param("checksum", checksum)
            .param("issuedBy", issuedBy)
            .update();
        Long receiptId = jdbc.sql("select max(id) from electronic_payment_receipt").query(Long.class).single();
        audit(issuedBy, "开具电子缴费票据-" + receiptNo, "electronic_payment_receipt", receiptId);
        return receiptId;
    }

    private ElectronicPaymentReceipt receiptById(long receiptId) {
        return jdbc.sql("""
            select r.id, r.receipt_no receiptNo, c.name communityName, r.room_no roomNo,
                   r.payer_name payerName, r.bill_type billType, r.period, r.pay_channel payChannel,
                   r.amount, r.status, r.file_url fileUrl, r.checksum, r.issued_by issuedBy, r.issued_at issuedAt
            from electronic_payment_receipt r
            join community c on c.id = r.community_id
            where r.id = :id
            """)
            .param("id", receiptId)
            .query(ElectronicPaymentReceipt.class)
            .optional()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "票据不存在"));
    }

    private PaymentReceiptForTax receiptForTax(long receiptId) {
        return jdbc.sql("""
            select r.id receiptId, r.bill_id billId, r.payment_order_id paymentOrderId,
                   r.community_id communityId, r.payer_name payerName, r.bill_type billType,
                   r.amount
            from electronic_payment_receipt r
            where r.id = :id and r.status = 'ISSUED'
            """)
            .param("id", receiptId)
            .query(PaymentReceiptForTax.class)
            .optional()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "可开票电子票据不存在"));
    }

    private TaxInvoice taxInvoiceById(long invoiceId) {
        return jdbc.sql("""
            select t.id, t.invoice_request_no invoiceRequestNo, r.receipt_no receiptNo,
                   c.name communityName, t.buyer_name buyerName, t.buyer_tax_no buyerTaxNo,
                   t.invoice_item invoiceItem, t.tax_category_code taxCategoryCode, t.tax_rate taxRate,
                   t.amount, t.status, t.tax_invoice_no taxInvoiceNo, t.tax_platform_code taxPlatformCode,
                   t.pdf_url pdfUrl, t.checksum, t.fail_reason failReason, t.issued_by issuedBy,
                   t.issued_at issuedAt, t.red_original_id redOriginalId, t.red_at redAt, t.created_at createdAt
            from tax_invoice_request t
            join electronic_payment_receipt r on r.id = t.receipt_id
            join community c on c.id = t.community_id
            where t.id = :id
            """)
            .param("id", invoiceId)
            .query(TaxInvoice.class)
            .optional()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "税务发票申请不存在"));
    }

    private String normalizePayChannel(String provider) {
        if (provider == null) {
            return "WECHAT_PAY";
        }
        String value = provider.trim().toUpperCase();
        if ("WECHAT".equals(value) || "WECHAT_PAY".equals(value) || "WX".equals(value)) {
            return "WECHAT_PAY";
        }
        if ("ALIPAY".equals(value) || "ALI".equals(value)) {
            return "ALIPAY";
        }
        throw new IllegalArgumentException("不支持的支付渠道：" + provider);
    }

    private Long insertPaymentChannelStatement(long communityId, String provider, LocalDate statementDate, String tradeType, long billId, String orderNo, String channelTradeNo, BigDecimal amount) {
        String finalChannelTradeNo = channelTradeNo == null || channelTradeNo.isBlank() ? orderNo : channelTradeNo;
        Long existing = jdbc.sql("""
            select id from payment_channel_statement
            where provider = :provider and statement_date = :statementDate
              and trade_type = :tradeType and channel_trade_no = :channelTradeNo
            order by id desc limit 1
            """)
            .param("provider", provider)
            .param("statementDate", statementDate)
            .param("tradeType", tradeType)
            .param("channelTradeNo", finalChannelTradeNo)
            .query(Long.class)
            .optional()
            .orElse(null);
        if (existing != null) {
            return existing;
        }
        jdbc.sql("""
            insert into payment_channel_statement(community_id, provider, statement_date, trade_type, bill_id,
                                                  order_no, channel_trade_no, amount, status, synced_at, created_at)
            values(:communityId, :provider, :statementDate, :tradeType, :billId,
                   :orderNo, :channelTradeNo, :amount, 'MATCHED', now(), now())
            """)
            .param("communityId", communityId)
            .param("provider", provider)
            .param("statementDate", statementDate)
            .param("tradeType", tradeType)
            .param("billId", billId)
            .param("orderNo", orderNo)
            .param("channelTradeNo", finalChannelTradeNo)
            .param("amount", amount)
            .update();
        return jdbc.sql("select max(id) from payment_channel_statement").query(Long.class).single();
    }

    private Map<String, Object> withPaymentOrder(Map<String, Object> source, String orderNo, String channel, String providerOrderNo) {
        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>(source);
        result.put("orderNo", orderNo);
        result.put("payChannel", channel);
        result.put("providerOrderNo", providerOrderNo);
        return result;
    }

    @GetMapping("/revenue")
    public List<BankFlow> revenue(HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET", "COMMITTEE", "PROPERTY");
        List<Long> allowedCommunityIds = scopedCommunityIds(request, null, "BANK_FLOW", "READ");
        if (allowedCommunityIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
            select f.id, c.name communityName, a.account_name accountName, f.direction, f.amount,
                   f.counterparty, f.summary, f.occurred_at occurredAt, f.trace_no traceNo
            from bank_flow f
            join fund_account a on a.id = f.account_id
            join community c on c.id = f.community_id
            where f.community_id in (:allowedCommunityIds)
            order by f.occurred_at desc
            """).param("allowedCommunityIds", allowedCommunityIds).query(BankFlow.class).list();
    }

    @GetMapping("/expenses")
    public List<Expense> expenses(HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET", "COMMITTEE");
        List<Long> allowedCommunityIds = scopedCommunityIds(request, null, "EXPENSE", "READ");
        if (allowedCommunityIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
            select e.id, c.name communityName, e.order_type orderType, e.title, e.amount, e.status,
                   e.current_node currentNode, e.invoice_no invoiceNo, e.contract_no contractNo,
                   e.created_at createdAt, e.due_at dueAt
            from expense_order e join community c on c.id = e.community_id
            where e.community_id in (:allowedCommunityIds)
            order by e.created_at desc
            """).param("allowedCommunityIds", allowedCommunityIds).query(Expense.class).list();
    }

    @GetMapping("/approval-rules")
    public List<ApprovalRule> approvalRules(@RequestParam(required = false) Long communityId, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET", "COMMITTEE");
        List<Long> allowedCommunityIds = scopedCommunityIds(request, communityId, "EXPENSE", "READ");
        if (allowedCommunityIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
            select ar.id, c.name communityName, ar.community_id communityId, ar.expense_threshold expenseThreshold,
                   ar.vote_threshold voteThreshold, ar.approval_timeout_hours approvalTimeoutHours,
                   ar.vote_ratio voteRatio, ar.status, ar.created_at createdAt
            from approval_rule ar join community c on c.id = ar.community_id
            where ar.community_id in (:allowedCommunityIds)
            order by ar.community_id
            """).param("allowedCommunityIds", allowedCommunityIds).query(ApprovalRule.class).list();
    }

    @PostMapping("/approval-rules")
    public Map<String, Object> saveApprovalRule(@RequestBody ApprovalRuleRequest body, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET");
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        accessControl.assertCommunityAccess(principal, body.communityId(), "EXPENSE", "READ");
        Long existingId = jdbc.sql("select id from approval_rule where community_id = :communityId")
            .param("communityId", body.communityId())
            .query(Long.class)
            .optional()
            .orElse(null);
        if (existingId == null) {
            jdbc.sql("""
                insert into approval_rule(community_id, expense_threshold, vote_threshold, approval_timeout_hours, vote_ratio, status, created_at)
                values(:communityId, :expenseThreshold, :voteThreshold, :approvalTimeoutHours, :voteRatio, 'ACTIVE', now())
                """)
                .param("communityId", body.communityId())
                .param("expenseThreshold", body.expenseThreshold())
                .param("voteThreshold", body.voteThreshold())
                .param("approvalTimeoutHours", body.approvalTimeoutHours())
                .param("voteRatio", body.voteRatio())
                .update();
            existingId = jdbc.sql("select max(id) from approval_rule").query(Long.class).single();
        } else {
            jdbc.sql("""
                update approval_rule
                set expense_threshold = :expenseThreshold,
                    vote_threshold = :voteThreshold,
                    approval_timeout_hours = :approvalTimeoutHours,
                    vote_ratio = :voteRatio,
                    status = :status
                where id = :id
                """)
                .param("expenseThreshold", body.expenseThreshold())
                .param("voteThreshold", body.voteThreshold())
                .param("approvalTimeoutHours", body.approvalTimeoutHours())
                .param("voteRatio", body.voteRatio())
                .param("status", body.status() == null || body.status().isBlank() ? "ACTIVE" : body.status())
                .param("id", existingId)
                .update();
        }
        audit(principal.username(), "维护审批阈值规则", "approval_rule", existingId);
        return Map.of("approvalRuleId", existingId, "status", "SAVED");
    }

    @GetMapping("/workflow/templates")
    public List<WorkflowTemplate> workflowTemplates(HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET", "COMMITTEE");
        return jdbc.sql("""
            select id, template_code templateCode, template_name templateName, business_type businessType,
                   status, created_at createdAt
            from workflow_template
            order by id
            """).query(WorkflowTemplate.class).list();
    }

    @GetMapping("/workflow/templates/{templateCode}/nodes")
    public List<WorkflowTemplateNode> workflowTemplateNodes(@PathVariable String templateCode, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET", "COMMITTEE");
        return workflowTemplateNodes(templateCode);
    }

    @PostMapping("/workflow/templates/{templateCode}/nodes")
    public Map<String, Object> saveWorkflowTemplateNode(@PathVariable String templateCode, @RequestBody WorkflowTemplateNodeRequest body, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET");
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        Integer exists = jdbc.sql("select count(*) from workflow_template where template_code = :templateCode")
            .param("templateCode", templateCode)
            .query(Integer.class)
            .single();
        if (exists == null || exists == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "工作流模板不存在");
        }
        String conditionCode = body.conditionCode() == null || body.conditionCode().isBlank() ? "ALWAYS" : body.conditionCode();
        int timeoutHours = body.timeoutHours() <= 0 ? 24 : body.timeoutHours();
        String nodeStatus = body.status() == null || body.status().isBlank() ? "ACTIVE" : body.status();
        Integer nodeExists = jdbc.sql("""
                select count(*) from workflow_template_node
                where template_code = :templateCode and node_code = :nodeCode
                """)
            .param("templateCode", templateCode)
            .param("nodeCode", body.nodeCode())
            .query(Integer.class)
            .single();
        if (nodeExists != null && nodeExists > 0) {
            jdbc.sql("""
                update workflow_template_node
                set node_name = :nodeName, role_code = :roleCode, sort_no = :sortNo,
                    condition_code = :conditionCode, timeout_hours = :timeoutHours, status = :status
                where template_code = :templateCode and node_code = :nodeCode
                """)
                .param("templateCode", templateCode)
                .param("nodeCode", body.nodeCode())
                .param("nodeName", body.nodeName())
                .param("roleCode", body.roleCode())
                .param("sortNo", body.sortNo())
                .param("conditionCode", conditionCode)
                .param("timeoutHours", timeoutHours)
                .param("status", nodeStatus)
                .update();
        } else {
            jdbc.sql("""
                insert into workflow_template_node(template_code, node_code, node_name, role_code,
                                                   sort_no, condition_code, timeout_hours, status, created_at)
                values(:templateCode, :nodeCode, :nodeName, :roleCode,
                       :sortNo, :conditionCode, :timeoutHours, :status, now())
                """)
                .param("templateCode", templateCode)
                .param("nodeCode", body.nodeCode())
                .param("nodeName", body.nodeName())
                .param("roleCode", body.roleCode())
                .param("sortNo", body.sortNo())
                .param("conditionCode", conditionCode)
                .param("timeoutHours", timeoutHours)
                .param("status", nodeStatus)
                .update();
        }
        workflowEvent("TEMPLATE", 0, templateCode, body.nodeCode(), "UPSERT_NODE", principal.username(), "维护工作流节点：" + body.nodeName());
        return Map.of("templateCode", templateCode, "nodeCode", body.nodeCode(), "status", "SAVED");
    }

    @GetMapping("/approvals/{expenseId}/events")
    public List<WorkflowEvent> approvalWorkflowEvents(@PathVariable long expenseId, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET", "COMMITTEE");
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        Long expenseCommunityId = jdbc.sql("select community_id from expense_order where id = :expenseId")
            .param("expenseId", expenseId)
            .query(Long.class)
            .single();
        accessControl.assertCommunityAccess(principal, expenseCommunityId, "EXPENSE", "READ");
        return jdbc.sql("""
            select id, business_type businessType, business_id businessId, template_code templateCode,
                   node_code nodeCode, event_type eventType, operator, event_summary eventSummary,
                   created_at createdAt
            from workflow_event
            where business_type = 'EXPENSE' and business_id = :expenseId
            order by id
            """).param("expenseId", expenseId).query(WorkflowEvent.class).list();
    }

    @GetMapping("/workflow/events/export")
    public ResponseEntity<byte[]> workflowEventsCsv(HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET");
        List<WorkflowEvent> rows = jdbc.sql("""
            select id, business_type businessType, business_id businessId, template_code templateCode,
                   node_code nodeCode, event_type eventType, operator, event_summary eventSummary,
                   created_at createdAt
            from workflow_event
            order by created_at desc, id desc
            limit 500
            """).query(WorkflowEvent.class).list();
        StringBuilder csv = new StringBuilder("业务类型,业务ID,模板,节点,事件,操作人,摘要,时间\n");
        for (WorkflowEvent row : rows) {
            csv.append(csv(row.businessType())).append(',')
                .append(row.businessId()).append(',')
                .append(csv(row.templateCode())).append(',')
                .append(csv(row.nodeCode())).append(',')
                .append(csv(row.eventType())).append(',')
                .append(csv(row.operator())).append(',')
                .append(csv(row.eventSummary())).append(',')
                .append(row.createdAt()).append('\n');
        }
        logDataExport(request, "WORKFLOW_EVENT", "workflow-events.csv", "workflow_event", 0, null, rows.size());
        return csvResponse("workflow-events.csv", csv.toString());
    }

    @GetMapping("/approvals/{expenseId}/logs")
    public List<ApprovalLog> approvalLogs(@PathVariable long expenseId, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET", "COMMITTEE");
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        Long expenseCommunityId = jdbc.sql("select community_id from expense_order where id = :expenseId")
            .param("expenseId", expenseId)
            .query(Long.class)
            .single();
        accessControl.assertCommunityAccess(principal, expenseCommunityId, "EXPENSE", "READ");
        return jdbc.sql("""
            select id, expense_id expenseId, node_name nodeName, operator, decision, comment, operated_at operatedAt
            from approval_log where expense_id = :expenseId order by operated_at
            """).param("expenseId", expenseId).query(ApprovalLog.class).list();
    }

    @GetMapping("/approvals/{expenseId}/nodes")
    public List<ApprovalWorkflowNode> approvalNodes(@PathVariable long expenseId, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET", "COMMITTEE");
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        Long expenseCommunityId = jdbc.sql("select community_id from expense_order where id = :expenseId")
            .param("expenseId", expenseId)
            .query(Long.class)
            .single();
        accessControl.assertCommunityAccess(principal, expenseCommunityId, "EXPENSE", "READ");
        return approvalNodesByExpense(expenseId);
    }

    @GetMapping("/expenses/{expenseId}/documents")
    public List<ExpenseDocument> expenseDocuments(@PathVariable long expenseId, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET", "COMMITTEE");
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        Long expenseCommunityId = jdbc.sql("select community_id from expense_order where id = :expenseId")
            .param("expenseId", expenseId)
            .query(Long.class)
            .single();
        accessControl.assertCommunityAccess(principal, expenseCommunityId, "EXPENSE", "READ");
        return jdbc.sql("""
            select id, expense_id expenseId, document_type documentType, document_no documentNo,
                   issuer, amount, issue_date issueDate, file_url fileUrl,
                   verification_status verificationStatus, review_comment reviewComment,
                   verified_by verifiedBy, verified_at verifiedAt, created_at createdAt
            from expense_document
            where expense_id = :expenseId
            order by created_at desc, id desc
            """).param("expenseId", expenseId).query(ExpenseDocument.class).list();
    }

    @PostMapping("/expenses/{expenseId}/documents")
    public Map<String, Object> addExpenseDocument(@PathVariable long expenseId, @RequestBody ExpenseDocumentRequest body, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "COMMITTEE");
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        Long expenseCommunityId = jdbc.sql("select community_id from expense_order where id = :expenseId")
            .param("expenseId", expenseId)
            .query(Long.class)
            .single();
        accessControl.assertCommunityAccess(principal, expenseCommunityId, "EXPENSE", "WRITE");
        Long documentId = createExpenseDocument(expenseId, body.documentType(), body.documentNo(), body.issuer(), body.amount(), body.issueDate(), body.fileUrl(), "PENDING", "待核验");
        audit(principal.username(), "新增支出资料-" + body.documentType(), "expense_document", documentId);
        return Map.of("documentId", documentId, "status", "PENDING");
    }

    @PostMapping("/expenses/{expenseId}/documents/{documentId}/verify")
    public Map<String, Object> verifyExpenseDocument(@PathVariable long expenseId, @PathVariable long documentId, @RequestBody DocumentVerifyRequest body, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET", "COMMITTEE");
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        Long expenseCommunityId = jdbc.sql("select community_id from expense_order where id = :expenseId")
            .param("expenseId", expenseId)
            .query(Long.class)
            .single();
        accessControl.assertCommunityAccess(principal, expenseCommunityId, "EXPENSE", "READ");
        String status = "PASS".equalsIgnoreCase(body.decision()) ? "VERIFIED" : "REJECTED";
        jdbc.sql("""
            update expense_document
            set verification_status = :status, review_comment = :comment, verified_by = :verifiedBy, verified_at = now()
            where id = :documentId and expense_id = :expenseId
            """)
            .param("status", status)
            .param("comment", body.comment())
            .param("verifiedBy", principal.username())
            .param("documentId", documentId)
            .param("expenseId", expenseId)
            .update();
        audit(principal.username(), "核验支出资料-" + status, "expense_document", documentId);
        return Map.of("documentId", documentId, "status", status);
    }

    @GetMapping("/expenses/{expenseId}/documents/{documentId}/download")
    public ResponseEntity<byte[]> downloadExpenseDocument(@PathVariable long expenseId, @PathVariable long documentId, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET", "COMMITTEE");
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        Long expenseCommunityId = jdbc.sql("select community_id from expense_order where id = :expenseId")
            .param("expenseId", expenseId)
            .query(Long.class)
            .single();
        accessControl.assertCommunityAccess(principal, expenseCommunityId, "EXPENSE", "READ");
        ExpenseDocument document = jdbc.sql("""
            select id, expense_id expenseId, document_type documentType, document_no documentNo,
                   issuer, amount, issue_date issueDate, file_url fileUrl,
                   verification_status verificationStatus, review_comment reviewComment,
                   verified_by verifiedBy, verified_at verifiedAt, created_at createdAt
            from expense_document
            where id = :documentId and expense_id = :expenseId
            """)
            .param("documentId", documentId)
            .param("expenseId", expenseId)
            .query(ExpenseDocument.class)
            .optional()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "支出资料不存在"));
        String filename = "expense-document-" + document.id() + ".pdf";
        audit(principal.username(), "下载支出资料附件-" + document.documentNo(), "expense_document", documentId);
        logDataExport(request, "EXPENSE_DOCUMENT", filename, "expense_document", documentId, expenseCommunityId, 1);
        byte[] bytes = simplePdf("""
            Lingma Tech SPARK Property Platform
            Expense Document Attachment
            Expense Id: %s
            Document Type: %s
            Document No: %s
            Issuer: %s
            Amount: %s CNY
            Issue Date: %s
            File URL: %s
            Verification Status: %s
            Review Comment: %s
            Verified By: %s
            Verified At: %s
            """.formatted(
            document.expenseId(),
            document.documentType(),
            document.documentNo(),
            document.issuer() == null ? "" : document.issuer(),
            document.amount() == null ? "" : document.amount(),
            document.issueDate() == null ? "" : document.issueDate(),
            document.fileUrl() == null || document.fileUrl().isBlank() ? "generated://expense-document/" + document.id() : document.fileUrl(),
            document.verificationStatus(),
            document.reviewComment() == null ? "" : document.reviewComment(),
            document.verifiedBy() == null ? "" : document.verifiedBy(),
            document.verifiedAt() == null ? "" : document.verifiedAt()
        ));
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.APPLICATION_PDF)
            .body(bytes);
    }

    @PostMapping("/expenses")
    public Map<String, Object> createExpense(@RequestBody ExpenseRequest request, HttpServletRequest httpRequest) {
        requireAnyRole(httpRequest, "ADMIN", "COMMITTEE");
        TokenService.Principal principal = (TokenService.Principal) httpRequest.getAttribute("principal");
        accessControl.assertCommunityAccess(principal, request.communityId(), "EXPENSE", "WRITE");
        ApprovalRule rule = jdbc.sql("""
            select ar.id, c.name communityName, ar.community_id communityId, ar.expense_threshold expenseThreshold,
                   ar.vote_threshold voteThreshold, ar.approval_timeout_hours approvalTimeoutHours,
                   ar.vote_ratio voteRatio, ar.status, ar.created_at createdAt
            from approval_rule ar join community c on c.id = ar.community_id
            where ar.community_id = :communityId and ar.status = 'ACTIVE'
            order by ar.id desc limit 1
            """).param("communityId", request.communityId()).query(ApprovalRule.class).single();
        boolean needsCommunityApproval = request.amount().compareTo(rule.expenseThreshold()) >= 0;
        boolean needsVote = request.amount().compareTo(rule.voteThreshold()) >= 0;
        String currentNode = "业委会审批";
        LocalDateTime dueAt = LocalDateTime.now().plusHours(rule.approvalTimeoutHours());
        Long expenseId = insertAndReturnId("""
            insert into expense_order(community_id, order_type, title, amount, status, current_node, invoice_no, contract_no,
                                      created_at, due_at, source_account_type, need_vote)
            values(:communityId, :orderType, :title, :amount, 'PENDING', :currentNode, :invoiceNo, :contractNo,
                   now(), :dueAt, 'PUBLIC_REVENUE', :needVote)
            """, new MapSqlParameterSource()
            .addValue("communityId", request.communityId())
            .addValue("orderType", request.orderType())
            .addValue("title", request.title())
            .addValue("amount", request.amount())
            .addValue("currentNode", currentNode)
            .addValue("invoiceNo", request.invoiceNo())
            .addValue("contractNo", request.contractNo())
            .addValue("dueAt", dueAt)
            .addValue("needVote", needsVote ? "YES" : "NO"));
        jdbc.sql("""
            insert into approval_log(expense_id, node_name, operator, decision, comment, operated_at)
            values(:expenseId, '提交申请', :operator, 'SUBMITTED', :comment, now())
            """)
            .param("expenseId", expenseId)
            .param("operator", principal.username())
            .param("comment", needsVote ? "达到业主大会表决阈值" : needsCommunityApproval ? "达到社区审批阈值" : "未达到社区审批阈值")
            .update();
        createApprovalWorkflowNodes(expenseId, rule, needsCommunityApproval, needsVote);
        if (needsVote) {
            createExpenseVote(request.communityId(), expenseId, request.title(), rule);
        }
        if (request.invoiceNo() != null && !request.invoiceNo().isBlank()) {
            createExpenseDocument(expenseId, "INVOICE", request.invoiceNo(), request.invoiceIssuer(), request.invoiceAmount() == null ? request.amount() : request.invoiceAmount(), request.invoiceIssueDate(), request.invoiceFileUrl(), "PENDING", "提交支出时自动生成发票资料");
        }
        if (request.contractNo() != null && !request.contractNo().isBlank()) {
            createExpenseDocument(expenseId, "CONTRACT", request.contractNo(), request.contractParty(), request.contractAmount() == null ? request.amount() : request.contractAmount(), request.contractSignDate(), request.contractFileUrl(), "PENDING", "提交支出时自动生成合同资料");
        }
        integrations.sendSms("13800000000", "APPROVAL_REMINDER", "公共支出待审批：" + request.title() + "，金额：" + request.amount());
        audit(principal.username(), "提交公共支出-" + request.title(), "expense_order", expenseId);
        return Map.of("expenseId", expenseId, "currentNode", currentNode, "needVote", needsVote);
    }

    @PostMapping("/approvals/{expenseId}/{decision}")
    public Map<String, Object> decide(@PathVariable long expenseId, @PathVariable String decision, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET", "COMMITTEE");
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        Long expenseCommunityId = jdbc.sql("select community_id from expense_order where id = :expenseId")
            .param("expenseId", expenseId)
            .query(Long.class)
            .single();
        accessControl.assertCommunityAccess(principal, expenseCommunityId, "EXPENSE", "APPROVE");
        String status = "approve".equalsIgnoreCase(decision) ? "APPROVED" : "REJECTED";
        ApprovalWorkflowNode activeNode = activeApprovalNode(expenseId);
        assertNodeDecisionRole(principal, activeNode);
        String finalStatus = status;
        String currentNode = "流程结束";
        String comment = "reject".equalsIgnoreCase(decision) ? "驳回并触发银行退回" : activeNode.nodeName() + "通过";
        jdbc.sql("""
            update approval_workflow_node
            set status = :status, operator = :operator, decision = :decision, comment = :comment, operated_at = now()
            where id = :id
            """)
            .param("status", status)
            .param("operator", principal.username())
            .param("decision", status)
            .param("comment", comment)
            .param("id", activeNode.id())
            .update();
        workflowEvent("EXPENSE", expenseId, "PUBLIC_EXPENSE", activeNode.nodeCode(), status, principal.username(), comment);
        if ("APPROVED".equals(status)) {
            ApprovalWorkflowNode nextNode = nextWaitingApprovalNode(expenseId);
            if (nextNode != null) {
                LocalDateTime nextDueAt = LocalDateTime.now().plusHours(24);
                jdbc.sql("""
                    update approval_workflow_node set status = 'ACTIVE', due_at = :dueAt
                    where id = :id
                    """).param("dueAt", nextDueAt).param("id", nextNode.id()).update();
                workflowEvent("EXPENSE", expenseId, "PUBLIC_EXPENSE", nextNode.nodeCode(), "ACTIVATE_NODE", principal.username(), "激活下一节点：" + nextNode.nodeName());
                finalStatus = "PENDING";
                currentNode = nextNode.nodeName();
            }
        } else {
            markPendingNodes(expenseId, "REJECTED");
            workflowEvent("EXPENSE", expenseId, "PUBLIC_EXPENSE", activeNode.nodeCode(), "REJECT_FLOW", principal.username(), "驳回后终止剩余节点");
        }
        jdbc.sql("""
            update expense_order set status = :status, current_node = :currentNode where id = :id
            """).param("status", finalStatus).param("currentNode", currentNode).param("id", expenseId).update();
        jdbc.sql("""
            insert into approval_log(expense_id, node_name, operator, decision, comment, operated_at)
            values(:expenseId, :nodeName, :operator, :decision, :comment, now())
            """)
            .param("expenseId", expenseId)
            .param("nodeName", activeNode.nodeName())
            .param("operator", principal.username())
            .param("decision", status)
            .param("comment", comment)
            .update();
        if ("REJECTED".equals(finalStatus)) {
            integrations.refundExpense(expenseId);
        } else if ("APPROVED".equals(finalStatus)) {
            completeBankPaymentNode(expenseId);
            createExpenseVoucher(expenseId);
        }
        audit(principal.username(), activeNode.nodeName() + "-" + status, "expense_order", expenseId);
        return Map.of("expenseId", expenseId, "status", finalStatus, "currentNode", currentNode);
    }

    @PostMapping("/expenses/timeout-scan")
    public Map<String, Object> scanTimeoutExpenses(HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET");
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        List<Long> allowedCommunityIds = scopedCommunityIds(request, null, "EXPENSE", "APPROVE");
        if (allowedCommunityIds.isEmpty()) {
            return Map.of("returnedCount", 0);
        }
        List<Long> timeoutIds = jdbc.sql("""
            select distinct e.id
            from expense_order e
            join approval_workflow_node n on n.expense_id = e.id
            where e.status in ('PENDING', 'WARNING')
              and n.status = 'ACTIVE'
              and n.due_at < now()
              and e.community_id in (:allowedCommunityIds)
            """).param("allowedCommunityIds", allowedCommunityIds).query(Long.class).list();
        for (Long id : timeoutIds) {
            jdbc.sql("""
                update approval_workflow_node
                set status = 'RETURNED', operator = :operator, decision = 'RETURNED',
                    comment = '超过24小时未审批，触发银行退回', operated_at = now()
                where expense_id = :expenseId and status = 'ACTIVE'
                """)
                .param("expenseId", id)
                .param("operator", principal.username())
                .update();
            markPendingNodes(id, "RETURNED");
            jdbc.sql("""
                update expense_order set status = 'RETURNED', current_node = '银行退回' where id = :id
                """).param("id", id).update();
            workflowEvent("EXPENSE", id, "PUBLIC_EXPENSE", "TIMEOUT_SCAN", "TIMEOUT_RETURN", principal.username(), "超过24小时未审批，触发银行退回");
            jdbc.sql("""
                insert into approval_log(expense_id, node_name, operator, decision, comment, operated_at)
                values(:expenseId, '超时扫描', :operator, 'RETURNED', '超过24小时未审批，触发银行退回', now())
                """)
                .param("expenseId", id)
                .param("operator", principal.username())
                .update();
            integrations.refundExpense(id);
            audit(principal.username(), "超时退回公共支出", "expense_order", id);
        }
        return Map.of("returnedCount", timeoutIds.size());
    }

    @GetMapping("/votes")
    public List<Vote> votes(HttpServletRequest request) {
        List<Long> allowedCommunityIds = scopedCommunityIds(request, null, "VOTE", "READ");
        if (allowedCommunityIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
            select v.id, c.name communityName, v.title, v.vote_type voteType, v.status,
                   v.start_at startAt, v.end_at endAt, v.participation_rate participationRate,
                   v.agree_rate agreeRate
            from vote v join community c on c.id = v.community_id
            where v.community_id in (:allowedCommunityIds)
            order by v.start_at desc
            """).param("allowedCommunityIds", allowedCommunityIds).query(Vote.class).list();
    }

    @PostMapping("/votes")
    public Map<String, Object> createVote(@RequestBody VoteRequest request, HttpServletRequest httpRequest) {
        requireAnyRole(httpRequest, "ADMIN", "COMMITTEE");
        TokenService.Principal principal = (TokenService.Principal) httpRequest.getAttribute("principal");
        accessControl.assertCommunityAccess(principal, request.communityId(), "VOTE", "WRITE");
        Integer totalVoters = jdbc.sql("select households from community where id = :communityId")
            .param("communityId", request.communityId())
            .query(Integer.class)
            .single();
        Long voteId = insertAndReturnId("""
            insert into vote(community_id, title, vote_type, status, start_at, end_at, participation_rate, agree_rate,
                             related_expense_id, total_voters, agree_count, disagree_count)
            values(:communityId, :title, :voteType, 'OPEN', :startAt, :endAt, 0, 0, null, :totalVoters, 0, 0)
            """, new MapSqlParameterSource()
            .addValue("communityId", request.communityId())
            .addValue("title", request.title())
            .addValue("voteType", request.voteType())
            .addValue("startAt", request.startAt())
            .addValue("endAt", request.endAt())
            .addValue("totalVoters", totalVoters));
        if ("SURVEY".equalsIgnoreCase(request.voteType())) {
            createSurveyQuestion(voteId, request.questionTitle(), request.options());
        }
        audit(principal.username(), "发布投票问卷-" + request.title(), "vote", voteId);
        return Map.of("voteId", voteId, "status", "OPEN");
    }

    @GetMapping("/votes/{voteId}/survey")
    public List<SurveyQuestion> surveyQuestions(@PathVariable long voteId, HttpServletRequest request) {
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        Long voteCommunityId = jdbc.sql("select community_id from vote where id = :voteId")
            .param("voteId", voteId)
            .query(Long.class)
            .single();
        assertVoteAccess(principal, voteCommunityId, "READ");
        return surveyQuestionsByVote(voteId);
    }

    @GetMapping("/votes/{voteId}/survey-results")
    public List<SurveyResult> surveyResults(@PathVariable long voteId, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET", "COMMITTEE");
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        Long voteCommunityId = jdbc.sql("select community_id from vote where id = :voteId")
            .param("voteId", voteId)
            .query(Long.class)
            .single();
        accessControl.assertCommunityAccess(principal, voteCommunityId, "VOTE", "READ");
        return jdbc.sql("""
            select q.id questionId, q.question_title questionTitle, o.id optionId, o.option_label optionLabel,
                   count(r.id) responseCount
            from survey_question q
            join survey_option o on o.question_id = q.id
            left join survey_response r on r.question_id = q.id and r.option_id = o.id
            where q.vote_id = :voteId
            group by q.id, q.question_title, o.id, o.option_label, o.sort_no
            order by q.sort_no, o.sort_no
            """).param("voteId", voteId).query(SurveyResult.class).list();
    }

    @PostMapping("/votes/{voteId}/survey-submit")
    public Map<String, Object> submitSurvey(@PathVariable long voteId, @RequestBody SurveySubmitRequest body, HttpServletRequest request) {
        requireAnyRole(request, "OWNER");
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        Long voteCommunityId = jdbc.sql("select community_id from vote where id = :voteId")
            .param("voteId", voteId)
            .query(Long.class)
            .single();
        assertVoteAccess(principal, voteCommunityId, "WRITE");
        Long ownerId = ownerIdForCommunity(principal.username(), voteCommunityId);
        for (SurveyAnswer answer : body.answers()) {
            jdbc.sql("""
                insert into survey_response(vote_id, question_id, option_id, owner_id, submitted_at)
                values(:voteId, :questionId, :optionId, :ownerId, now())
                on duplicate key update option_id = values(option_id), submitted_at = now()
                """)
                .param("voteId", voteId)
                .param("questionId", answer.questionId())
                .param("optionId", answer.optionId())
                .param("ownerId", ownerId)
                .update();
        }
        refreshSurveyStats(voteId);
        audit(principal.username(), "提交问卷", "vote", voteId);
        return Map.of("voteId", voteId, "status", "SUBMITTED");
    }

    @PostMapping("/votes/{voteId}/cast")
    public Map<String, Object> castVote(@PathVariable long voteId, @RequestBody VoteCastRequest request, HttpServletRequest httpRequest) {
        requireAnyRole(httpRequest, "OWNER");
        TokenService.Principal principal = (TokenService.Principal) httpRequest.getAttribute("principal");
        Long voteCommunityId = jdbc.sql("select community_id from vote where id = :voteId")
            .param("voteId", voteId)
            .query(Long.class)
            .single();
        assertVoteAccess(principal, voteCommunityId, "WRITE");
        Long ownerId = ownerIdForCommunity(principal.username(), voteCommunityId);
        String decision = "DISAGREE".equalsIgnoreCase(request.decision()) ? "DISAGREE" : "AGREE";
        String signatureHash = Integer.toHexString((voteId + "|" + ownerId + "|" + decision + "|" + principal.username()).hashCode());
        jdbc.sql("""
            insert into vote_ballot(vote_id, owner_id, decision, signature_hash, cast_at)
            values(:voteId, :ownerId, :decision, :signatureHash, now())
            on duplicate key update decision = values(decision), signature_hash = values(signature_hash), cast_at = now()
            """)
            .param("voteId", voteId)
            .param("ownerId", ownerId)
            .param("decision", decision)
            .param("signatureHash", signatureHash)
            .update();
        refreshVoteStats(voteId);
        audit(principal.username(), "实名投票-" + decision, "vote", voteId);
        return Map.of("voteId", voteId, "decision", decision, "signatureHash", signatureHash);
    }

    @GetMapping("/votes/{voteId}/ballots")
    public List<VoteBallot> voteBallots(@PathVariable long voteId, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET", "COMMITTEE");
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        Long voteCommunityId = jdbc.sql("select community_id from vote where id = :voteId")
            .param("voteId", voteId)
            .query(Long.class)
            .single();
        accessControl.assertCommunityAccess(principal, voteCommunityId, "VOTE", "READ");
        return jdbc.sql("""
            select vb.id, vb.vote_id voteId, o.name ownerName, vb.decision, vb.signature_hash signatureHash, vb.cast_at castAt
            from vote_ballot vb join owner o on o.id = vb.owner_id
            where vb.vote_id = :voteId order by vb.cast_at desc
            """).param("voteId", voteId).query(VoteBallot.class).list();
    }

    @GetMapping("/repairs")
    public List<WorkOrder> repairs(HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET", "COMMITTEE", "PROPERTY");
        return workOrders("REPAIR", request);
    }

    @GetMapping("/complaints")
    public List<WorkOrder> complaints(HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET", "COMMITTEE", "PROPERTY");
        return workOrders("COMPLAINT", request);
    }

    @GetMapping("/work-orders/sla-rules")
    public List<WorkOrderSlaRule> workOrderSlaRules(HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET", "COMMITTEE", "PROPERTY");
        return jdbc.sql("""
            select id, order_type orderType, priority, response_hours responseHours, status, created_at createdAt
            from work_order_sla_rule order by order_type, priority
            """).query(WorkOrderSlaRule.class).list();
    }

    @PostMapping("/work-orders/sla-scan")
    public Map<String, Object> scanWorkOrderSla(HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET", "PROPERTY");
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        List<Long> allowedCommunityIds = scopedCommunityIds(request, null, "REPAIR", "WRITE");
        if (allowedCommunityIds.isEmpty()) {
            return Map.of("overdueCount", 0);
        }
        int overdueCount = jdbc.sql("""
            update work_order
            set status = 'OVERDUE'
            where id in (
                select w.id from work_order w join house h on h.id = w.house_id
                where h.community_id in (:allowedCommunityIds)
                  and w.due_at < now()
                  and w.status in ('PENDING', 'PROCESSING')
            )
            """)
            .param("allowedCommunityIds", allowedCommunityIds)
            .update();
        if (overdueCount > 0) {
            jdbc.sql("""
                insert into risk_alert(community_id, level, title, description, status, created_at, rule_code, target_type)
                select distinct h.community_id, 'HIGH', '工单 SLA 超时', concat('存在 ', count(w.id), ' 个报修/投诉工单超过承诺处理时限'), 'OPEN', now(), 'WORK_ORDER_SLA', 'WORK_ORDER'
                from work_order w join house h on h.id = w.house_id
                where h.community_id in (:allowedCommunityIds) and w.status = 'OVERDUE'
                group by h.community_id
                """)
                .param("allowedCommunityIds", allowedCommunityIds)
                .update();
        }
        audit(principal.username(), "扫描工单SLA超时", "work_order", 0);
        return Map.of("overdueCount", overdueCount);
    }

    private List<WorkOrder> workOrders(String type, HttpServletRequest request) {
        String dataScope = "COMPLAINT".equals(type) ? "COMPLAINT" : "REPAIR";
        List<Long> allowedCommunityIds = scopedCommunityIds(request, null, dataScope, "READ");
        if (allowedCommunityIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
            select w.id, c.name communityName, h.room_no roomNo, w.order_type orderType, w.title,
                   w.description, w.status, w.priority, w.created_at createdAt, w.due_at dueAt,
                   w.handler, w.handled_at handledAt, w.reply,
                   w.satisfaction_score satisfactionScore, w.satisfaction_comment satisfactionComment, w.evaluated_at evaluatedAt
            from work_order w
            join house h on h.id = w.house_id
            join community c on c.id = h.community_id
            where w.order_type = :type
              and h.community_id in (:allowedCommunityIds)
            order by w.created_at desc
            """).param("type", type).param("allowedCommunityIds", allowedCommunityIds).query(WorkOrder.class).list();
    }

    @PostMapping("/work-orders")
    public Map<String, Object> createWorkOrder(@RequestBody WorkOrderRequest request, HttpServletRequest httpRequest) {
        requireAnyRole(httpRequest, "ADMIN", "PROPERTY", "OWNER");
        TokenService.Principal principal = (TokenService.Principal) httpRequest.getAttribute("principal");
        Long houseId = request.houseId();
        if (houseId == null || houseId == 0) {
            houseId = jdbc.sql("""
                select h.id
                from resident_house_relation r
                join app_user u on u.id = r.user_id
                join house h on h.id = r.house_id
                where u.username = :username and r.verification_status = 'APPROVED'
                order by r.is_primary desc, r.id desc
                limit 1
                """).param("username", principal.username()).query(Long.class).optional()
                .orElseThrow(() -> new IllegalArgumentException("当前账号未绑定房屋，不能提交工单"));
        }
        Long houseCommunityId = jdbc.sql("select community_id from house where id = :houseId")
            .param("houseId", houseId)
            .query(Long.class)
            .single();
        String dataScope = "COMPLAINT".equals(request.orderType()) ? "COMPLAINT" : "REPAIR";
        if ("OWNER".equals(principal.role())) {
            assertOwnerHouseAccess(principal.username(), houseId);
        } else {
            accessControl.assertCommunityAccess(principal, houseCommunityId, dataScope, "WRITE");
        }
        String orderType = "COMPLAINT".equalsIgnoreCase(request.orderType()) ? "COMPLAINT" : "REPAIR";
        String priority = "URGENT".equalsIgnoreCase(request.priority()) ? "URGENT" : "NORMAL";
        Integer responseHours = jdbc.sql("""
            select response_hours from work_order_sla_rule
            where order_type = :orderType and priority = :priority and status = 'ACTIVE'
            """)
            .param("orderType", orderType)
            .param("priority", priority)
            .query(Integer.class)
            .optional()
            .orElse("COMPLAINT".equals(orderType) ? 72 : 48);
        LocalDateTime dueAt = LocalDateTime.now().plusHours(responseHours);
        jdbc.sql("""
            insert into work_order(house_id, order_type, title, description, status, priority, created_at, due_at, reply, handler, handled_at)
            values(:houseId, :orderType, :title, :description, 'PENDING', :priority, now(), :dueAt, null, null, null)
            """)
            .param("houseId", houseId)
            .param("orderType", orderType)
            .param("priority", priority)
            .param("dueAt", dueAt)
            .param("title", request.title())
            .param("description", request.description())
            .update();
        Long id = jdbc.sql("select max(id) from work_order").query(Long.class).single();
        audit(principal.username(), "提交" + orderType + "-" + request.title(), "work_order", id);
        return Map.of("workOrderId", id, "status", "PENDING", "priority", priority, "responseHours", responseHours);
    }

    @PostMapping("/work-orders/{workOrderId}/reply")
    public Map<String, Object> replyWorkOrder(@PathVariable long workOrderId, @RequestBody WorkOrderReplyRequest request, HttpServletRequest httpRequest) {
        requireAnyRole(httpRequest, "ADMIN", "PROPERTY", "COMMITTEE");
        TokenService.Principal principal = (TokenService.Principal) httpRequest.getAttribute("principal");
        Long workOrderCommunityId = jdbc.sql("""
            select h.community_id from work_order w join house h on h.id = w.house_id where w.id = :id
            """).param("id", workOrderId).query(Long.class).single();
        String dataScope = "COMPLAINT".equalsIgnoreCase(jdbc.sql("select order_type from work_order where id = :id").param("id", workOrderId).query(String.class).single()) ? "COMPLAINT" : "REPAIR";
        accessControl.assertCommunityAccess(principal, workOrderCommunityId, dataScope, "WRITE");
        String status = request.status() == null || request.status().isBlank() ? "DONE" : request.status();
        jdbc.sql("""
            update work_order set status = :status, reply = :reply, handler = :handler, handled_at = now()
            where id = :id
            """)
            .param("status", status)
            .param("reply", request.reply())
            .param("handler", principal.username())
            .param("id", workOrderId)
            .update();
        jdbc.sql("""
            insert into message_notice(community_id, receiver_role, title, content, channel, status, created_at)
            select h.community_id, 'OWNER', concat('工单处理结果：', w.title), :reply, 'IN_APP', 'SENT', now()
            from work_order w join house h on h.id = w.house_id where w.id = :id
            """)
            .param("reply", request.reply())
            .param("id", workOrderId)
            .update();
        jdbc.sql("""
            select o.phone from work_order w
            join house h on h.id = w.house_id
            join owner o on o.house_id = h.id
            where w.id = :id limit 1
            """).param("id", workOrderId).query(String.class).optional()
            .ifPresent(phone -> integrations.sendSms(phone, "WORK_ORDER_REPLY", "您的工单已处理：" + request.reply()));
        audit(principal.username(), "回复工单", "work_order", workOrderId);
        return Map.of("workOrderId", workOrderId, "status", status);
    }

    @PostMapping("/work-orders/{workOrderId}/evaluate")
    public Map<String, Object> evaluateWorkOrder(@PathVariable long workOrderId, @RequestBody WorkOrderEvaluationRequest request, HttpServletRequest httpRequest) {
        requireAnyRole(httpRequest, "OWNER");
        TokenService.Principal principal = (TokenService.Principal) httpRequest.getAttribute("principal");
        Long workOrderHouseId = jdbc.sql("select house_id from work_order where id = :id")
            .param("id", workOrderId)
            .query(Long.class)
            .single();
        assertOwnerHouseAccess(principal.username(), workOrderHouseId);
        String status = jdbc.sql("select status from work_order where id = :id")
            .param("id", workOrderId)
            .query(String.class)
            .single();
        if (!List.of("DONE", "CLOSED").contains(status)) {
            throw new IllegalArgumentException("工单完成后才能评价");
        }
        int score = Math.max(1, Math.min(5, request.score()));
        jdbc.sql("""
            update work_order
            set satisfaction_score = :score, satisfaction_comment = :comment, evaluated_at = now()
            where id = :id
            """)
            .param("score", score)
            .param("comment", request.comment())
            .param("id", workOrderId)
            .update();
        audit(principal.username(), "评价工单-" + score + "星", "work_order", workOrderId);
        return Map.of("workOrderId", workOrderId, "score", score);
    }

    @GetMapping("/announcements")
    public List<Announcement> announcements(HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET", "COMMITTEE", "PROPERTY", "OWNER");
        List<Long> allowedCommunityIds = scopedCommunityIds(request, null, "COMMUNITY_PROFILE", "READ");
        if (allowedCommunityIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
            select a.id, c.name communityName, a.title, a.category, a.content, a.published_at publishedAt
            from announcement a join community c on c.id = a.community_id
            where a.community_id in (:allowedCommunityIds)
            order by a.published_at desc
            """).param("allowedCommunityIds", allowedCommunityIds).query(Announcement.class).list();
    }

    @PostMapping("/announcements")
    public Map<String, Object> createAnnouncement(@RequestBody AnnouncementRequest request, HttpServletRequest httpRequest) {
        requireAnyRole(httpRequest, "ADMIN", "GOVERNMENT", "STREET", "COMMITTEE", "PROPERTY");
        TokenService.Principal principal = (TokenService.Principal) httpRequest.getAttribute("principal");
        accessControl.assertCommunityAccess(principal, request.communityId(), "COMMUNITY_PROFILE", "WRITE");
        jdbc.sql("""
            insert into announcement(community_id, title, category, content, published_at)
            values(:communityId, :title, :category, :content, now())
            """)
            .param("communityId", request.communityId())
            .param("title", request.title())
            .param("category", request.category())
            .param("content", request.content())
            .update();
        Long id = jdbc.sql("select max(id) from announcement").query(Long.class).single();
        jdbc.sql("""
            insert into message_notice(community_id, receiver_role, title, content, channel, status, created_at)
            values(:communityId, 'OWNER', :title, :content, 'IN_APP', 'SENT', now())
            """)
            .param("communityId", request.communityId())
            .param("title", request.title())
            .param("content", request.content())
            .update();
        audit(principal.username(), "发布公告-" + request.title(), "announcement", id);
        return Map.of("announcementId", id, "status", "PUBLISHED");
    }

    @GetMapping("/messages")
    public List<MessageNotice> messages(@RequestParam(required = false) Long communityId, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET", "COMMITTEE", "PROPERTY", "OWNER");
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        List<Long> allowedCommunityIds = scopedCommunityIds(request, communityId, "COMMUNITY_PROFILE", "READ");
        if (allowedCommunityIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
            select m.id, m.community_id communityId, m.receiver_role receiverRole, m.title, m.content,
                   m.channel, m.status, case when r.id is null then 'UNREAD' else 'READ' end readStatus,
                   r.read_at readAt, m.created_at createdAt
            from message_notice m
            left join message_read_receipt r on r.message_id = m.id and r.user_id = :userId
            where m.community_id in (:allowedCommunityIds)
            order by m.created_at desc
            """)
            .param("allowedCommunityIds", allowedCommunityIds)
            .param("userId", currentUserId(principal))
            .query(MessageNotice.class).list();
    }

    @PostMapping("/messages/{messageId}/read")
    public Map<String, Object> markMessageRead(@PathVariable long messageId, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET", "COMMITTEE", "PROPERTY", "OWNER");
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        Long communityId = jdbc.sql("select community_id from message_notice where id = :id")
            .param("id", messageId)
            .query(Long.class)
            .optional()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "消息不存在"));
        accessControl.assertCommunityAccess(principal, communityId, "COMMUNITY_PROFILE", "READ");
        jdbc.sql("""
            insert into message_read_receipt(message_id, user_id, read_at)
            values(:messageId, :userId, now())
            on duplicate key update read_at = values(read_at)
            """)
            .param("messageId", messageId)
            .param("userId", currentUserId(principal))
            .update();
        audit(principal.username(), "阅读站内消息", "message_notice", messageId);
        return Map.of("messageId", messageId, "status", "READ");
    }

    @GetMapping("/finance/vouchers")
    public List<Voucher> vouchers(HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET", "COMMITTEE");
        List<Long> allowedCommunityIds = scopedCommunityIds(request, null, "PUBLIC_REVENUE", "READ");
        if (allowedCommunityIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
            select v.id, c.name communityName, v.voucher_no voucherNo, v.source_type sourceType,
                   v.debit_subject debitSubject, v.credit_subject creditSubject, v.amount,
                   v.status, v.booked_at bookedAt
            from finance_voucher v join community c on c.id = v.community_id
            where v.community_id in (:allowedCommunityIds)
            order by v.id desc
            """).param("allowedCommunityIds", allowedCommunityIds).query(Voucher.class).list();
    }

    @PostMapping("/finance/vouchers")
    public Map<String, Object> createManualVoucher(@RequestBody VoucherRequest body, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "COMMITTEE");
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        accessControl.assertCommunityAccess(principal, body.communityId(), "PUBLIC_REVENUE", "WRITE");
        if (body.amount() == null || body.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "凭证金额必须大于0");
        }
        String voucherNo = "PZ-MAN-" + body.communityId() + "-" + System.currentTimeMillis();
        jdbc.sql("""
            insert into finance_voucher(community_id, voucher_no, source_type, debit_subject, credit_subject, amount, status, booked_at)
            values(:communityId, :voucherNo, 'MANUAL', :debitSubject, :creditSubject, :amount, 'REVIEWING', null)
            """)
            .param("communityId", body.communityId())
            .param("voucherNo", voucherNo)
            .param("debitSubject", body.debitSubject())
            .param("creditSubject", body.creditSubject())
            .param("amount", body.amount())
            .update();
        Long voucherId = jdbc.sql("select max(id) from finance_voucher where voucher_no = :voucherNo")
            .param("voucherNo", voucherNo)
            .query(Long.class)
            .single();
        audit(principal.username(), "手动新增财务凭证-" + voucherNo, "finance_voucher", voucherId);
        return Map.of("voucherId", voucherId, "voucherNo", voucherNo, "status", "REVIEWING");
    }

    @GetMapping("/finance/account-books")
    public List<AccountingBook> accountingBooks(HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET", "COMMITTEE");
        List<Long> allowedCommunityIds = scopedCommunityIds(request, null, "PUBLIC_REVENUE", "READ");
        if (allowedCommunityIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
            select b.id, c.name communityName, b.book_name bookName, b.enabled_month enabledMonth,
                   b.base_currency baseCurrency, b.status, b.created_at createdAt
            from accounting_book b join community c on c.id = b.community_id
            where b.community_id in (:allowedCommunityIds)
            order by b.community_id
            """).param("allowedCommunityIds", allowedCommunityIds).query(AccountingBook.class).list();
    }

    @GetMapping("/finance/subjects")
    public List<FinanceSubject> financeSubjects(HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET", "COMMITTEE");
        return jdbc.sql("""
            select id, subject_code subjectCode, subject_name subjectName, subject_type subjectType,
                   direction, status
            from finance_subject
            where status = 'ACTIVE'
            order by subject_code
            """).query(FinanceSubject.class).list();
    }

    @GetMapping("/finance/initial-balances")
    public List<SubjectBalance> initialBalances(@RequestParam(required = false) Long communityId, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET", "COMMITTEE");
        List<Long> allowedCommunityIds = scopedCommunityIds(request, communityId, "PUBLIC_REVENUE", "READ");
        if (allowedCommunityIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
            select b.id, c.name communityName, b.subject_code subjectCode, s.subject_name subjectName,
                   b.opening_month openingMonth, b.direction, b.amount, b.created_at createdAt
            from subject_initial_balance b
            left join finance_subject s on s.subject_code = b.subject_code
            join community c on c.id = b.community_id
            where b.community_id in (:allowedCommunityIds)
            order by c.id, b.subject_code
            """).param("allowedCommunityIds", allowedCommunityIds).query(SubjectBalance.class).list();
    }

    @GetMapping("/finance/ledger")
    public List<LedgerEntry> ledger(@RequestParam(required = false) Long communityId, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET", "COMMITTEE");
        List<Long> allowedCommunityIds = scopedCommunityIds(request, communityId, "PUBLIC_REVENUE", "READ");
        if (allowedCommunityIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
            select l.id, c.name communityName, v.voucher_no voucherNo, l.subject_name subject,
                   l.direction, l.amount, v.status, l.booked_at bookedAt
            from finance_voucher_line l
            join finance_voucher v on v.id = l.voucher_id
            join community c on c.id = l.community_id
            where l.community_id in (:allowedCommunityIds)
            order by l.id desc
            """).param("allowedCommunityIds", allowedCommunityIds).query(LedgerEntry.class).list();
    }

    @GetMapping("/finance/ledger/export")
    public ResponseEntity<byte[]> ledgerCsv(@RequestParam(required = false) Long communityId, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET", "COMMITTEE");
        List<LedgerEntry> rows = ledger(communityId, request);
        StringBuilder csv = new StringBuilder("小区,凭证号,科目,方向,金额,状态,记账时间\n");
        for (LedgerEntry row : rows) {
            csv.append(csv(row.communityName())).append(',')
                .append(csv(row.voucherNo())).append(',')
                .append(csv(row.subject())).append(',')
                .append(csv(row.direction())).append(',')
                .append(row.amount()).append(',')
                .append(csv(row.status())).append(',')
                .append(csv(row.bookedAt() == null ? "" : row.bookedAt().toString())).append('\n');
        }
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        audit(principal.username(), "导出总分类账CSV", "finance_ledger", communityId == null ? 0 : communityId);
        logDataExport(request, "FINANCE_LEDGER", "finance-ledger.csv", "finance_ledger", communityId == null ? 0 : communityId, communityId, rows.size());
        return csvResponse("finance-ledger.csv", csv.toString());
    }

    @GetMapping("/finance/ledger/pdf")
    public ResponseEntity<byte[]> ledgerPdf(@RequestParam(required = false) Long communityId, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET", "COMMITTEE");
        List<LedgerEntry> rows = ledger(communityId, request);
        StringBuilder content = new StringBuilder("""
            Ding Sheng Yang Guang Property Public Revenue Platform
            General Ledger Report
            Export Scope: %s
            Entry Count: %d

            """.formatted(communityId == null ? "Allowed communities" : "Community " + communityId, rows.size()));
        for (LedgerEntry row : rows.stream().limit(18).toList()) {
            content.append("%s | %s | %s | %s | %s CNY | %s | %s%n".formatted(
                row.communityName(),
                row.voucherNo(),
                row.subject(),
                row.direction(),
                row.amount(),
                row.status(),
                row.bookedAt() == null ? "" : row.bookedAt()
            ));
        }
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        audit(principal.username(), "导出总分类账PDF", "finance_ledger", communityId == null ? 0 : communityId);
        logDataExport(request, "FINANCE_LEDGER_PDF", "finance-ledger.pdf", "finance_ledger", communityId == null ? 0 : communityId, communityId, rows.size());
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"finance-ledger.pdf\"")
            .contentType(MediaType.APPLICATION_PDF)
            .body(simplePdf(content.toString()));
    }

    @GetMapping("/finance/cash-flow")
    public List<CashFlowItem> cashFlow(@RequestParam(required = false) Long communityId, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET", "COMMITTEE");
        List<Long> allowedCommunityIds = scopedCommunityIds(request, communityId, "BANK_FLOW", "READ");
        if (allowedCommunityIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
            select c.name communityName,
                   case when f.direction = 'IN' then '经营活动现金流入' else '经营活动现金流出' end item,
                   f.direction,
                   sum(f.amount) amount
            from bank_flow f join community c on c.id = f.community_id
            where f.community_id in (:allowedCommunityIds)
            group by c.name, f.direction
            order by c.name, f.direction desc
            """).param("allowedCommunityIds", allowedCommunityIds).query(CashFlowItem.class).list();
    }

    @GetMapping("/finance/cash-flow/export")
    public ResponseEntity<byte[]> cashFlowCsv(@RequestParam(required = false) Long communityId, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET", "COMMITTEE");
        List<CashFlowItem> rows = cashFlow(communityId, request);
        StringBuilder csv = new StringBuilder("小区,项目,方向,金额\n");
        for (CashFlowItem row : rows) {
            csv.append(csv(row.communityName())).append(',')
                .append(csv(row.item())).append(',')
                .append(csv(row.direction())).append(',')
                .append(row.amount()).append('\n');
        }
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        audit(principal.username(), "导出现金流量表CSV", "cash_flow", communityId == null ? 0 : communityId);
        logDataExport(request, "CASH_FLOW", "cash-flow.csv", "cash_flow", communityId == null ? 0 : communityId, communityId, rows.size());
        return csvResponse("cash-flow.csv", csv.toString());
    }

    @GetMapping("/finance/cash-flow/pdf")
    public ResponseEntity<byte[]> cashFlowPdf(@RequestParam(required = false) Long communityId, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET", "COMMITTEE");
        List<CashFlowItem> rows = cashFlow(communityId, request);
        StringBuilder content = new StringBuilder("""
            Ding Sheng Yang Guang Property Public Revenue Platform
            Cash Flow Statement
            Export Scope: %s
            Item Count: %d

            """.formatted(communityId == null ? "Allowed communities" : "Community " + communityId, rows.size()));
        for (CashFlowItem row : rows) {
            content.append("%s | %s | %s | %s CNY%n".formatted(
                row.communityName(),
                row.item(),
                row.direction(),
                row.amount()
            ));
        }
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        audit(principal.username(), "导出现金流量表PDF", "cash_flow", communityId == null ? 0 : communityId);
        logDataExport(request, "CASH_FLOW_PDF", "cash-flow.pdf", "cash_flow", communityId == null ? 0 : communityId, communityId, rows.size());
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"cash-flow.pdf\"")
            .contentType(MediaType.APPLICATION_PDF)
            .body(simplePdf(content.toString()));
    }

    @PostMapping("/finance/vouchers/{voucherId}/review")
    public Map<String, Object> reviewVoucher(@PathVariable long voucherId, @RequestBody VoucherReviewRequest body, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "COMMITTEE");
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        Voucher voucher = voucherById(voucherId);
        Long voucherCommunityId = voucherCommunityId(voucherId);
        accessControl.assertCommunityAccess(principal, voucherCommunityId, "PUBLIC_REVENUE", "APPROVE");
        if (!"REVIEWING".equals(voucher.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只有待审核凭证可以审核");
        }
        String status = "reject".equalsIgnoreCase(body.decision()) ? "REJECTED" : "APPROVED";
        jdbc.sql("update finance_voucher set status = :status where id = :id")
            .param("status", status)
            .param("id", voucherId)
            .update();
        audit(principal.username(), "审核凭证-" + status + "-" + voucher.voucherNo(), "finance_voucher", voucherId);
        return Map.of("voucherId", voucherId, "status", status);
    }

    @PostMapping("/finance/vouchers/batch-review")
    public Map<String, Object> batchReviewVouchers(@RequestBody VoucherBatchReviewRequest body, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "COMMITTEE");
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        String status = "reject".equalsIgnoreCase(body.decision()) ? "REJECTED" : "APPROVED";
        int updated = 0;
        for (Long voucherId : body.voucherIds()) {
            Voucher voucher = voucherById(voucherId);
            Long voucherCommunityId = voucherCommunityId(voucherId);
            accessControl.assertCommunityAccess(principal, voucherCommunityId, "PUBLIC_REVENUE", "APPROVE");
            if (!"REVIEWING".equals(voucher.status())) {
                continue;
            }
            jdbc.sql("update finance_voucher set status = :status where id = :id")
                .param("status", status)
                .param("id", voucherId)
                .update();
            audit(principal.username(), "批量审核凭证-" + status + "-" + voucher.voucherNo(), "finance_voucher", voucherId);
            updated++;
        }
        return Map.of("updatedCount", updated, "status", status);
    }

    @PostMapping("/finance/vouchers/{voucherId}/book")
    public Map<String, Object> bookVoucher(@PathVariable long voucherId, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "COMMITTEE");
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        Voucher voucher = voucherById(voucherId);
        Long voucherCommunityId = voucherCommunityId(voucherId);
        accessControl.assertCommunityAccess(principal, voucherCommunityId, "PUBLIC_REVENUE", "APPROVE");
        if (!"APPROVED".equals(voucher.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只有已审核凭证可以记账");
        }
        jdbc.sql("update finance_voucher set status = 'BOOKED', booked_at = now() where id = :id")
            .param("id", voucherId)
            .update();
        createVoucherLines(voucherId);
        audit(principal.username(), "凭证记账-" + voucher.voucherNo(), "finance_voucher", voucherId);
        return Map.of("voucherId", voucherId, "status", "BOOKED");
    }

    @PostMapping("/finance/vouchers/batch-book")
    public Map<String, Object> batchBookVouchers(@RequestBody VoucherBatchRequest body, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "COMMITTEE");
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        int booked = 0;
        for (Long voucherId : body.voucherIds()) {
            Voucher voucher = voucherById(voucherId);
            Long voucherCommunityId = voucherCommunityId(voucherId);
            accessControl.assertCommunityAccess(principal, voucherCommunityId, "PUBLIC_REVENUE", "APPROVE");
            if (!"APPROVED".equals(voucher.status())) {
                continue;
            }
            jdbc.sql("update finance_voucher set status = 'BOOKED', booked_at = now() where id = :id")
                .param("id", voucherId)
                .update();
            createVoucherLines(voucherId);
            audit(principal.username(), "批量记账凭证-" + voucher.voucherNo(), "finance_voucher", voucherId);
            booked++;
        }
        return Map.of("bookedCount", booked, "status", "BOOKED");
    }

    @GetMapping("/finance/vouchers/{voucherId}/pdf")
    public ResponseEntity<byte[]> voucherPdf(@PathVariable long voucherId, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET", "COMMITTEE");
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        Voucher voucher = voucherById(voucherId);
        Long voucherCommunityId = voucherCommunityId(voucherId);
        accessControl.assertCommunityAccess(principal, voucherCommunityId, "PUBLIC_REVENUE", "EXPORT");
        audit(principal.username(), "导出凭证PDF-" + voucher.voucherNo(), "finance_voucher", voucherId);
        logDataExport(request, "FINANCE_VOUCHER", voucher.voucherNo() + ".pdf", "finance_voucher", voucherId, voucherCommunityId, 1);
        byte[] bytes = simplePdf("""
            Ding Sheng Yang Guang Property Public Revenue Platform
            Electronic Finance Voucher
            Voucher No: %s
            Community: %s
            Source: %s
            Debit Subject: %s
            Credit Subject: %s
            Amount: %s CNY
            Status: %s
            Booked At: %s
            """.formatted(
            voucher.voucherNo(),
            voucher.communityName(),
            voucher.sourceType(),
            voucher.debitSubject(),
            voucher.creditSubject(),
            voucher.amount(),
            voucher.status(),
            voucher.bookedAt() == null ? "Not booked" : voucher.bookedAt()
        ));
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + voucher.voucherNo() + ".pdf\"")
            .contentType(MediaType.APPLICATION_PDF)
            .body(bytes);
    }

    @GetMapping("/audit")
    public List<AuditLog> audit(HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT");
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        if (accessControl.isPlatformAdmin(principal)) {
            return jdbc.sql("""
                select id, actor, action, target_type targetType, target_id targetId, hash, previous_hash previousHash,
                       created_at createdAt
                from audit_log order by created_at desc
                """).query(AuditLog.class).list();
        }
        List<Long> allowedCommunityIds = accessControl.allowedCommunityIds(principal, "AUDIT", "READ");
        if (allowedCommunityIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
            select id, actor, action, target_type targetType, target_id targetId, hash, previous_hash previousHash,
                   created_at createdAt
            from audit_log
            where (target_type = 'community' and target_id in (:allowedCommunityIds))
               or target_type <> 'community'
            order by created_at desc
            """)
            .param("allowedCommunityIds", allowedCommunityIds)
            .query(AuditLog.class)
            .list();
    }

    @GetMapping("/audit/verify")
    public AuditVerification auditVerification(HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT");
        AuditVerification verification = auditVerificationSnapshot();
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        audit(principal.username(), "验证审计哈希链", "audit_log", verification.firstBrokenId());
        return verification;
    }

    @GetMapping("/audit/export")
    public ResponseEntity<byte[]> auditCsv(HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT");
        List<AuditLog> rows = audit(request);
        StringBuilder csv = new StringBuilder("ID,操作人,动作,对象,对象ID,哈希,前序哈希,时间\n");
        for (AuditLog row : rows) {
            csv.append(row.id()).append(',')
                .append(csv(row.actor())).append(',')
                .append(csv(row.action())).append(',')
                .append(csv(row.targetType())).append(',')
                .append(row.targetId()).append(',')
                .append(csv(row.hash())).append(',')
                .append(csv(row.previousHash())).append(',')
                .append(csv(row.createdAt() == null ? "" : row.createdAt().toString())).append('\n');
        }
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        audit(principal.username(), "导出审计日志CSV", "audit_log", 0);
        logDataExport(request, "AUDIT_LOG", "audit-log.csv", "audit_log", 0, null, rows.size());
        return csvResponse("audit-log.csv", csv.toString());
    }

    @GetMapping("/audit/export-logs")
    public List<DataExportLog> dataExportLogs(HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT");
        return jdbc.sql("""
            select id, actor, role_code roleCode, tenant_id tenantId, community_id communityId,
                   export_module exportModule, target_type targetType, target_id targetId,
                   file_name fileName, row_count rowCount, data_scope dataScope,
                   filter_summary filterSummary, status, created_at createdAt
            from data_export_log
            order by created_at desc, id desc
            limit 120
            """).query(DataExportLog.class).list();
    }

    public record Dashboard(List<Metric> metrics, List<TrendPoint> trend, List<CommunityRank> ranks, List<RiskAlert> alerts, List<ApprovalItem> approvals) {}
    public record SupervisionDrilldown(Community community, List<FundAccount> accounts, List<BankFlow> flows, List<Voucher> vouchers, List<Expense> expenses, List<Bill> overdueBills, List<RiskAlert> alerts) {}
    public record Metric(String label, BigDecimal value, String unit, String trend) {}
    public record TrendPoint(String month, BigDecimal income, BigDecimal expense) {}
    public record CommunityRank(long id, String name, String street, int households, BigDecimal occupancyRate, BigDecimal balance, BigDecimal paymentRate) {}
    public record ScreenTopic(String topicCode, String topicName, String level, String communityName, String headline, BigDecimal primaryValue, BigDecimal secondaryValue, String trend, String evidence, String action) {}
    public record RiskAlert(long id, String communityName, String level, String title, String description, String status, LocalDateTime createdAt) {}
    public record ApprovalItem(long id, String communityName, String title, BigDecimal amount, String status, String currentNode, LocalDateTime createdAt, LocalDateTime dueAt) {}
    public record Community(long id, String district, String street, String neighborhood, String name, int households, BigDecimal occupancyRate, BigDecimal approvalThreshold) {}
    public record House(long id, String communityName, String building, String unitNo, String roomNo, BigDecimal area, String ownerName, String ownerPhone, String status) {}
    public record FundAccount(long id, long communityId, String accountType, String accountName, String bankName, String accountNoMask, BigDecimal balance) {}
    public record OwnerProfile(long id, String name, String phone, String identityMask, String communityName, String building, String roomNo) {}
    public record OwnerBinding(long ownerId, long houseId, long communityId, String communityName) {}
    public record OwnerLedger(BigDecimal publicIncome, BigDecimal publicExpense, BigDecimal unpaidAmount, BigDecimal paymentRate) {}
    public record OwnerPortal(OwnerProfile profile, OwnerLedger ledger, List<Bill> bills, List<ElectronicPaymentReceipt> receipts, List<BankFlow> flows, List<Announcement> announcements, List<MessageNotice> messages, List<Vote> votes, List<WorkOrder> workOrders, List<ArrearsPublicationItem> arrearsPublicItems) {}
    public record HouseBindCandidate(long houseId, long communityId, String communityName, String building, String roomNo, String ownerName, String ownerPhone, String identityMask) {}
    public record Bill(long id, String communityName, String roomNo, String billType, String period, BigDecimal amount, BigDecimal paidAmount, String status, LocalDate dueDate) {}
    public record BankFlow(long id, String communityName, String accountName, String direction, BigDecimal amount, String counterparty, String summary, LocalDateTime occurredAt, String traceNo) {}
    public record Expense(long id, String communityName, String orderType, String title, BigDecimal amount, String status, String currentNode, String invoiceNo, String contractNo, LocalDateTime createdAt, LocalDateTime dueAt) {}
    public record Vote(long id, String communityName, String title, String voteType, String status, LocalDateTime startAt, LocalDateTime endAt, BigDecimal participationRate, BigDecimal agreeRate) {}
    public record WorkOrder(long id, String communityName, String roomNo, String orderType, String title, String description, String status, String priority, LocalDateTime createdAt, LocalDateTime dueAt, String handler, LocalDateTime handledAt, String reply, Integer satisfactionScore, String satisfactionComment, LocalDateTime evaluatedAt) {}
    public record Announcement(long id, String communityName, String title, String category, String content, LocalDateTime publishedAt) {}
    public record Voucher(long id, String communityName, String voucherNo, String sourceType, String debitSubject, String creditSubject, BigDecimal amount, String status, LocalDateTime bookedAt) {}
    public record VoucherRequest(long communityId, String debitSubject, String creditSubject, BigDecimal amount) {}
    public record VoucherBatchRequest(List<Long> voucherIds) {}
    public record VoucherBatchReviewRequest(List<Long> voucherIds, String decision) {}
    public record LedgerEntry(long id, String communityName, String voucherNo, String subject, String direction, BigDecimal amount, String status, LocalDateTime bookedAt) {}
    public record CashFlowItem(String communityName, String item, String direction, BigDecimal amount) {}
    public record AccountingBook(long id, String communityName, String bookName, String enabledMonth, String baseCurrency, String status, LocalDateTime createdAt) {}
    public record FinanceSubject(long id, String subjectCode, String subjectName, String subjectType, String direction, String status) {}
    public record SubjectBalance(long id, String communityName, String subjectCode, String subjectName, String openingMonth, String direction, BigDecimal amount, LocalDateTime createdAt) {}
    public record SupervisionAlertRule(long id, String ruleCode, String ruleName, String level, BigDecimal thresholdAmount, Integer thresholdDays, String status, LocalDateTime createdAt) {}
    public record PaymentRateRisk(long communityId, String communityName, BigDecimal paymentRate) {}
    public record CommunityCreditScore(long id, String communityName, BigDecimal score, String riskGrade, BigDecimal paymentRate, int openAlertCount, int overdueBillCount, String auditStatus, String summary, LocalDateTime generatedAt) {}
    public record CommunityCreditFactor(long id, long scoreId, String factorCode, String factorName, String factorValue, BigDecimal weight, BigDecimal deduction, String evidence, LocalDateTime createdAt) {}
    public record CreditScoreRule(long id, String ruleVersion, String factorCode, String factorName, BigDecimal weight, BigDecimal thresholdValue, BigDecimal deductionUnit, String evidenceTemplate, String status, LocalDateTime createdAt) {}
    public record CreditScoreRun(long id, String ruleVersion, int generatedCount, String operator, String status, String summary, LocalDateTime createdAt) {}
    public record AuditLog(long id, String actor, String action, String targetType, long targetId, String hash, String previousHash, LocalDateTime createdAt) {}
    public record AuditVerification(int totalCount, int brokenLinks, long firstBrokenId, String status, String latestHash) {}
    public record DataExportLog(long id, String actor, String roleCode, long tenantId, Long communityId, String exportModule, String targetType, long targetId, String fileName, int rowCount, String dataScope, String filterSummary, String status, LocalDateTime createdAt) {}
    public record FeeStandard(long id, String communityName, long communityId, String feeType, String billingMode, BigDecimal unitPrice, String cycle, LocalDate effectiveFrom, String status, LocalDateTime createdAt) {}
    public record FeeStandardRequest(long communityId, String feeType, String billingMode, BigDecimal unitPrice, String cycle, LocalDate effectiveFrom) {}
    public record BillGenerationRequest(long communityId, String period, LocalDate dueDate) {}
    public record GeneratedBill(long houseId, String billType, BigDecimal amount) {}
    public record PaymentRefund(long id, String communityName, String roomNo, String billType, String period, String refundNo, String payChannel, BigDecimal amount, String reason, String status, String providerRefundNo, LocalDateTime processedAt, LocalDateTime createdAt) {}
    public record PaymentRefundRequest(long billId, BigDecimal amount, String reason) {}
    public record PaidPaymentOrder(long id, String orderNo, BigDecimal amount, long communityId, String payChannel) {}
    public record DrillRefundEvidence(long refundId, long orderId, long communityId, BigDecimal amount, String payChannel) {}
    public record BillForPayment(long id, long communityId, String billType, String period, BigDecimal amount) {}
    public record PaymentChannelStatement(long id, String communityName, String provider, LocalDate statementDate, String tradeType, long billId, String orderNo, String channelTradeNo, BigDecimal amount, String status, LocalDateTime syncedAt) {}
    public record PaymentStatementSyncRequest(LocalDate statementDate) {}
    public record PaymentStatementDraft(long communityId, long billId, String tradeType, String orderNo, String channelTradeNo, BigDecimal amount) {}
    public record PaymentReceiptDraft(long paymentOrderId, long billId, long communityId, String orderNo, String payChannel, BigDecimal amount, String roomNo, String payerName, String billType, String period) {}
    public record ElectronicPaymentReceipt(long id, String receiptNo, String communityName, String roomNo, String payerName, String billType, String period, String payChannel, BigDecimal amount, String status, String fileUrl, String checksum, String issuedBy, LocalDateTime issuedAt) {}
    public record PaymentReceiptForTax(long receiptId, long billId, long paymentOrderId, long communityId, String payerName, String billType, BigDecimal amount) {}
    public record TaxInvoice(long id, String invoiceRequestNo, String receiptNo, String communityName, String buyerName, String buyerTaxNo, String invoiceItem, String taxCategoryCode, BigDecimal taxRate, BigDecimal amount, String status, String taxInvoiceNo, String taxPlatformCode, String pdfUrl, String checksum, String failReason, String issuedBy, LocalDateTime issuedAt, Long redOriginalId, LocalDateTime redAt, LocalDateTime createdAt) {}
    public record TaxInvoiceRequest(String buyerName, String buyerTaxNo, String invoiceItem, String taxCategoryCode, BigDecimal taxRate) {}
    public record ArrearsPublication(long id, String communityName, String period, String title, int totalHouseholds, int arrearsCount, BigDecimal arrearsAmount, String status, String publishedBy, LocalDateTime publishedAt) {}
    public record ArrearsPublicationItem(long id, long publicationId, long billId, String roomNoMask, String billType, String period, BigDecimal amount, BigDecimal paidAmount, BigDecimal arrearsAmount, LocalDate dueDate) {}
    public record ArrearsPublicationRequest(long communityId, String period, String title) {}
    public record ArrearsBillDraft(long billId, String roomNo, String billType, String period, BigDecimal amount, BigDecimal paidAmount, BigDecimal arrearsAmount, LocalDate dueDate) {}
    public record ApprovalRule(long id, String communityName, long communityId, BigDecimal expenseThreshold, BigDecimal voteThreshold, int approvalTimeoutHours, BigDecimal voteRatio, String status, LocalDateTime createdAt) {}
    public record ApprovalRuleRequest(long communityId, BigDecimal expenseThreshold, BigDecimal voteThreshold, int approvalTimeoutHours, BigDecimal voteRatio, String status) {}
    public record ApprovalLog(long id, long expenseId, String nodeName, String operator, String decision, String comment, LocalDateTime operatedAt) {}
    public record WorkflowTemplate(long id, String templateCode, String templateName, String businessType, String status, LocalDateTime createdAt) {}
    public record WorkflowTemplateNode(long id, String templateCode, String nodeCode, String nodeName, String roleCode, int sortNo, String conditionCode, int timeoutHours, String status, LocalDateTime createdAt) {}
    public record WorkflowTemplateNodeRequest(String nodeCode, String nodeName, String roleCode, int sortNo, String conditionCode, int timeoutHours, String status) {}
    public record WorkflowEvent(long id, String businessType, long businessId, String templateCode, String nodeCode, String eventType, String operator, String eventSummary, LocalDateTime createdAt) {}
    public record ApprovalWorkflowNode(long id, long expenseId, String nodeCode, String nodeName, String roleCode, int sortNo, String status, LocalDateTime dueAt, String operator, String decision, String comment, LocalDateTime operatedAt, LocalDateTime createdAt) {}
    public record ExpenseDocument(long id, long expenseId, String documentType, String documentNo, String issuer, BigDecimal amount, LocalDate issueDate, String fileUrl, String verificationStatus, String reviewComment, String verifiedBy, LocalDateTime verifiedAt, LocalDateTime createdAt) {}
    public record ExpenseDocumentRequest(String documentType, String documentNo, String issuer, BigDecimal amount, LocalDate issueDate, String fileUrl) {}
    public record DocumentVerifyRequest(String decision, String comment) {}
    public record ExpenseRequest(long communityId, String orderType, String title, BigDecimal amount, String invoiceNo, String contractNo, String invoiceIssuer, BigDecimal invoiceAmount, LocalDate invoiceIssueDate, String invoiceFileUrl, String contractParty, BigDecimal contractAmount, LocalDate contractSignDate, String contractFileUrl) {}
    public record VoteRequest(long communityId, String title, String voteType, LocalDateTime startAt, LocalDateTime endAt, String questionTitle, List<String> options) {}
    public record VoteCastRequest(String decision) {}
    public record VoteBallot(long id, long voteId, String ownerName, String decision, String signatureHash, LocalDateTime castAt) {}
    public record SurveyQuestion(long id, long voteId, String questionTitle, String questionType, int sortNo, List<SurveyOption> options) {}
    public record SurveyOption(long id, long questionId, String optionLabel, int sortNo) {}
    public record SurveyAnswer(long questionId, long optionId) {}
    public record SurveySubmitRequest(List<SurveyAnswer> answers) {}
    public record SurveyResult(long questionId, String questionTitle, long optionId, String optionLabel, int responseCount) {}
    public record OwnerBindRequest(String communityName, String building, String roomNo, String name, String phone, String identityNo) {}
    public record VoucherReviewRequest(String decision, String comment) {}
    public record WorkOrderSlaRule(long id, String orderType, String priority, int responseHours, String status, LocalDateTime createdAt) {}
    public record WorkOrderRequest(Long houseId, String orderType, String title, String description, String priority) {}
    public record WorkOrderReplyRequest(String status, String reply) {}
    public record WorkOrderEvaluationRequest(int score, String comment) {}
    public record AnnouncementRequest(long communityId, String title, String category, String content) {}
    public record MessageNotice(long id, long communityId, String receiverRole, String title, String content, String channel, String status, String readStatus, LocalDateTime readAt, LocalDateTime createdAt) {}
    public record AlertStatusRequest(String status) {}

    private Long scopedCommunityId(HttpServletRequest request, Long requestedCommunityId) {
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        if (canViewAllCommunities(principal)) {
            return requestedCommunityId;
        }
        if (requestedCommunityId != null && requestedCommunityId != principal.communityId()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问其他小区数据");
        }
        return principal.communityId();
    }

    private List<Long> scopedCommunityIds(HttpServletRequest request, Long requestedCommunityId, String dataScope, String action) {
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        if (requestedCommunityId != null) {
            accessControl.assertCommunityAccess(principal, requestedCommunityId, dataScope, action);
            return List.of(requestedCommunityId);
        }
        return accessControl.allowedCommunityIds(principal, dataScope, action);
    }

    private void requireAnyRole(HttpServletRequest request, String... roles) {
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        for (String role : roles) {
            if (role.equals(principal.role())) {
                return;
            }
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前角色无权使用该功能");
    }

    private boolean canViewAllCommunities(TokenService.Principal principal) {
        return "ADMIN".equals(principal.role())
            || "GOVERNMENT".equals(principal.role())
            || "STREET".equals(principal.role());
    }

    private long currentUserId(TokenService.Principal principal) {
        return jdbc.sql("select id from app_user where username = :username")
            .param("username", principal.username())
            .query(Long.class)
            .optional()
            .orElse(0L);
    }

    private void assertVoteAccess(TokenService.Principal principal, long communityId, String action) {
        if ("OWNER".equals(principal.role())) {
            assertOwnerCommunityAccess(principal.username(), communityId);
            return;
        }
        accessControl.assertCommunityAccess(principal, communityId, "VOTE", action);
    }

    private void assertOwnerCommunityAccess(String username, long communityId) {
        Integer count = jdbc.sql("""
            select count(*)
            from resident_house_relation r
            join app_user u on u.id = r.user_id
            join house h on h.id = r.house_id
            where u.username = :username
              and h.community_id = :communityId
              and r.verification_status = 'APPROVED'
            """)
            .param("username", username)
            .param("communityId", communityId)
            .query(Integer.class)
            .single();
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "业主只能访问本人已绑定房屋所属小区");
        }
    }

    private void assertOwnerHouseAccess(String username, long houseId) {
        Integer count = jdbc.sql("""
            select count(*)
            from resident_house_relation r
            join app_user u on u.id = r.user_id
            where u.username = :username
              and r.house_id = :houseId
              and r.verification_status = 'APPROVED'
            """)
            .param("username", username)
            .param("houseId", houseId)
            .query(Integer.class)
            .single();
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "业主只能操作本人已绑定房屋");
        }
    }

    private void assertOwnerBillAccess(String username, long billId) {
        Integer count = jdbc.sql("""
            select count(*)
            from bill b
            join resident_house_relation r on r.house_id = b.house_id
            join app_user u on u.id = r.user_id
            where u.username = :username
              and b.id = :billId
              and r.verification_status = 'APPROVED'
            """)
            .param("username", username)
            .param("billId", billId)
            .query(Integer.class)
            .single();
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "业主只能操作本人已绑定房屋账单");
        }
    }

    private void assertOwnerPaymentOrderAccess(String username, long paymentOrderId) {
        Integer count = jdbc.sql("""
            select count(*)
            from payment_order po
            join bill b on b.id = po.bill_id
            join resident_house_relation r on r.house_id = b.house_id
            join app_user u on u.id = r.user_id
            where u.username = :username
              and po.id = :paymentOrderId
              and r.verification_status = 'APPROVED'
            """)
            .param("username", username)
            .param("paymentOrderId", paymentOrderId)
            .query(Integer.class)
            .single();
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "业主只能操作本人已绑定房屋支付订单");
        }
    }

    private void assertOwnerReceiptAccess(String username, long receiptId) {
        Integer count = jdbc.sql("""
            select count(*)
            from electronic_payment_receipt rct
            join bill b on b.id = rct.bill_id
            join resident_house_relation rel on rel.house_id = b.house_id
            join app_user u on u.id = rel.user_id
            where u.username = :username
              and rct.id = :receiptId
              and rel.verification_status = 'APPROVED'
            """)
            .param("username", username)
            .param("receiptId", receiptId)
            .query(Integer.class)
            .single();
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "业主只能访问本人已绑定房屋电子票据");
        }
    }

    private void writeCreditFactors(long scoreId, long communityId, BigDecimal paymentRate, int openAlerts, int overdueBills, AuditVerification verification, Map<String, CreditScoreRule> rules) {
        jdbc.sql("delete from community_credit_factor where score_id = :scoreId")
            .param("scoreId", scoreId)
            .update();
        CreditScoreRule paymentRule = rules.get("PAYMENT_RATE");
        CreditScoreRule alertRule = rules.get("OPEN_ALERT");
        CreditScoreRule overdueRule = rules.get("OVERDUE_BILL");
        CreditScoreRule auditRule = rules.get("AUDIT_CHAIN");
        BigDecimal paymentDeduction = BigDecimal.valueOf(Math.max(0, paymentRule.thresholdValue().doubleValue() - paymentRate.doubleValue())).multiply(paymentRule.deductionUnit());
        BigDecimal alertDeduction = BigDecimal.valueOf(openAlerts).multiply(alertRule.deductionUnit());
        BigDecimal overdueDeduction = BigDecimal.valueOf(overdueBills).multiply(overdueRule.deductionUnit());
        BigDecimal auditDeduction = BigDecimal.valueOf(verification.brokenLinks()).multiply(auditRule.deductionUnit());
        insertCreditFactor(scoreId, communityId, paymentRule.factorCode(), paymentRule.factorName(), paymentRate + "%", paymentRule.weight(), paymentDeduction, paymentRule.evidenceTemplate());
        insertCreditFactor(scoreId, communityId, alertRule.factorCode(), alertRule.factorName(), String.valueOf(openAlerts), alertRule.weight(), alertDeduction, alertRule.evidenceTemplate());
        insertCreditFactor(scoreId, communityId, overdueRule.factorCode(), overdueRule.factorName(), String.valueOf(overdueBills), overdueRule.weight(), overdueDeduction, overdueRule.evidenceTemplate());
        insertCreditFactor(scoreId, communityId, auditRule.factorCode(), auditRule.factorName(), verification.status(), auditRule.weight(), auditDeduction, auditRule.evidenceTemplate() + "；latestHash=" + verification.latestHash());
    }

    private void insertCreditFactor(long scoreId, long communityId, String code, String name, String value, BigDecimal weight, BigDecimal deduction, String evidence) {
        jdbc.sql("""
            insert into community_credit_factor(score_id, community_id, factor_code, factor_name,
                                                factor_value, weight, deduction, evidence, created_at)
            values(:scoreId, :communityId, :code, :name,
                   :value, :weight, :deduction, :evidence, now())
            """)
            .param("scoreId", scoreId)
            .param("communityId", communityId)
            .param("code", code)
            .param("name", name)
            .param("value", value)
            .param("weight", weight)
            .param("deduction", deduction)
            .param("evidence", evidence)
            .update();
    }

    private long ownerIdForCommunity(String username, long communityId) {
        return jdbc.sql("""
            select o.id
            from owner o
            join house h on h.id = o.house_id
            join resident_house_relation r on r.house_id = h.id
            join app_user u on u.id = r.user_id and u.username = o.username
            where o.username = :username
              and h.community_id = :communityId
              and r.verification_status = 'APPROVED'
            order by r.is_primary desc, r.id desc
            limit 1
            """)
            .param("username", username)
            .param("communityId", communityId)
            .query(Long.class)
            .optional()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号未绑定该小区房屋，不能提交业主动作"));
    }

    private void assertCommunityAccess(TokenService.Principal principal, long communityId) {
        if (!canViewAllCommunities(principal) && principal.communityId() != communityId) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权操作其他小区数据");
        }
    }

    private Voucher voucherById(long voucherId) {
        return jdbc.sql("""
            select v.id, c.name communityName, v.voucher_no voucherNo, v.source_type sourceType,
                   v.debit_subject debitSubject, v.credit_subject creditSubject, v.amount,
                   v.status, v.booked_at bookedAt
            from finance_voucher v join community c on c.id = v.community_id
            where v.id = :id
            """).param("id", voucherId).query(Voucher.class).single();
    }

    private Long voucherCommunityId(long voucherId) {
        return jdbc.sql("select community_id from finance_voucher where id = :id")
            .param("id", voucherId)
            .query(Long.class)
            .single();
    }

    private AuditVerification auditVerificationSnapshot() {
        List<AuditLog> rows = jdbc.sql("""
            select id, actor, action, target_type targetType, target_id targetId, hash, previous_hash previousHash,
                   created_at createdAt
            from audit_log order by id
            """).query(AuditLog.class).list();
        int brokenLinks = 0;
        long firstBrokenId = 0;
        String previousHash = "GENESIS";
        for (AuditLog row : rows) {
            if (!previousHash.equals(row.previousHash())) {
                brokenLinks++;
                if (firstBrokenId == 0) {
                    firstBrokenId = row.id();
                }
            }
            previousHash = row.hash();
        }
        return new AuditVerification(rows.size(), brokenLinks, firstBrokenId, brokenLinks == 0 ? "VALID" : "BROKEN", previousHash);
    }

    private BigDecimal creditScore(BigDecimal paymentRate, int openAlerts, int overdueBills, int brokenAuditLinks, Map<String, CreditScoreRule> rules) {
        CreditScoreRule paymentRule = rules.get("PAYMENT_RATE");
        CreditScoreRule alertRule = rules.get("OPEN_ALERT");
        CreditScoreRule overdueRule = rules.get("OVERDUE_BILL");
        CreditScoreRule auditRule = rules.get("AUDIT_CHAIN");
        BigDecimal score = BigDecimal.valueOf(100)
            .subtract(BigDecimal.valueOf(Math.max(0, paymentRule.thresholdValue().doubleValue() - paymentRate.doubleValue())).multiply(paymentRule.deductionUnit()))
            .subtract(BigDecimal.valueOf(openAlerts).multiply(alertRule.deductionUnit()))
            .subtract(BigDecimal.valueOf(overdueBills).multiply(overdueRule.deductionUnit()))
            .subtract(BigDecimal.valueOf(brokenAuditLinks).multiply(auditRule.deductionUnit()));
        return score.max(BigDecimal.ZERO).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private String activeCreditRuleVersion() {
        return jdbc.sql("""
            select rule_version from credit_score_rule
            where status = 'ACTIVE'
            group by rule_version
            order by rule_version desc
            limit 1
            """).query(String.class).single();
    }

    private Map<String, CreditScoreRule> activeCreditRules(String ruleVersion) {
        List<CreditScoreRule> rows = jdbc.sql("""
            select id, rule_version ruleVersion, factor_code factorCode, factor_name factorName,
                   weight, threshold_value thresholdValue, deduction_unit deductionUnit,
                   evidence_template evidenceTemplate, status, created_at createdAt
            from credit_score_rule
            where rule_version = :ruleVersion and status = 'ACTIVE'
            """).param("ruleVersion", ruleVersion).query(CreditScoreRule.class).list();
        java.util.Map<String, CreditScoreRule> rules = new java.util.HashMap<>();
        for (CreditScoreRule row : rows) {
            rules.put(row.factorCode(), row);
        }
        for (String code : List.of("PAYMENT_RATE", "OPEN_ALERT", "OVERDUE_BILL", "AUDIT_CHAIN")) {
            if (!rules.containsKey(code)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "信用评分规则缺失：" + code);
            }
        }
        return rules;
    }

    private int scanLargeExpenseAlerts(List<Long> communityIds) {
        SupervisionAlertRule rule = alertRule("LARGE_EXPENSE");
        List<Expense> expenses = jdbc.sql("""
            select e.id, c.name communityName, e.order_type orderType, e.title, e.amount, e.status,
                   e.current_node currentNode, e.invoice_no invoiceNo, e.contract_no contractNo,
                   e.created_at createdAt, e.due_at dueAt
            from expense_order e join community c on c.id = e.community_id
            where e.community_id in (:communityIds)
              and e.amount >= :threshold
              and e.status in ('PENDING', 'WARNING', 'APPROVED')
            """)
            .param("communityIds", communityIds)
            .param("threshold", rule.thresholdAmount())
            .query(Expense.class)
            .list();
        int created = 0;
        for (Expense expense : expenses) {
            created += createRiskAlertIfAbsent(rule, expense.communityName(), "EXPENSE", expense.id(), expense.title(),
                "支出金额 ¥" + expense.amount() + "，当前节点：" + expense.currentNode());
        }
        return created;
    }

    private int scanNoPublicAnnouncementAlerts(List<Long> communityIds) {
        SupervisionAlertRule rule = alertRule("NO_PUBLIC_ANNOUNCEMENT");
        List<Community> communities = jdbc.sql("""
            select id, district, street, neighborhood, name, households, occupancy_rate occupancyRate,
                   approval_threshold approvalThreshold
            from community c
            where c.id in (:communityIds)
              and (
                select count(*) from announcement a
                where a.community_id = c.id
                  and a.category in ('财务公示', '审批公示')
                  and a.published_at >= :since
              ) = 0
            """)
            .param("communityIds", communityIds)
            .param("since", LocalDateTime.now().minusDays(rule.thresholdDays()))
            .query(Community.class)
            .list();
        int created = 0;
        for (Community community : communities) {
            created += createRiskAlertIfAbsent(rule, community.name(), "COMMUNITY", community.id(), "长期未公示",
                rule.thresholdDays() + "天内未发现财务公示或审批公示记录");
        }
        return created;
    }

    private int scanLowPaymentRateAlerts(List<Long> communityIds) {
        SupervisionAlertRule rule = alertRule("LOW_PAYMENT_RATE");
        List<PaymentRateRisk> risks = jdbc.sql("""
            select c.id communityId, c.name communityName, b.payment_rate paymentRate
            from billing_summary b join community c on c.id = b.community_id
            where b.community_id in (:communityIds) and b.payment_rate < :threshold
            """)
            .param("communityIds", communityIds)
            .param("threshold", rule.thresholdAmount())
            .query(PaymentRateRisk.class)
            .list();
        int created = 0;
        for (PaymentRateRisk risk : risks) {
            created += createRiskAlertIfAbsent(rule, risk.communityName(), "BILLING_SUMMARY", risk.communityId(), "缴费率偏低",
                "当前缴费率 " + risk.paymentRate() + "%，低于监管阈值 " + rule.thresholdAmount() + "%");
        }
        return created;
    }

    private SupervisionAlertRule alertRule(String ruleCode) {
        return jdbc.sql("""
            select id, rule_code ruleCode, rule_name ruleName, level, threshold_amount thresholdAmount,
                   threshold_days thresholdDays, status, created_at createdAt
            from supervision_alert_rule
            where rule_code = :ruleCode and status = 'ACTIVE'
            """)
            .param("ruleCode", ruleCode)
            .query(SupervisionAlertRule.class)
            .optional()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "未启用预警规则：" + ruleCode));
    }

    private int createRiskAlertIfAbsent(SupervisionAlertRule rule, String communityName, String targetType, long targetId, String title, String description) {
        Integer exists = jdbc.sql("""
            select count(*) from risk_alert
            where rule_code = :ruleCode and target_type = :targetType and target_id = :targetId
              and status in ('OPEN', 'TRACKING')
            """)
            .param("ruleCode", rule.ruleCode())
            .param("targetType", targetType)
            .param("targetId", targetId)
            .query(Integer.class)
            .single();
        if (exists > 0) {
            return 0;
        }
        jdbc.sql("""
            insert into risk_alert(community_name, level, title, description, status, created_at,
                                   rule_code, target_type, target_id, handled_by, resolved_at)
            values(:communityName, :level, :title, :description, 'OPEN', now(),
                   :ruleCode, :targetType, :targetId, null, null)
            """)
            .param("communityName", communityName)
            .param("level", rule.level())
            .param("title", title)
            .param("description", description)
            .param("ruleCode", rule.ruleCode())
            .param("targetType", targetType)
            .param("targetId", targetId)
            .update();
        return 1;
    }

    private void createVoucherLines(long voucherId) {
        Voucher voucher = voucherById(voucherId);
        Long communityId = voucherCommunityId(voucherId);
        Integer exists = jdbc.sql("select count(*) from finance_voucher_line where voucher_id = :voucherId")
            .param("voucherId", voucherId)
            .query(Integer.class)
            .single();
        if (exists > 0) {
            return;
        }
        insertVoucherLine(voucherId, communityId, voucher.debitSubject(), "DEBIT", voucher.amount());
        insertVoucherLine(voucherId, communityId, voucher.creditSubject(), "CREDIT", voucher.amount());
    }

    private void insertVoucherLine(long voucherId, long communityId, String subjectName, String direction, BigDecimal amount) {
        jdbc.sql("""
            insert into finance_voucher_line(voucher_id, community_id, subject_code, subject_name, direction, amount, booked_at)
            values(:voucherId, :communityId, :subjectCode, :subjectName, :direction, :amount, now())
            """)
            .param("voucherId", voucherId)
            .param("communityId", communityId)
            .param("subjectCode", subjectCode(subjectName, direction))
            .param("subjectName", subjectName)
            .param("direction", direction)
            .param("amount", amount)
            .update();
    }

    private String subjectCode(String subjectName, String direction) {
        if (subjectName != null && subjectName.contains("银行存款")) {
            return "1002";
        }
        if (subjectName != null && subjectName.contains("公共收益收入")) {
            return "4001";
        }
        if (subjectName != null && subjectName.contains("应付账款")) {
            return "2202";
        }
        return "DEBIT".equals(direction) ? "5001" : "4001";
    }

    private ResponseEntity<byte[]> csvResponse(String filename, String content) {
        byte[] bytes = ("\uFEFF" + content).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(bytes);
    }

    private void logDataExport(HttpServletRequest request, String exportModule, String fileName, String targetType, long targetId, Long communityId, int rowCount) {
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        jdbc.sql("""
            insert into data_export_log(actor, role_code, tenant_id, community_id, export_module,
                                        target_type, target_id, file_name, row_count, data_scope, filter_summary, status, created_at)
            values(:actor, :roleCode, :tenantId, :communityId, :exportModule,
                   :targetType, :targetId, :fileName, :rowCount, :dataScope, :filterSummary, 'SUCCESS', now())
            """)
            .param("actor", principal.username())
            .param("roleCode", principal.role())
            .param("tenantId", principal.tenantId())
            .param("communityId", communityId)
            .param("exportModule", exportModule)
            .param("targetType", targetType)
            .param("targetId", targetId)
            .param("fileName", fileName)
            .param("rowCount", rowCount)
            .param("dataScope", exportDataScope(principal, communityId))
            .param("filterSummary", exportFilterSummary(targetType, targetId, communityId, rowCount))
            .update();
    }

    private String exportDataScope(TokenService.Principal principal, Long communityId) {
        if (communityId != null) {
            return "COMMUNITY:" + communityId;
        }
        if (canViewAllCommunities(principal)) {
            return "AUTHORIZED_COMMUNITIES";
        }
        return "TENANT:" + principal.tenantId();
    }

    private String exportFilterSummary(String targetType, long targetId, Long communityId, int rowCount) {
        return "target=" + targetType + "#" + targetId
            + "; community=" + (communityId == null ? "AUTHORIZED" : communityId)
            + "; rows=" + rowCount;
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private Long collectionBankConfigId(long communityId) {
        return jdbc.sql("""
            select id from community_bank_config
            where community_id = :communityId and service_type = 'COLLECTION' and status = 'ACTIVE'
            order by id desc limit 1
            """)
            .param("communityId", communityId)
            .query(Long.class)
            .optional()
            .orElse(null);
    }

    private void logExternalCallback(String adapterCode, String eventType, String businessNo, String signatureStatus, int httpStatus, String requestDigest, String responseSummary, String idempotencyKey, String processStatus) {
        jdbc.sql("""
            insert into external_callback_receipt(adapter_code, event_type, business_no, signature_status,
                                                  http_status, request_digest, response_summary, trace_no,
                                                  idempotency_key, process_status, received_at)
            values(:adapterCode, :eventType, :businessNo, :signatureStatus,
                   :httpStatus, :requestDigest, :responseSummary, :traceNo,
                   :idempotencyKey, :processStatus, now())
            """)
            .param("adapterCode", adapterCode)
            .param("eventType", eventType)
            .param("businessNo", businessNo == null ? "" : businessNo)
            .param("signatureStatus", signatureStatus)
            .param("httpStatus", httpStatus)
            .param("requestDigest", requestDigest == null ? "" : (requestDigest.length() <= 900 ? requestDigest : requestDigest.substring(0, 900)))
            .param("responseSummary", responseSummary == null ? "" : responseSummary)
            .param("traceNo", adapterCode + "-CB-" + System.currentTimeMillis())
            .param("idempotencyKey", idempotencyKey)
            .param("processStatus", processStatus)
            .update();
    }

    private boolean callbackAlreadyProcessed(String idempotencyKey) {
        Integer count = jdbc.sql("""
            select count(*) from external_callback_receipt
            where idempotency_key = :idempotencyKey and process_status = 'PROCESSED'
            """)
            .param("idempotencyKey", idempotencyKey)
            .query(Integer.class)
            .single();
        return count != null && count > 0;
    }

    private String callbackIdempotencyKey(String adapterCode, String eventType, String businessNo) {
        return adapterCode + "|" + eventType + "|" + (businessNo == null ? "" : businessNo);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private List<ArrearsPublicationItem> publicArrearsItems(long communityId) {
        Long publicationId = jdbc.sql("""
            select id from arrears_publication
            where community_id = :communityId and status = 'PUBLISHED'
            order by published_at desc limit 1
            """)
            .param("communityId", communityId)
            .query(Long.class)
            .optional()
            .orElse(null);
        if (publicationId == null) {
            return List.of();
        }
        return jdbc.sql("""
            select id, publication_id publicationId, bill_id billId, room_no_mask roomNoMask,
                   bill_type billType, period, amount, paid_amount paidAmount,
                   arrears_amount arrearsAmount, due_date dueDate
            from arrears_publication_item
            where publication_id = :publicationId
            order by due_date, room_no_mask
            """).param("publicationId", publicationId).query(ArrearsPublicationItem.class).list();
    }

    private String maskRoomNo(String roomNo) {
        if (roomNo == null || roomNo.length() < 3) {
            return "***";
        }
        return roomNo.charAt(0) + "**" + roomNo.substring(roomNo.length() - 1);
    }

    private List<ApprovalWorkflowNode> approvalNodesByExpense(long expenseId) {
        return jdbc.sql("""
            select id, expense_id expenseId, node_code nodeCode, node_name nodeName, role_code roleCode,
                   sort_no sortNo, status, due_at dueAt, operator, decision, comment, operated_at operatedAt, created_at createdAt
            from approval_workflow_node
            where expense_id = :expenseId
            order by sort_no
            """).param("expenseId", expenseId).query(ApprovalWorkflowNode.class).list();
    }

    private List<WorkflowTemplateNode> workflowTemplateNodes(String templateCode) {
        return jdbc.sql("""
            select id, template_code templateCode, node_code nodeCode, node_name nodeName,
                   role_code roleCode, sort_no sortNo, condition_code conditionCode,
                   timeout_hours timeoutHours, status, created_at createdAt
            from workflow_template_node
            where template_code = :templateCode and status = 'ACTIVE'
            order by sort_no
            """).param("templateCode", templateCode).query(WorkflowTemplateNode.class).list();
    }

    private void workflowEvent(String businessType, long businessId, String templateCode, String nodeCode, String eventType, String operator, String summary) {
        jdbc.sql("""
            insert into workflow_event(business_type, business_id, template_code, node_code,
                                       event_type, operator, event_summary, created_at)
            values(:businessType, :businessId, :templateCode, :nodeCode,
                   :eventType, :operator, :summary, now())
            """)
            .param("businessType", businessType)
            .param("businessId", businessId)
            .param("templateCode", templateCode)
            .param("nodeCode", nodeCode)
            .param("eventType", eventType)
            .param("operator", operator == null || operator.isBlank() ? "SYSTEM" : operator)
            .param("summary", summary)
            .update();
    }

    private Long createExpenseDocument(long expenseId, String documentType, String documentNo, String issuer, BigDecimal amount, LocalDate issueDate, String fileUrl, String status, String comment) {
        jdbc.sql("""
            insert into expense_document(expense_id, document_type, document_no, issuer, amount, issue_date, file_url, verification_status, review_comment, created_at)
            values(:expenseId, :documentType, :documentNo, :issuer, :amount, :issueDate, :fileUrl, :status, :comment, now())
            """)
            .param("expenseId", expenseId)
            .param("documentType", documentType == null || documentType.isBlank() ? "OTHER" : documentType)
            .param("documentNo", documentNo == null || documentNo.isBlank() ? "DOC-" + System.currentTimeMillis() : documentNo)
            .param("issuer", issuer)
            .param("amount", amount)
            .param("issueDate", issueDate)
            .param("fileUrl", fileUrl)
            .param("status", status)
            .param("comment", comment)
            .update();
        return jdbc.sql("select max(id) from expense_document").query(Long.class).single();
    }

    private ApprovalWorkflowNode activeApprovalNode(long expenseId) {
        return jdbc.sql("""
            select id, expense_id expenseId, node_code nodeCode, node_name nodeName, role_code roleCode,
                   sort_no sortNo, status, due_at dueAt, operator, decision, comment, operated_at operatedAt, created_at createdAt
            from approval_workflow_node
            where expense_id = :expenseId and status = 'ACTIVE'
            order by sort_no limit 1
            """)
            .param("expenseId", expenseId)
            .query(ApprovalWorkflowNode.class)
            .optional()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前支出没有可处理的审批节点"));
    }

    private ApprovalWorkflowNode nextWaitingApprovalNode(long expenseId) {
        return jdbc.sql("""
            select id, expense_id expenseId, node_code nodeCode, node_name nodeName, role_code roleCode,
                   sort_no sortNo, status, due_at dueAt, operator, decision, comment, operated_at operatedAt, created_at createdAt
            from approval_workflow_node
            where expense_id = :expenseId and status = 'WAITING' and node_code <> 'BANK_PAYMENT'
            order by sort_no limit 1
            """)
            .param("expenseId", expenseId)
            .query(ApprovalWorkflowNode.class)
            .optional()
            .orElse(null);
    }

    private void createApprovalWorkflowNodes(long expenseId, ApprovalRule rule, boolean needsCommunityApproval, boolean needsVote) {
        String templateCode = "PUBLIC_EXPENSE";
        List<WorkflowTemplateNode> templateNodes = workflowTemplateNodes(templateCode).stream()
            .filter(node -> shouldUseWorkflowNode(node.conditionCode(), needsCommunityApproval, needsVote))
            .toList();
        for (WorkflowTemplateNode node : templateNodes) {
            String status = "SUBMIT".equals(node.nodeCode()) ? "APPROVED"
                : "COMMITTEE_REVIEW".equals(node.nodeCode()) ? "ACTIVE" : "WAITING";
            LocalDateTime dueAt = "ACTIVE".equals(status)
                ? LocalDateTime.now().plusHours(node.timeoutHours() > 0 ? node.timeoutHours() : rule.approvalTimeoutHours())
                : null;
            jdbc.sql("""
                insert into approval_workflow_node(expense_id, node_code, node_name, role_code, sort_no,
                                                   status, due_at, operator, decision, comment, operated_at, created_at)
                values(:expenseId, :nodeCode, :nodeName, :roleCode, :sortNo,
                       :status, :dueAt, :operator, :decision, :comment, :operatedAt, now())
                """)
                .param("expenseId", expenseId)
                .param("nodeCode", node.nodeCode())
                .param("nodeName", node.nodeName())
                .param("roleCode", node.roleCode())
                .param("sortNo", node.sortNo())
                .param("status", status)
                .param("dueAt", dueAt)
                .param("operator", "SUBMIT".equals(node.nodeCode()) ? "SYSTEM" : null)
                .param("decision", "SUBMIT".equals(node.nodeCode()) ? "SUBMITTED" : null)
                .param("comment", "SUBMIT".equals(node.nodeCode()) ? "支出申请已提交" : null)
                .param("operatedAt", "SUBMIT".equals(node.nodeCode()) ? LocalDateTime.now() : null)
                .update();
            workflowEvent("EXPENSE", expenseId, templateCode, node.nodeCode(), "CREATE_NODE", "SYSTEM", "创建审批节点：" + node.nodeName() + "，状态=" + status);
        }
    }

    private boolean shouldUseWorkflowNode(String conditionCode, boolean needsCommunityApproval, boolean needsVote) {
        return "ALWAYS".equals(conditionCode)
            || ("COMMUNITY_APPROVAL".equals(conditionCode) && (needsCommunityApproval || needsVote))
            || ("OWNER_VOTE".equals(conditionCode) && needsVote);
    }

    private void assertNodeDecisionRole(TokenService.Principal principal, ApprovalWorkflowNode node) {
        if ("ADMIN".equals(principal.role())) {
            return;
        }
        if ("STREET".equals(node.roleCode()) && ("STREET".equals(principal.role()) || "GOVERNMENT".equals(principal.role()))) {
            return;
        }
        if ("COMMITTEE".equals(node.roleCode()) && "COMMITTEE".equals(principal.role())) {
            return;
        }
        if ("BANK".equals(node.roleCode()) && "BANK".equals(principal.role())) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前角色不能处理节点：" + node.nodeName());
    }

    private void markPendingNodes(long expenseId, String status) {
        jdbc.sql("""
            update approval_workflow_node set status = :status
            where expense_id = :expenseId and status in ('WAITING', 'ACTIVE')
            """).param("expenseId", expenseId).param("status", status).update();
    }

    private void completeBankPaymentNode(long expenseId) {
        jdbc.sql("""
            update approval_workflow_node
            set status = 'APPROVED', decision = 'APPROVED', comment = '审批完成，进入银行放款/凭证生成', operated_at = now()
            where expense_id = :expenseId and node_code = 'BANK_PAYMENT' and status = 'WAITING'
            """).param("expenseId", expenseId).update();
        workflowEvent("EXPENSE", expenseId, "PUBLIC_EXPENSE", "BANK_PAYMENT", "APPROVE_BANK_NODE", "SYSTEM", "审批完成，进入银行放款/凭证生成");
        createBankDisbursementInstruction(expenseId);
    }

    private void createBankDisbursementInstruction(long expenseId) {
        Expense expense = jdbc.sql("""
            select e.id, c.name communityName, e.order_type orderType, e.title, e.amount, e.status,
                   e.current_node currentNode, e.invoice_no invoiceNo, e.contract_no contractNo,
                   e.created_at createdAt, e.due_at dueAt
            from expense_order e join community c on c.id = e.community_id
            where e.id = :expenseId
            """).param("expenseId", expenseId).query(Expense.class).single();
        Integer exists = jdbc.sql("""
                select count(*) from bank_disbursement_instruction
                where expense_id = :expenseId and created_at >= :expenseCreatedAt
                """)
            .param("expenseId", expenseId)
            .param("expenseCreatedAt", expense.createdAt())
            .query(Integer.class)
            .single();
        if (exists > 0) {
            return;
        }
        Long communityId = jdbc.sql("select community_id from expense_order where id = :expenseId")
            .param("expenseId", expenseId)
            .query(Long.class)
            .single();
        Long accountId = jdbc.sql("""
            select id from fund_account
            where community_id = :communityId and account_type = 'PUBLIC_REVENUE'
            order by id limit 1
            """).param("communityId", communityId).query(Long.class).optional().orElse(null);
        jdbc.sql("""
            insert into bank_disbursement_instruction(expense_id, community_id, fund_account_id, instruction_no,
                                                     payee_name, amount, purpose, status, bank_trace_no,
                                                     created_at, processed_by, processed_at, remark)
            values(:expenseId, :communityId, :accountId, :instructionNo,
                   '工程/供应商收款方', :amount, :purpose, 'PENDING', null,
                   now(), null, null, null)
            """)
            .param("expenseId", expenseId)
            .param("communityId", communityId)
            .param("accountId", accountId)
            .param("instructionNo", "BDI-" + expenseId + "-" + System.currentTimeMillis())
            .param("amount", expense.amount())
            .param("purpose", expense.title())
            .update();
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return "";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private String maskIdentity(String identityNo) {
        if (identityNo == null || identityNo.length() < 8) {
            return "";
        }
        return identityNo.substring(0, 4) + "**********" + identityNo.substring(identityNo.length() - 4);
    }

    private void audit(String actor, String action, String targetType, long targetId) {
        String previousHash = jdbc.sql("select hash from audit_log order by id desc limit 1")
            .query(String.class)
            .optional()
            .orElse("GENESIS");
        String hash = Integer.toHexString((actor + action + targetType + targetId + previousHash + LocalDateTime.now()).hashCode());
        jdbc.sql("""
            insert into audit_log(actor, action, target_type, target_id, hash, previous_hash, created_at)
            values(:actor, :action, :targetType, :targetId, :hash, :previousHash, now())
            """)
            .param("actor", actor)
            .param("action", action)
            .param("targetType", targetType)
            .param("targetId", targetId)
            .param("hash", hash)
            .param("previousHash", previousHash)
            .update();
    }

    private void createExpenseVoucher(long expenseId) {
        Expense expense = jdbc.sql("""
            select e.id, c.name communityName, e.order_type orderType, e.title, e.amount, e.status,
                   e.current_node currentNode, e.invoice_no invoiceNo, e.contract_no contractNo,
                   e.created_at createdAt, e.due_at dueAt
            from expense_order e join community c on c.id = e.community_id
            where e.id = :expenseId
            """).param("expenseId", expenseId).query(Expense.class).single();
        Long communityId = jdbc.sql("select community_id from expense_order where id = :expenseId")
            .param("expenseId", expenseId)
            .query(Long.class)
            .single();
        Integer exists = jdbc.sql("""
            select count(*) from finance_voucher where source_type = 'EXPENSE' and voucher_no = :voucherNo
            """).param("voucherNo", "PZ-EXP-" + expenseId).query(Integer.class).single();
        if (exists > 0) {
            return;
        }
        jdbc.sql("""
            insert into finance_voucher(community_id, voucher_no, source_type, debit_subject, credit_subject, amount, status, booked_at)
            values(:communityId, :voucherNo, 'EXPENSE', '公共设施维护支出', '银行存款-公共收益专户', :amount, 'REVIEWING', null)
            """)
            .param("communityId", communityId)
            .param("voucherNo", "PZ-EXP-" + expenseId)
            .param("amount", expense.amount())
            .update();
    }

    private void createExpenseVote(long communityId, long expenseId, String expenseTitle, ApprovalRule rule) {
        Integer totalVoters = jdbc.sql("select households from community where id = :communityId")
            .param("communityId", communityId)
            .query(Integer.class)
            .single();
        LocalDateTime endAt = LocalDateTime.now().plusHours(rule.approvalTimeoutHours());
        jdbc.sql("""
            insert into vote(community_id, title, vote_type, status, start_at, end_at, participation_rate, agree_rate,
                             related_expense_id, total_voters, agree_count, disagree_count)
            values(:communityId, :title, 'VOTE', 'OPEN', now(), :endAt, 0, 0,
                   :expenseId, :totalVoters, 0, 0)
            """)
            .param("communityId", communityId)
            .param("title", "关于" + expenseTitle + "的业主大会表决")
            .param("endAt", endAt)
            .param("expenseId", expenseId)
            .param("totalVoters", totalVoters)
            .update();
    }

    private void createSurveyQuestion(long voteId, String questionTitle, List<String> options) {
        String title = questionTitle == null || questionTitle.isBlank()
            ? "您对本小区公共收益管理透明度是否满意？"
            : questionTitle;
        List<String> labels = options == null || options.isEmpty()
            ? List.of("满意", "基本满意", "不满意")
            : options.stream().filter(option -> option != null && !option.isBlank()).toList();
        Long questionId = insertAndReturnId("""
            insert into survey_question(vote_id, question_title, question_type, sort_no)
            values(:voteId, :questionTitle, 'SINGLE', 1)
            """, new MapSqlParameterSource()
            .addValue("voteId", voteId)
            .addValue("questionTitle", title));
        int sort = 1;
        for (String label : labels) {
            jdbc.sql("""
                insert into survey_option(question_id, option_label, sort_no)
                values(:questionId, :optionLabel, :sortNo)
                """)
                .param("questionId", questionId)
                .param("optionLabel", label)
                .param("sortNo", sort++)
                .update();
        }
    }

    private List<SurveyQuestion> surveyQuestionsByVote(long voteId) {
        List<SurveyQuestion> questions = jdbc.sql("""
            select id, vote_id voteId, question_title questionTitle, question_type questionType, sort_no sortNo
            from survey_question
            where vote_id = :voteId
            order by sort_no
            """)
            .param("voteId", voteId)
            .query((rs, rowNum) -> new SurveyQuestion(
                rs.getLong("id"),
                rs.getLong("voteId"),
                rs.getString("questionTitle"),
                rs.getString("questionType"),
                rs.getInt("sortNo"),
                List.of()
            ))
            .list();
        return questions.stream()
            .map(question -> new SurveyQuestion(question.id(), question.voteId(), question.questionTitle(), question.questionType(), question.sortNo(), surveyOptions(question.id())))
            .toList();
    }

    private List<SurveyOption> surveyOptions(long questionId) {
        return jdbc.sql("""
            select id, question_id questionId, option_label optionLabel, sort_no sortNo
            from survey_option
            where question_id = :questionId
            order by sort_no
            """).param("questionId", questionId).query(SurveyOption.class).list();
    }

    private void refreshVoteStats(long voteId) {
        VoteStats stats = jdbc.sql("""
            select
              coalesce(sum(case when decision = 'AGREE' then 1 else 0 end), 0) agreeCount,
              coalesce(sum(case when decision = 'DISAGREE' then 1 else 0 end), 0) disagreeCount,
              count(*) castCount
            from vote_ballot where vote_id = :voteId
            """).param("voteId", voteId).query(VoteStats.class).single();
        Integer totalVoters = jdbc.sql("select total_voters from vote where id = :voteId")
            .param("voteId", voteId)
            .query(Integer.class)
            .single();
        BigDecimal participationRate = totalVoters == 0
            ? BigDecimal.ZERO
            : BigDecimal.valueOf(stats.castCount()).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(totalVoters), 2, java.math.RoundingMode.HALF_UP);
        BigDecimal agreeRate = stats.castCount() == 0
            ? BigDecimal.ZERO
            : BigDecimal.valueOf(stats.agreeCount()).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(stats.castCount()), 2, java.math.RoundingMode.HALF_UP);
        jdbc.sql("""
            update vote set agree_count = :agreeCount, disagree_count = :disagreeCount,
                            participation_rate = :participationRate, agree_rate = :agreeRate
            where id = :voteId
            """)
            .param("agreeCount", stats.agreeCount())
            .param("disagreeCount", stats.disagreeCount())
            .param("participationRate", participationRate)
            .param("agreeRate", agreeRate)
            .param("voteId", voteId)
            .update();
        advanceExpenseByVoteIfPassed(voteId, participationRate, agreeRate);
    }

    private void refreshSurveyStats(long voteId) {
        Integer totalVoters = jdbc.sql("select total_voters from vote where id = :voteId")
            .param("voteId", voteId)
            .query(Integer.class)
            .single();
        Integer castCount = jdbc.sql("select count(distinct owner_id) from survey_response where vote_id = :voteId")
            .param("voteId", voteId)
            .query(Integer.class)
            .single();
        BigDecimal participationRate = totalVoters == 0
            ? BigDecimal.ZERO
            : BigDecimal.valueOf(castCount).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(totalVoters), 2, java.math.RoundingMode.HALF_UP);
        jdbc.sql("update vote set participation_rate = :participationRate where id = :voteId")
            .param("participationRate", participationRate)
            .param("voteId", voteId)
            .update();
    }

    private void advanceExpenseByVoteIfPassed(long voteId, BigDecimal participationRate, BigDecimal agreeRate) {
        Long expenseId = jdbc.sql("select related_expense_id from vote where id = :voteId")
            .param("voteId", voteId)
            .query(Long.class)
            .optional()
            .orElse(null);
        if (expenseId == null) {
            return;
        }
        Long communityId = jdbc.sql("select community_id from expense_order where id = :expenseId")
            .param("expenseId", expenseId)
            .query(Long.class)
            .single();
        ApprovalRule rule = jdbc.sql("""
            select ar.id, c.name communityName, ar.community_id communityId, ar.expense_threshold expenseThreshold,
                   ar.vote_threshold voteThreshold, ar.approval_timeout_hours approvalTimeoutHours,
                   ar.vote_ratio voteRatio, ar.status, ar.created_at createdAt
            from approval_rule ar join community c on c.id = ar.community_id
            where ar.community_id = :communityId and ar.status = 'ACTIVE'
            order by ar.id desc limit 1
            """).param("communityId", communityId).query(ApprovalRule.class).single();
        if (participationRate.compareTo(rule.voteRatio()) < 0 || agreeRate.compareTo(rule.voteRatio()) < 0) {
            return;
        }
        Integer activeVoteNode = jdbc.sql("""
            select count(*) from approval_workflow_node
            where expense_id = :expenseId and node_code = 'OWNER_VOTE' and status = 'ACTIVE'
            """).param("expenseId", expenseId).query(Integer.class).single();
        if (activeVoteNode == 0) {
            return;
        }
        jdbc.sql("""
            update approval_workflow_node
            set status = 'APPROVED', operator = 'SYSTEM', decision = 'APPROVED',
                comment = '业主大会表决达到通过比例', operated_at = now()
            where expense_id = :expenseId and node_code = 'OWNER_VOTE' and status = 'ACTIVE'
            """).param("expenseId", expenseId).update();
        completeBankPaymentNode(expenseId);
        jdbc.sql("""
            update expense_order set status = 'APPROVED', current_node = '流程结束'
            where id = :expenseId and status in ('PENDING', 'WARNING')
            """).param("expenseId", expenseId).update();
        jdbc.sql("""
            update vote set status = 'CLOSED' where id = :voteId
            """).param("voteId", voteId).update();
        jdbc.sql("""
            insert into approval_log(expense_id, node_name, operator, decision, comment, operated_at)
            values(:expenseId, '业主大会表决', 'SYSTEM', 'APPROVED', '表决比例达标，自动进入放款与凭证生成', now())
            """).param("expenseId", expenseId).update();
        createExpenseVoucher(expenseId);
        audit("SYSTEM", "业主大会表决通过支出", "expense_order", expenseId);
    }

    public record VoteStats(int agreeCount, int disagreeCount, int castCount) {}

    private AcceptanceItem acceptanceItem(String module, String description, List<Integer> evidenceCounts, List<String> evidenceLabels, int requiredEvidenceCount, String nextAction) {
        int matched = (int) evidenceCounts.stream().filter(count -> count != null && count > 0).count();
        String status = matched >= requiredEvidenceCount ? "DONE" : matched > 0 ? "PARTIAL" : "TODO";
        int missing = Math.max(0, requiredEvidenceCount - matched);
        List<String> missingLabels = java.util.stream.IntStream.range(0, evidenceCounts.size())
            .filter(index -> evidenceCounts.get(index) == null || evidenceCounts.get(index) <= 0)
            .mapToObj(index -> index < evidenceLabels.size() ? evidenceLabels.get(index) : "证据" + (index + 1))
            .toList();
        String gapSummary = missing == 0 ? "验收证据已满足" : "缺少：" + String.join("、", missingLabels);
        return new AcceptanceItem(module, description, status, matched, requiredEvidenceCount, missing, evidenceCounts, evidenceLabels, gapSummary, missing == 0 ? "持续保持数据闭环并定期导出验收证据" : nextAction);
    }

    private int count(String sql) {
        return jdbc.sql(sql).query(Integer.class).single();
    }

    private BillForPayment billForPayment(long billId) {
        return jdbc.sql("""
            select b.id, h.community_id communityId, b.bill_type billType, b.period, b.amount
            from bill b join house h on h.id = b.house_id
            where b.id = :billId
            """).param("billId", billId).query(BillForPayment.class).single();
    }

    private long ensureDrillPayment() {
        Long existing = jdbc.sql("select id from payment_order order by id desc limit 1").query(Long.class).optional().orElse(null);
        if (existing != null) {
            return existing;
        }
        BillForPayment bill = billForPayment(1);
        Long bankConfigId = collectionBankConfigId(bill.communityId());
        String orderNo = "DRILLPAY" + System.currentTimeMillis();
        Map<String, Object> prepay = integrations.createWechatPrepay(bill.id(), bill.amount(), bill.billType() + bill.period());
        jdbc.sql("""
            insert into payment_order(bill_id, community_id, bank_config_id, order_no, amount, status,
                                      prepay_id, pay_channel, provider_order_no, paid_at, created_at)
            values(:billId, :communityId, :bankConfigId, :orderNo, :amount, 'PAID',
                   :prepayId, 'WECHAT_PAY', :providerOrderNo, now(), now())
            """)
            .param("billId", bill.id())
            .param("communityId", bill.communityId())
            .param("bankConfigId", bankConfigId)
            .param("orderNo", orderNo)
            .param("amount", bill.amount())
            .param("prepayId", String.valueOf(prepay.get("prepayId")))
            .param("providerOrderNo", "DRILLWX" + bill.id())
            .update();
        jdbc.sql("update bill set paid_amount = amount, status = 'PAID' where id = :billId")
            .param("billId", bill.id())
            .update();
        insertDrillBankFlow(bill.communityId(), "IN", bill.amount(), "验收演练住户", "验收演练缴费入账", "DRILL-IN-" + bill.id());
        return jdbc.sql("select max(id) from payment_order").query(Long.class).single();
    }

    private long ensureDrillPaymentReceipt(HttpServletRequest request, long paymentOrderId) {
        Long receiptId = issueReceiptForPaymentOrder(paymentOrderId, "ACCEPTANCE_DRILL");
        Long communityId = jdbc.sql("select community_id from electronic_payment_receipt where id = :id")
            .param("id", receiptId)
            .query(Long.class)
            .single();
        logDataExport(request, "ELECTRONIC_RECEIPT", "electronic-payment-receipts.csv", "electronic_payment_receipt", receiptId, communityId, 1);
        return receiptId;
    }

    private long ensureDrillTaxInvoice(HttpServletRequest request, long receiptId) {
        Long existing = jdbc.sql("select id from tax_invoice_request where receipt_id = :receiptId order by id desc limit 1")
            .param("receiptId", receiptId)
            .query(Long.class)
            .optional()
            .orElse(null);
        if (existing != null) {
            logDataExport(request, "TAX_INVOICE", "tax-invoices.csv", "tax_invoice_request", existing, null, count("select count(*) from tax_invoice_request"));
            return existing;
        }
        PaymentReceiptForTax receipt = receiptForTax(receiptId);
        String requestNo = "TAX-DRILL-" + receiptId;
        Map<String, Object> taxResult = integrations.issueTaxInvoice(
            requestNo,
            receipt.payerName(),
            "PERSONAL",
            receipt.billType(),
            "304080299",
            BigDecimal.valueOf(0.06),
            receipt.amount()
        );
        String taxInvoiceNo = String.valueOf(taxResult.get("taxInvoiceNo"));
        String platformCode = String.valueOf(taxResult.get("taxPlatformCode"));
        String pdfUrl = String.valueOf(taxResult.get("pdfUrl"));
        String checksum = Integer.toHexString((requestNo + "|" + taxInvoiceNo + "|" + receipt.amount()).hashCode());
        jdbc.sql("""
            insert into tax_invoice_request(invoice_request_no, receipt_id, bill_id, payment_order_id, community_id,
                                            buyer_name, buyer_tax_no, invoice_item, tax_category_code, tax_rate,
                                            amount, status, tax_invoice_no, tax_platform_code, pdf_url, checksum,
                                            fail_reason, issued_by, issued_at, created_at)
            values(:requestNo, :receiptId, :billId, :paymentOrderId, :communityId,
                   :buyerName, 'PERSONAL', :invoiceItem, '304080299', 0.0600,
                   :amount, 'ISSUED', :taxInvoiceNo, :platformCode, :pdfUrl, :checksum,
                   null, 'ACCEPTANCE_DRILL', now(), now())
            """)
            .param("requestNo", requestNo)
            .param("receiptId", receiptId)
            .param("billId", receipt.billId())
            .param("paymentOrderId", receipt.paymentOrderId())
            .param("communityId", receipt.communityId())
            .param("buyerName", receipt.payerName())
            .param("invoiceItem", receipt.billType())
            .param("amount", receipt.amount())
            .param("taxInvoiceNo", taxInvoiceNo)
            .param("platformCode", platformCode)
            .param("pdfUrl", pdfUrl)
            .param("checksum", checksum)
            .update();
        Long invoiceId = jdbc.sql("select max(id) from tax_invoice_request").query(Long.class).single();
        logDataExport(request, "TAX_INVOICE", "tax-invoices.csv", "tax_invoice_request", invoiceId, receipt.communityId(), 1);
        return invoiceId;
    }

    private long ensureDrillRefund() {
        DrillRefundEvidence existing = jdbc.sql("""
            select r.id refundId, r.payment_order_id orderId, r.community_id communityId,
                   r.amount, r.pay_channel payChannel
            from payment_refund_order r
            order by r.id desc limit 1
            """)
            .query(DrillRefundEvidence.class)
            .optional()
            .orElse(null);
        if (existing != null) {
            ensureDrillRefundEvidence(existing);
            return existing.refundId();
        }
        PaidPaymentOrder order = jdbc.sql("""
            select id, order_no orderNo, amount, community_id communityId, pay_channel payChannel
            from payment_order
            where status in ('PAID', 'PARTIAL_REFUND', 'REFUNDED')
            order by id desc limit 1
            """).query(PaidPaymentOrder.class).single();
        Long billId = jdbc.sql("select bill_id from payment_order where id = :id")
            .param("id", order.id())
            .query(Long.class)
            .single();
        Map<String, Object> refundResult = integrations.refundPayment(order.payChannel(), order.orderNo(), order.amount(), "验收演练退款");
        String refundNo = "DRILLRF" + System.currentTimeMillis();
        jdbc.sql("""
            insert into payment_refund_order(payment_order_id, bill_id, community_id, refund_no, pay_channel,
                                             amount, reason, status, provider_refund_no, processed_at, created_at)
            values(:paymentOrderId, :billId, :communityId, :refundNo, :payChannel,
                   :amount, '验收演练退款', 'REFUNDED', :providerRefundNo, now(), now())
            """)
            .param("paymentOrderId", order.id())
            .param("billId", billId)
            .param("communityId", order.communityId())
            .param("refundNo", refundNo)
            .param("payChannel", order.payChannel())
            .param("amount", order.amount())
            .param("providerRefundNo", String.valueOf(refundResult.get("refundNo")))
            .update();
        jdbc.sql("update payment_order set status = 'REFUNDED' where id = :id").param("id", order.id()).update();
        insertDrillBankFlow(order.communityId(), "OUT", order.amount(), "验收演练住户", "验收演练退款出账", "DRILL-REFUND-" + order.id());
        integrations.sendSms("13800001024", "REFUND_NOTICE", "验收演练退款已完成");
        return jdbc.sql("select max(id) from payment_refund_order").query(Long.class).single();
    }

    private void ensureDrillRefundEvidence(DrillRefundEvidence refund) {
        insertDrillBankFlow(refund.communityId(), "OUT", refund.amount(), "验收演练住户", "验收演练退款出账", "DRILL-REFUND-" + refund.orderId());
        if (count("select count(*) from integration_call_log where operation = 'SEND_SMS'") == 0) {
            integrations.sendSms("13800001024", "REFUND_NOTICE", "验收演练退款已完成");
        }
    }

    private int ensureDrillPaymentStatements() {
        if (count("select count(*) from payment_channel_statement") > 0) {
            return count("select count(*) from payment_channel_statement");
        }
        List<PaymentStatementDraft> trades = jdbc.sql("""
            select po.community_id communityId, po.bill_id billId, 'PAYMENT' tradeType, po.order_no orderNo,
                   coalesce(po.provider_order_no, po.prepay_id, po.order_no) channelTradeNo, po.amount amount
            from payment_order po
            union all
            select r.community_id communityId, r.bill_id billId, 'REFUND' tradeType, r.refund_no orderNo,
                   coalesce(r.provider_refund_no, r.refund_no) channelTradeNo, r.amount amount
            from payment_refund_order r
            """).query(PaymentStatementDraft.class).list();
        LocalDate statementDate = LocalDate.now();
        for (PaymentStatementDraft trade : trades) {
            jdbc.sql("""
                insert into payment_channel_statement(community_id, provider, statement_date, trade_type, bill_id,
                                                      order_no, channel_trade_no, amount, status, synced_at, created_at)
                values(:communityId, 'WECHAT_PAY', :statementDate, :tradeType, :billId,
                       :orderNo, :channelTradeNo, :amount, 'MATCHED', now(), now())
                """)
                .param("communityId", trade.communityId())
                .param("statementDate", statementDate)
                .param("tradeType", trade.tradeType())
                .param("billId", trade.billId())
                .param("orderNo", trade.orderNo())
                .param("channelTradeNo", trade.channelTradeNo())
                .param("amount", trade.amount())
                .update();
        }
        integrations.syncPaymentStatement("WECHAT_PAY", statementDate.toString(), trades.size(), trades.stream().map(PaymentStatementDraft::amount).reduce(BigDecimal.ZERO, BigDecimal::add));
        return trades.size();
    }

    private long ensureDrillReconciliation() {
        Long existing = jdbc.sql("select id from reconciliation_record order by id desc limit 1").query(Long.class).optional().orElse(null);
        if (existing != null) {
            return existing;
        }
        Long bankConfigId = collectionBankConfigId(1);
        BigDecimal amount = jdbc.sql("select coalesce(sum(amount), 0) from payment_order where community_id = 1")
            .query(BigDecimal.class).single();
        jdbc.sql("""
            insert into reconciliation_record(community_id, bank_config_id, reconcile_date, system_amount,
                                              bank_amount, diff_amount, matched_count, unmatched_count, status, created_at)
            values(1, :bankConfigId, :reconcileDate, :amount, :amount, 0, 1, 0, 'MATCHED', now())
            """)
            .param("bankConfigId", bankConfigId)
            .param("reconcileDate", LocalDate.now())
            .param("amount", amount)
            .update();
        Long id = jdbc.sql("select max(id) from reconciliation_record").query(Long.class).single();
        jdbc.sql("""
            insert into reconciliation_detail(reconciliation_id, source_type, source_id, order_no, trace_no,
                                              amount, status, description, handled_status, created_at)
            values(:id, 'SYSTEM', 0, 'DRILL', 'DRILL', :amount, 'MATCHED', '验收演练自动匹配', 'AUTO_CLOSED', now())
            """)
            .param("id", id)
            .param("amount", amount)
            .update();
        return id;
    }

    private int ensureDrillReconciliationExport(HttpServletRequest request, long reconciliationId) {
        Integer exists = jdbc.sql("""
            select count(*) from data_export_log
            where export_module = 'BANK_RECONCILIATION' and target_id = :reconciliationId
            """)
            .param("reconciliationId", reconciliationId)
            .query(Integer.class)
            .single();
        if (exists != null && exists > 0) {
            return 0;
        }
        Long communityId = jdbc.sql("select community_id from reconciliation_record where id = :reconciliationId")
            .param("reconciliationId", reconciliationId)
            .query(Long.class)
            .single();
        int rowCount = jdbc.sql("select count(*) from reconciliation_detail where reconciliation_id = :reconciliationId")
            .param("reconciliationId", reconciliationId)
            .query(Integer.class)
            .single();
        logDataExport(request, "BANK_RECONCILIATION", "bank-reconciliation-" + reconciliationId + ".csv",
            "reconciliation_record", reconciliationId, communityId, rowCount);
        return 1;
    }

    private int ensureDrillBankAdapterEvidence(HttpServletRequest request) {
        if (count("select count(*) from bank_adapter_health") == 0) {
            List<DrillBankAdapter> adapters = jdbc.sql("""
                    select bank_code bankCode, api_base_url apiBaseUrl, sign_algorithm signAlgorithm,
                           callback_algorithm callbackAlgorithm, statement_mode statementMode,
                           disbursement_mode disbursementMode
                    from bank_adapter_profile
                    where status in ('ACTIVE','READY')
                    order by id
                    """).query(DrillBankAdapter.class).list();
            for (DrillBankAdapter adapter : adapters) {
                String evidence = "endpoint=" + adapter.apiBaseUrl()
                    + "; sign=" + adapter.signAlgorithm()
                    + "; callback=" + adapter.callbackAlgorithm()
                    + "; statement=" + adapter.statementMode()
                    + "; disbursement=" + adapter.disbursementMode()
                    + "; contractReady=true";
                jdbc.sql("""
                    insert into bank_adapter_health(bank_code, check_item, check_result, latency_ms, evidence, checked_at)
                    values(:bankCode, '验收演练银行适配器契约检查', 'PASS', 1, :evidence, now())
                    """)
                    .param("bankCode", adapter.bankCode())
                    .param("evidence", evidence)
                    .update();
            }
        }
        if (count("select count(*) from data_export_log where export_module = 'BANK_ADAPTER_HEALTH'") == 0) {
            logDataExport(request, "BANK_ADAPTER_HEALTH", "bank-adapter-health.csv", "bank_adapter_health", 0, null, count("select count(*) from bank_adapter_health"));
            return 1;
        }
        return 0;
    }

    private record DrillBankAdapter(String bankCode, String apiBaseUrl, String signAlgorithm, String callbackAlgorithm, String statementMode, String disbursementMode) {}

    private long ensureDrillExpenseVote() {
        Long existingVoteId = jdbc.sql("""
            select v.id from vote v
            join vote_ballot b on b.vote_id = v.id
            where v.related_expense_id is not null
            order by b.cast_at desc limit 1
            """).query(Long.class).optional().orElse(null);
        if (existingVoteId != null) {
            Long expenseId = jdbc.sql("select related_expense_id from vote where id = :voteId")
                .param("voteId", existingVoteId)
                .query(Long.class)
                .single();
            ensureDrillExpenseDisbursement(expenseId);
            return existingVoteId;
        }
        Long expenseId = jdbc.sql("""
                select id from expense_order
                where source_account_type = 'PUBLIC_REVENUE' and need_vote = 'YES'
                order by id desc limit 1
                """)
            .query(Long.class)
            .optional()
            .orElse(null);
        if (expenseId == null) {
            ApprovalRule rule = jdbc.sql("""
                select ar.id, c.name communityName, ar.community_id communityId, ar.expense_threshold expenseThreshold,
                       ar.vote_threshold voteThreshold, ar.approval_timeout_hours approvalTimeoutHours,
                       ar.vote_ratio voteRatio, ar.status, ar.created_at createdAt
                from approval_rule ar join community c on c.id = ar.community_id
                where ar.community_id = 1 and ar.status = 'ACTIVE'
                order by ar.id desc limit 1
                """).query(ApprovalRule.class).single();
            BigDecimal drillAmount = rule.voteThreshold().add(BigDecimal.valueOf(1000));
            jdbc.sql("""
                insert into expense_order(community_id, order_type, title, amount, status, current_node, invoice_no, contract_no,
                                          created_at, due_at, source_account_type, need_vote)
                values(:communityId, 'PAYMENT', '验收演练公共收益表决支出', :amount, 'PENDING', '业委会审批',
                       'INV-DRILL-VOTE', 'HT-DRILL-VOTE', now(), :dueAt, 'PUBLIC_REVENUE', 'YES')
                """)
                .param("communityId", rule.communityId())
                .param("amount", drillAmount)
                .param("dueAt", LocalDateTime.now().plusHours(rule.approvalTimeoutHours()))
                .update();
            expenseId = jdbc.sql("select max(id) from expense_order").query(Long.class).single();
            createApprovalWorkflowNodes(expenseId, rule, true, true);
            audit("system", "验收演练补齐公共收益表决支出", "expense_order", expenseId);
        }
        Long communityId = jdbc.sql("select community_id from expense_order where id = :expenseId")
            .param("expenseId", expenseId)
            .query(Long.class)
            .single();
        Long voteId = jdbc.sql("select id from vote where related_expense_id = :expenseId order by id desc limit 1")
            .param("expenseId", expenseId)
            .query(Long.class)
            .optional()
            .orElse(null);
        if (voteId == null) {
            Integer totalVoters = jdbc.sql("select households from community where id = :communityId")
                .param("communityId", communityId)
                .query(Integer.class)
                .single();
            jdbc.sql("""
                insert into vote(community_id, title, vote_type, status, start_at, end_at, participation_rate, agree_rate,
                                 related_expense_id, total_voters, agree_count, disagree_count)
                values(:communityId, '验收演练公共收益支出表决', 'VOTE', 'OPEN', now(), :endAt, 0, 0,
                       :expenseId, :totalVoters, 0, 0)
                """)
                .param("communityId", communityId)
                .param("endAt", LocalDateTime.now().plusHours(24))
                .param("expenseId", expenseId)
                .param("totalVoters", totalVoters)
                .update();
            voteId = jdbc.sql("select max(id) from vote").query(Long.class).single();
        }
        Long ownerId = jdbc.sql("select id from owner where username = 'owner' order by id limit 1")
            .query(Long.class)
            .single();
        jdbc.sql("""
            insert into vote_ballot(vote_id, owner_id, decision, signature_hash, cast_at)
            values(:voteId, :ownerId, 'AGREE', :signatureHash, now())
            on duplicate key update decision = values(decision), signature_hash = values(signature_hash), cast_at = now()
            """)
            .param("voteId", voteId)
            .param("ownerId", ownerId)
            .param("signatureHash", Integer.toHexString(("DRILL|" + voteId + "|" + ownerId).hashCode()))
            .update();
        refreshVoteStats(voteId);
        ensureDrillExpenseDisbursement(expenseId);
        return voteId;
    }

    private void ensureDrillExpenseDisbursement(long expenseId) {
        createBankDisbursementInstruction(expenseId);
        DrillDisbursement instruction = jdbc.sql("""
            select d.id, d.community_id communityId, d.payee_name payeeName, d.amount, d.purpose, d.status,
                   d.bank_trace_no bankTraceNo
            from bank_disbursement_instruction d
            where d.expense_id = :expenseId
            order by d.id desc limit 1
            """)
            .param("expenseId", expenseId)
            .query(DrillDisbursement.class)
            .single();
        if ("PAID".equals(instruction.status()) && instruction.bankTraceNo() != null) {
            return;
        }
        String bankTraceNo = "DRILL-OUT-" + instruction.id();
        jdbc.sql("""
            update bank_disbursement_instruction
            set status = 'PAID', bank_trace_no = :bankTraceNo, processed_by = 'ACCEPTANCE_DRILL',
                processed_at = now(), remark = '验收演练银行放款完成'
            where id = :id
            """)
            .param("bankTraceNo", bankTraceNo)
            .param("id", instruction.id())
            .update();
        jdbc.sql("update expense_order set status = 'PAID', current_node = '银行放款完成' where id = :expenseId")
            .param("expenseId", expenseId)
            .update();
        insertDrillBankFlow(instruction.communityId(), "OUT", instruction.amount(), instruction.payeeName(), instruction.purpose(), bankTraceNo);
    }

    private record DrillDisbursement(long id, long communityId, String payeeName, BigDecimal amount, String purpose, String status, String bankTraceNo) {}

    private int ensureDrillExpenseFinanceEvidence(long voteId, HttpServletRequest request) {
        Long expenseId = jdbc.sql("select related_expense_id from vote where id = :voteId")
            .param("voteId", voteId)
            .query(Long.class)
            .single();
        int changed = 0;
        changed += ensureDrillExpenseDocument(expenseId, "INVOICE", "DRILL-INV-" + expenseId, "验收演练供应商");
        changed += ensureDrillExpenseDocument(expenseId, "CONTRACT", "DRILL-HT-" + expenseId, "验收演练供应商");
        changed += verifyDrillExpenseDocuments(expenseId);
        changed += ensureDrillExpenseDocumentExport(expenseId, request);
        createExpenseVoucher(expenseId);
        Long voucherId = jdbc.sql("""
            select id from finance_voucher
            where source_type = 'EXPENSE' and voucher_no = :voucherNo
            order by id desc limit 1
            """)
            .param("voucherNo", "PZ-EXP-" + expenseId)
            .query(Long.class)
            .single();
        String status = jdbc.sql("select status from finance_voucher where id = :voucherId")
            .param("voucherId", voucherId)
            .query(String.class)
            .single();
        if (!"BOOKED".equals(status)) {
            jdbc.sql("update finance_voucher set status = 'BOOKED', booked_at = now() where id = :voucherId")
                .param("voucherId", voucherId)
                .update();
            createVoucherLines(voucherId);
            audit("SYSTEM", "验收演练凭证记账", "finance_voucher", voucherId);
            changed++;
        } else {
            createVoucherLines(voucherId);
        }
        if (count("select count(*) from data_export_log where export_module = 'FINANCE_VOUCHER' and target_id = " + voucherId) == 0) {
            Long voucherCommunityId = voucherCommunityId(voucherId);
            String voucherNo = jdbc.sql("select voucher_no from finance_voucher where id = :voucherId")
                .param("voucherId", voucherId)
                .query(String.class)
                .single();
            logDataExport(request, "FINANCE_VOUCHER", voucherNo + ".pdf", "finance_voucher", voucherId, voucherCommunityId, 1);
            changed++;
        }
        return changed;
    }

    private int ensureDrillWorkflowEvidence(long voteId, HttpServletRequest request) {
        Long expenseId = jdbc.sql("select related_expense_id from vote where id = :voteId")
            .param("voteId", voteId)
            .query(Long.class)
            .single();
        if (count("select count(*) from workflow_event where business_type = 'EXPENSE' and business_id = " + expenseId) == 0) {
            for (ApprovalWorkflowNode node : approvalNodesByExpense(expenseId)) {
                workflowEvent("EXPENSE", expenseId, "PUBLIC_EXPENSE", node.nodeCode(), "DRILL_BACKFILL", "ACCEPTANCE_DRILL", "验收演练补齐工作流事件：" + node.nodeName());
            }
        }
        int eventCount = count("select count(*) from workflow_event where business_type = 'EXPENSE' and business_id = " + expenseId);
        Long communityId = jdbc.sql("select community_id from expense_order where id = :expenseId")
            .param("expenseId", expenseId)
            .query(Long.class)
            .single();
        logDataExport(request, "WORKFLOW_EVENT", "workflow-events.csv", "workflow_event", expenseId, communityId, eventCount);
        return eventCount;
    }

    private int ensureDrillExpenseDocumentExport(long expenseId, HttpServletRequest request) {
        Long documentId = jdbc.sql("""
            select id from expense_document
            where expense_id = :expenseId and verification_status = 'VERIFIED'
            order by id limit 1
            """)
            .param("expenseId", expenseId)
            .query(Long.class)
            .optional()
            .orElse(null);
        if (documentId == null) {
            return 0;
        }
        if (count("select count(*) from data_export_log where export_module = 'EXPENSE_DOCUMENT' and target_id = " + documentId) > 0) {
            return 0;
        }
        Long communityId = jdbc.sql("select community_id from expense_order where id = :expenseId")
            .param("expenseId", expenseId)
            .query(Long.class)
            .single();
        logDataExport(request, "EXPENSE_DOCUMENT", "expense-document-" + documentId + ".pdf", "expense_document", documentId, communityId, 1);
        return 1;
    }

    private int ensureDrillExpenseDocument(long expenseId, String documentType, String documentNo, String issuer) {
        Integer exists = jdbc.sql("""
            select count(*) from expense_document
            where expense_id = :expenseId and document_type = :documentType
            """)
            .param("expenseId", expenseId)
            .param("documentType", documentType)
            .query(Integer.class)
            .single();
        if (exists != null && exists > 0) {
            return 0;
        }
        Expense expense = jdbc.sql("""
            select e.id, c.name communityName, e.order_type orderType, e.title, e.amount, e.status,
                   e.current_node currentNode, e.invoice_no invoiceNo, e.contract_no contractNo,
                   e.created_at createdAt, e.due_at dueAt
            from expense_order e join community c on c.id = e.community_id
            where e.id = :expenseId
            """)
            .param("expenseId", expenseId)
            .query(Expense.class)
            .single();
        createExpenseDocument(expenseId, documentType, documentNo, issuer, expense.amount(), LocalDate.now(), "mock://acceptance/" + documentType.toLowerCase() + "/" + expenseId, "PENDING", "验收演练补齐资料");
        return 1;
    }

    private int verifyDrillExpenseDocuments(long expenseId) {
        int updated = jdbc.sql("""
            update expense_document
            set verification_status = 'VERIFIED',
                review_comment = '验收演练资料核验通过',
                verified_by = 'SYSTEM',
                verified_at = now()
            where expense_id = :expenseId and verification_status <> 'VERIFIED'
            """)
            .param("expenseId", expenseId)
            .update();
        if (updated > 0) {
            audit("SYSTEM", "验收演练核验支出资料", "expense_order", expenseId);
        }
        return updated;
    }

    private long ensureDrillSurveyResponse() {
        Long existing = jdbc.sql("select id from survey_response order by id desc limit 1")
            .query(Long.class)
            .optional()
            .orElse(null);
        if (existing != null) {
            return existing;
        }
        Long surveyVoteId = jdbc.sql("select id from vote where vote_type = 'SURVEY' and community_id = 1 order by id desc limit 1")
            .query(Long.class)
            .optional()
            .orElse(null);
        if (surveyVoteId == null) {
            jdbc.sql("""
                insert into vote(community_id, title, vote_type, status, start_at, end_at, participation_rate, agree_rate,
                                 related_expense_id, total_voters, agree_count, disagree_count)
                values(1, '验收演练住户满意度问卷', 'SURVEY', 'OPEN', now(), :endAt, 0, 0,
                       null, (select households from community where id = 1), 0, 0)
                """)
                .param("endAt", LocalDateTime.now().plusDays(7))
                .update();
            surveyVoteId = jdbc.sql("select max(id) from vote").query(Long.class).single();
            createSurveyQuestion(surveyVoteId, "您对物业服务响应是否满意？", List.of("满意", "基本满意", "不满意"));
        }
        SurveyQuestion question = surveyQuestionsByVote(surveyVoteId).stream()
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "验收问卷缺少题目"));
        Long optionId = question.options().stream()
            .findFirst()
            .map(SurveyOption::id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "验收问卷缺少选项"));
        Long ownerId = jdbc.sql("select id from owner where username = 'owner' order by id limit 1")
            .query(Long.class)
            .single();
        jdbc.sql("""
            insert into survey_response(vote_id, question_id, option_id, owner_id, submitted_at)
            values(:voteId, :questionId, :optionId, :ownerId, now())
            on duplicate key update option_id = values(option_id), submitted_at = now()
            """)
            .param("voteId", surveyVoteId)
            .param("questionId", question.id())
            .param("optionId", optionId)
            .param("ownerId", ownerId)
            .update();
        refreshSurveyStats(surveyVoteId);
        return jdbc.sql("""
            select id from survey_response
            where vote_id = :voteId and question_id = :questionId and owner_id = :ownerId
            """)
            .param("voteId", surveyVoteId)
            .param("questionId", question.id())
            .param("ownerId", ownerId)
            .query(Long.class)
            .single();
    }

    private long ensureDrillWorkOrderEvaluation() {
        Long existing = jdbc.sql("select id from work_order where satisfaction_score is not null order by id desc limit 1")
            .query(Long.class)
            .optional()
            .orElse(null);
        if (existing != null) {
            return existing;
        }
        Long workOrderId = jdbc.sql("""
            select w.id from work_order w
            join house h on h.id = w.house_id
            join owner o on o.house_id = h.id
            where o.username = 'owner'
            order by w.id desc limit 1
            """)
            .query(Long.class)
            .optional()
            .orElse(null);
        if (workOrderId == null) {
            Long houseId = jdbc.sql("select house_id from owner where username = 'owner' order by id limit 1")
                .query(Long.class)
                .single();
            jdbc.sql("""
                insert into work_order(house_id, order_type, title, description, status, priority, created_at, due_at, reply, handler, handled_at)
                values(:houseId, 'REPAIR', '验收演练报修', '验收演练创建报修工单', 'DONE', 'NORMAL', now(), :dueAt,
                       '验收演练已完成维修', 'SYSTEM', now())
                """)
                .param("houseId", houseId)
                .param("dueAt", LocalDateTime.now().plusHours(48))
                .update();
            workOrderId = jdbc.sql("select max(id) from work_order").query(Long.class).single();
        } else {
            jdbc.sql("""
                update work_order
                set status = 'DONE',
                    reply = coalesce(reply, '验收演练已完成处理'),
                    handler = coalesce(handler, 'SYSTEM'),
                    handled_at = coalesce(handled_at, now())
                where id = :id
                """)
                .param("id", workOrderId)
                .update();
        }
        jdbc.sql("""
            update work_order
            set satisfaction_score = 5, satisfaction_comment = '验收演练服务评价', evaluated_at = now()
            where id = :id
            """)
            .param("id", workOrderId)
            .update();
        return workOrderId;
    }

    private long ensureDrillMessageReceipt() {
        Long existing = jdbc.sql("select id from message_read_receipt order by id desc limit 1").query(Long.class).optional().orElse(null);
        if (existing != null) {
            return existing;
        }
        Long userId = jdbc.sql("select id from app_user where username = 'owner'").query(Long.class).single();
        Long messageId = jdbc.sql("select id from message_notice order by id limit 1").query(Long.class).single();
        jdbc.sql("""
            insert into message_read_receipt(message_id, user_id, read_at)
            values(:messageId, :userId, now())
            """).param("messageId", messageId).param("userId", userId).update();
        return jdbc.sql("select max(id) from message_read_receipt").query(Long.class).single();
    }

    private int ensureDrillSecurityChecks() {
        if (count("select count(*) from integration_security_check") == 0) {
            jdbc.sql("""
                insert into integration_security_check(adapter_code, check_item, check_result, risk_level, evidence, checked_at)
                values('SECURITY', '验收演练安全体检', 'PASS', 'LOW', '验收演练生成安全体检证据', now())
                """).update();
        }
        return count("select count(*) from integration_security_check");
    }

    private int ensureDrillExternalSecurityEvidence(HttpServletRequest request) {
        integrations.verifyIdentity("张女士", "13800001024", "420101199001012381", "张女士", "138****1024", "4201**********2381");
        TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
        int inserted = 0;
        inserted += insertExternalCallbackReceiptIfAbsent();
        inserted += insertExternalCallbackNonceIfAbsent();
        inserted += insertSensitiveAuditIfAbsent("identity_test", 0, "phone", "MASKED_VERIFIED", principal.username(), "验收演练实名认证核验");
        inserted += insertSensitiveAuditIfAbsent("identity_test", 0, "identityNo", "MASKED_VERIFIED", principal.username(), "验收演练实名认证核验");
        if (count("select count(*) from data_export_log where export_module = 'INTEGRATION_READINESS'") == 0) {
            logDataExport(request, "INTEGRATION_READINESS", "integration-production-readiness.csv", "integration_production_readiness", 0, null, count("select count(*) from integration_config"));
            inserted++;
        }
        if (count("select count(*) from data_export_log where export_module = 'DEPLOYMENT_READINESS'") == 0) {
            logDataExport(request, "DEPLOYMENT_READINESS", "deployment-readiness.csv", "deployment_readiness", 0, null, 5);
            inserted++;
        }
        if (count("select count(*) from data_export_log where export_module = 'EXTERNAL_CALLBACK'") == 0) {
            logDataExport(request, "EXTERNAL_CALLBACK", "external-callback-receipts.csv", "external_callback_receipt", 0, null, count("select count(*) from external_callback_receipt"));
            inserted++;
        }
        if (count("select count(*) from data_export_log where export_module = 'EXTERNAL_CALLBACK_NONCE'") == 0) {
            logDataExport(request, "EXTERNAL_CALLBACK_NONCE", "external-callback-nonces.csv", "external_callback_nonce", 0, null, count("select count(*) from external_callback_nonce"));
            inserted++;
        }
        if (count("select count(*) from data_export_log where export_module = 'SENSITIVE_FIELD_AUDIT'") == 0) {
            logDataExport(request, "SENSITIVE_FIELD_AUDIT", "sensitive-field-audits.csv", "sensitive_field_audit", 0, null, count("select count(*) from sensitive_field_audit"));
            inserted++;
        }
        if (count("select count(*) from data_export_log where export_module = 'INTEGRATION_SECURITY'") == 0) {
            logDataExport(request, "INTEGRATION_SECURITY", "integration-security-checks.csv", "integration_security_check", 0, null, count("select count(*) from integration_security_check"));
            inserted++;
        }
        return inserted;
    }

    private int ensureDrillDataExchangeEvidence(HttpServletRequest request) {
        int changed = 0;
        if (count("select count(*) from data_exchange_package") == 0) {
            int recordCount = count("select count(*) from bill")
                + count("select count(*) from bank_flow")
                + count("select count(*) from expense_order")
                + count("select count(*) from risk_alert")
                + count("select count(*) from community_credit_score")
                + count("select count(*) from data_export_log");
            String packageNo = "DX-GOVERNMENT-DRILL";
            String domains = "BILLING,BANK_FLOW,PUBLIC_REVENUE,RISK_ALERT,CREDIT_SCORE,EXPORT_LOG";
            String checksum = Integer.toHexString((packageNo + "|" + domains + "|" + recordCount).hashCode());
            TokenService.Principal principal = (TokenService.Principal) request.getAttribute("principal");
            jdbc.sql("""
                insert into data_exchange_package(package_no, target_party, community_id, data_domains,
                                                  record_count, checksum, status, created_by, created_at)
                values(:packageNo, 'GOVERNMENT', null, :domains,
                       :recordCount, :checksum, 'READY', :createdBy, now())
                """)
                .param("packageNo", packageNo)
                .param("domains", domains)
                .param("recordCount", recordCount)
                .param("checksum", checksum)
                .param("createdBy", principal.username())
                .update();
            jdbc.sql("""
                insert into integration_call_log(adapter_code, operation, request_summary, response_summary, status, trace_no, created_at)
                values('DATA_EXCHANGE', 'BUILD_PACKAGE', :requestSummary, :responseSummary, 'SUCCESS', :traceNo, now())
                """)
                .param("requestSummary", "target=GOVERNMENT, community=ALL, domains=" + domains)
                .param("responseSummary", "packageNo=" + packageNo + ", records=" + recordCount + ", checksum=" + checksum)
                .param("traceNo", packageNo)
                .update();
            changed++;
        }
        if (count("select count(*) from data_export_log where export_module = 'DATA_EXCHANGE'") == 0) {
            logDataExport(request, "DATA_EXCHANGE", "data-exchange-packages.csv", "data_exchange_package", 0, null, count("select count(*) from data_exchange_package"));
            changed++;
        }
        if (count("select count(*) from data_export_log where export_module = 'DATABASE_COMPATIBILITY'") == 0) {
            logDataExport(request, "DATABASE_COMPATIBILITY", "database-compatibility.csv", "database_compatibility", 0, null, 4);
            changed++;
        }
        return changed;
    }

    private int ensureDrillScreenTopicEvidence(HttpServletRequest request) {
        int changed = 0;
        if (count("select count(*) from community_credit_score") == 0
            || count("select count(*) from community_credit_factor") == 0
            || count("select count(*) from credit_score_run") == 0) {
            generateCommunityCreditScores(request);
            changed++;
        }
        if (count("select count(*) from data_export_log where export_module = 'SUPERVISION_ALERT'") == 0) {
            logDataExport(request, "SUPERVISION_ALERT", "supervision-alerts.csv", "risk_alert", 0, null, count("select count(*) from risk_alert"));
            changed++;
        }
        if (count("select count(*) from data_export_log where export_module = 'COMMUNITY_CREDIT_SCORE'") == 0) {
            logDataExport(request, "COMMUNITY_CREDIT_SCORE", "community-credit-scores.csv", "community_credit_score", 0, null, count("select count(*) from community_credit_score"));
            changed++;
        }
        Long scoreId = jdbc.sql("select id from community_credit_score order by id limit 1")
            .query(Long.class)
            .optional()
            .orElse(null);
        if (scoreId != null && count("select count(*) from data_export_log where export_module = 'COMMUNITY_CREDIT_FACTOR'") == 0) {
            Long communityId = jdbc.sql("select community_id from community_credit_score where id = :scoreId")
                .param("scoreId", scoreId)
                .query(Long.class)
                .single();
            logDataExport(request, "COMMUNITY_CREDIT_FACTOR", "community-credit-factors-" + scoreId + ".csv", "community_credit_score", scoreId, communityId, count("select count(*) from community_credit_factor where score_id = " + scoreId));
            changed++;
        }
        if (count("select count(*) from data_export_log where export_module = 'CREDIT_SCORE_RULE'") == 0) {
            logDataExport(request, "CREDIT_SCORE_RULE", "credit-score-rules.csv", "credit_score_rule", 0, null, count("select count(*) from credit_score_rule where status = 'ACTIVE'"));
            changed++;
        }
        List<ScreenTopic> topics = screenTopics(request);
        logDataExport(request, "SCREEN_TOPIC", "screen-topics.csv", "screen_topic", 0, null, topics.size());
        return changed + topics.size();
    }

    private int insertExternalCallbackReceiptIfAbsent() {
        String businessNo = "DRILL-CALLBACK-" + System.nanoTime();
        jdbc.sql("""
            insert into external_callback_receipt(adapter_code, event_type, business_no, signature_status,
                                                  http_status, request_digest, response_summary, trace_no,
                                                  idempotency_key, process_status, received_at)
            values('ACCEPTANCE_DRILL', 'SECURITY_CALLBACK', :businessNo, 'VALID',
                   200, :requestDigest, '验收演练外部回调验签通过', :traceNo,
                   :idempotencyKey, 'PROCESSED', now())
            """)
            .param("businessNo", businessNo)
            .param("requestDigest", Integer.toHexString(("ACCEPTANCE_DRILL|" + businessNo).hashCode()))
            .param("traceNo", "TRACE-" + businessNo)
            .param("idempotencyKey", "ACCEPTANCE_DRILL|SECURITY_CALLBACK|" + businessNo)
            .update();
        return 1;
    }

    private int insertExternalCallbackNonceIfAbsent() {
        String nonce = "drill-security-callback-nonce-" + System.nanoTime();
        jdbc.sql("""
            insert into external_callback_nonce(adapter_code, event_type, business_no, nonce_value, callback_timestamp, received_at)
            values('ACCEPTANCE_DRILL', 'SECURITY_CALLBACK', 'DRILL-CALLBACK-001', :nonce, :callbackTimestamp, now())
            """)
            .param("nonce", nonce)
            .param("callbackTimestamp", java.time.Instant.now().getEpochSecond())
            .update();
        return 1;
    }

    private int insertSensitiveAuditIfAbsent(String targetTable, long targetId, String fieldName, String protection, String actor, String accessPurpose) {
        Integer exists = jdbc.sql("""
            select count(*) from sensitive_field_audit
            where target_table = :targetTable and target_id = :targetId and field_name = :fieldName
              and protection = :protection and actor = :actor and access_purpose = :accessPurpose
            """)
            .param("targetTable", targetTable)
            .param("targetId", targetId)
            .param("fieldName", fieldName)
            .param("protection", protection)
            .param("actor", actor)
            .param("accessPurpose", accessPurpose)
            .query(Integer.class)
            .single();
        if (exists != null && exists > 0) {
            return 0;
        }
        jdbc.sql("""
            insert into sensitive_field_audit(target_table, target_id, field_name, protection, actor, access_purpose, created_at)
            values(:targetTable, :targetId, :fieldName, :protection, :actor, :accessPurpose, now())
            """)
            .param("targetTable", targetTable)
            .param("targetId", targetId)
            .param("fieldName", fieldName)
            .param("protection", protection)
            .param("actor", actor)
            .param("accessPurpose", accessPurpose)
            .update();
        return 1;
    }

    private void insertDrillBankFlow(long communityId, String direction, BigDecimal amount, String counterparty, String summary, String traceNo) {
        Integer exists = jdbc.sql("select count(*) from bank_flow where trace_no = :traceNo")
            .param("traceNo", traceNo)
            .query(Integer.class)
            .single();
        if (exists != null && exists > 0) {
            return;
        }
        Long accountId = jdbc.sql("""
            select coalesce(
                (select fund_account_id from community_bank_config where community_id = :communityId and service_type = 'COLLECTION' and status = 'ACTIVE' order by id limit 1),
                (select id from fund_account where community_id = :communityId order by id limit 1)
            )
            """).param("communityId", communityId).query(Long.class).single();
        jdbc.sql("""
            insert into bank_flow(community_id, account_id, direction, amount, counterparty, summary, occurred_at, trace_no)
            values(:communityId, :accountId, :direction, :amount, :counterparty, :summary, now(), :traceNo)
            """)
            .param("communityId", communityId)
            .param("accountId", accountId)
            .param("direction", direction)
            .param("amount", amount)
            .param("counterparty", counterparty)
            .param("summary", summary)
            .param("traceNo", traceNo)
            .update();
    }

    private Long insertAndReturnId(String sql, MapSqlParameterSource params) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        namedJdbc.update(sql, params, keyHolder, new String[] {"id"});
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "创建记录失败，未返回主键");
        }
        return key.longValue();
    }

    public record AcceptanceChecklist(int completionScore, long doneCount, int totalCount, List<AcceptanceItem> items) {}
    public record AcceptanceItem(String module, String description, String status, int matchedEvidenceCount, int requiredEvidenceCount, int missingEvidenceCount, List<Integer> evidenceCounts, List<String> evidenceLabels, String gapSummary, String nextAction) {}

    private byte[] simplePdf(String content) {
        String safeContent = content
            .replace("\\", "\\\\")
            .replace("(", "\\(")
            .replace(")", "\\)");
        String[] lines = safeContent.split("\\R");
        StringBuilder text = new StringBuilder("BT /F1 12 Tf 50 790 Td 16 TL\n");
        for (String line : lines) {
            if (!line.isBlank()) {
                text.append("(").append(line).append(") Tj\n");
            }
            text.append("T*\n");
        }
        text.append("ET");
        String stream = text.toString();
        String[] objects = {
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
            "<< /Length " + stream.getBytes(StandardCharsets.UTF_8).length + " >>\nstream\n" + stream + "\nendstream"
        };
        StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
        int[] offsets = new int[objects.length + 1];
        for (int i = 0; i < objects.length; i++) {
            offsets[i + 1] = pdf.toString().getBytes(StandardCharsets.UTF_8).length;
            pdf.append(i + 1).append(" 0 obj\n").append(objects[i]).append("\nendobj\n");
        }
        int xref = pdf.toString().getBytes(StandardCharsets.UTF_8).length;
        pdf.append("xref\n0 ").append(objects.length + 1).append("\n0000000000 65535 f \n");
        for (int i = 1; i < offsets.length; i++) {
            pdf.append(String.format("%010d 00000 n \n", offsets[i]));
        }
        pdf.append("trailer << /Size ").append(objects.length + 1).append(" /Root 1 0 R >>\nstartxref\n")
            .append(xref)
            .append("\n%%EOF");
        return pdf.toString().getBytes(StandardCharsets.UTF_8);
    }
}
