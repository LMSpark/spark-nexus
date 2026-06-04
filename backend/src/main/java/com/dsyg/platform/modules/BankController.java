package com.dsyg.platform.modules;

import com.dsyg.platform.auth.AccessControlService;
import com.dsyg.platform.auth.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bank")
public class BankController {
    private final JdbcClient jdbc;
    private final AccessControlService access;
    private final IntegrationService integrations;

    public BankController(JdbcClient jdbc, AccessControlService access, IntegrationService integrations) {
        this.jdbc = jdbc;
        this.access = access;
        this.integrations = integrations;
    }

    @GetMapping("/workbench")
    public BankWorkbench workbench(HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET", "BANK");
        List<Long> allowedCommunityIds = allowedCommunities(request, null, "BANK_FLOW", "READ");
        if (allowedCommunityIds.isEmpty()) {
            return new BankWorkbench(
                0, 0, BigDecimal.ZERO, BigDecimal.ZERO,
                0, BigDecimal.ZERO, 0, 0, adapterWarningCount(),
                List.of(), List.of()
            );
        }
        int activeConfigCount = countForCommunities("""
                select count(*) from community_bank_config
                where status = 'ACTIVE' and community_id in (:allowedCommunityIds)
                """, allowedCommunityIds);
        BigDecimal todayInAmount = amountForCommunities("""
                select coalesce(sum(amount), 0) from bank_flow
                where community_id in (:allowedCommunityIds)
                  and direction = 'IN'
                  and cast(occurred_at as date) = current_date
                """, allowedCommunityIds);
        BigDecimal todayOutAmount = amountForCommunities("""
                select coalesce(sum(amount), 0) from bank_flow
                where community_id in (:allowedCommunityIds)
                  and direction = 'OUT'
                  and cast(occurred_at as date) = current_date
                """, allowedCommunityIds);
        int pendingDisbursementCount = countForCommunities("""
                select count(*) from bank_disbursement_instruction
                where community_id in (:allowedCommunityIds) and status = 'PENDING'
                """, allowedCommunityIds);
        BigDecimal pendingDisbursementAmount = amountForCommunities("""
                select coalesce(sum(amount), 0) from bank_disbursement_instruction
                where community_id in (:allowedCommunityIds) and status = 'PENDING'
                """, allowedCommunityIds);
        int diffReconciliationCount = countForCommunities("""
                select count(*) from reconciliation_record
                where community_id in (:allowedCommunityIds) and status = 'DIFF'
                """, allowedCommunityIds);
        int openReconciliationDetailCount = countForCommunities("""
                select count(*)
                from reconciliation_detail rd
                join reconciliation_record r on r.id = rd.reconciliation_id
                where r.community_id in (:allowedCommunityIds)
                  and rd.status <> 'MATCHED'
                  and rd.handled_status = 'OPEN'
                """, allowedCommunityIds);
        List<BankTodoRow> todos = jdbc.sql("""
                select * from (
                  select 'DISBURSEMENT' todoType, d.id businessId, c.name communityName,
                         d.instruction_no businessNo, d.amount, d.status, d.created_at createdAt
                  from bank_disbursement_instruction d
                  join community c on c.id = d.community_id
                  where d.community_id in (:allowedCommunityIds) and d.status = 'PENDING'
                  union all
                  select 'RECONCILIATION' todoType, r.id businessId, c.name communityName,
                         concat(date_format(r.reconcile_date, '%Y-%m-%d'), '/', coalesce(cfg.service_type, 'UNKNOWN')) businessNo,
                         abs(r.diff_amount) amount, r.status, r.created_at createdAt
                  from reconciliation_record r
                  join community c on c.id = r.community_id
                  left join community_bank_config cfg on cfg.id = r.bank_config_id
                  where r.community_id in (:allowedCommunityIds) and r.status = 'DIFF'
                ) bank_todos
                order by createdAt desc
                limit 20
                """)
            .param("allowedCommunityIds", allowedCommunityIds)
            .query(BankTodoRow.class)
            .list();
        List<BankCommunitySummaryRow> communitySummaries = jdbc.sql("""
                select c.id communityId, c.name communityName,
                       (select count(*) from community_bank_config cfg
                        where cfg.community_id = c.id and cfg.status = 'ACTIVE') activeConfigCount,
                       (select coalesce(sum(bf.amount), 0) from bank_flow bf
                        where bf.community_id = c.id and bf.direction = 'IN'
                          and bf.occurred_at >= date_sub(now(), interval 30 day)) inAmount,
                       (select coalesce(sum(bf.amount), 0) from bank_flow bf
                        where bf.community_id = c.id and bf.direction = 'OUT'
                          and bf.occurred_at >= date_sub(now(), interval 30 day)) outAmount,
                       (select count(*) from bank_disbursement_instruction d
                        where d.community_id = c.id and d.status = 'PENDING') pendingDisbursementCount,
                       (select r.status from reconciliation_record r
                        where r.community_id = c.id
                        order by r.reconcile_date desc, r.id desc limit 1) latestReconciliationStatus,
                       (select r.reconcile_date from reconciliation_record r
                        where r.community_id = c.id
                        order by r.reconcile_date desc, r.id desc limit 1) latestReconcileDate
                from community c
                where c.id in (:allowedCommunityIds)
                order by pendingDisbursementCount desc, c.id
                """)
            .param("allowedCommunityIds", allowedCommunityIds)
            .query(BankCommunitySummaryRow.class)
            .list();
        return new BankWorkbench(
            allowedCommunityIds.size(), activeConfigCount, todayInAmount, todayOutAmount,
            pendingDisbursementCount, pendingDisbursementAmount, diffReconciliationCount,
            openReconciliationDetailCount, adapterWarningCount(), todos, communitySummaries
        );
    }

    @GetMapping("/configs")
    public List<BankConfigRow> configs(@RequestParam(required = false) Long communityId, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET", "PROPERTY", "BANK");
        List<Long> allowedCommunityIds = allowedCommunities(request, communityId, "BANK_FLOW", "READ");
        if (allowedCommunityIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
                select cfg.id, cfg.community_id communityId, c.name communityName,
                       cfg.bank_tenant_id bankTenantId, t.tenant_name bankTenantName,
                       cfg.service_type serviceType, cfg.fund_account_id fundAccountId,
                       fa.account_name fundAccountName, cfg.merchant_no merchantNo,
                       cfg.status, cfg.created_at createdAt, cfg.updated_at updatedAt
                from community_bank_config cfg
                join community c on c.id = cfg.community_id
                join tenant t on t.id = cfg.bank_tenant_id
                left join fund_account fa on fa.id = cfg.fund_account_id
                where cfg.community_id in (:allowedCommunityIds)
                order by cfg.community_id, cfg.service_type
                """)
            .param("allowedCommunityIds", allowedCommunityIds)
            .query(BankConfigRow.class)
            .list();
    }

    @GetMapping("/adapters")
    public List<BankAdapterProfileRow> adapters(HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET", "BANK");
        return jdbc.sql("""
                select p.id, p.bank_code bankCode, p.bank_name bankName, p.bank_tenant_id bankTenantId,
                       t.tenant_name bankTenantName, p.api_base_url apiBaseUrl,
                       p.sign_algorithm signAlgorithm, p.callback_algorithm callbackAlgorithm,
                       p.statement_mode statementMode, p.disbursement_mode disbursementMode,
                       p.status,
                       h.check_result lastCheckResult, h.evidence lastEvidence, h.checked_at lastCheckedAt
                from bank_adapter_profile p
                left join tenant t on t.id = p.bank_tenant_id
                left join bank_adapter_health h on h.id = (
                    select max(latest_health.id) from bank_adapter_health latest_health where latest_health.bank_code = p.bank_code
                )
                order by p.id
                """)
            .query(BankAdapterProfileRow.class)
            .list();
    }

    @PostMapping("/adapters/health-check")
    public Map<String, Object> checkAllAdapters(HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "BANK");
        List<BankAdapterProfileRow> rows = adapters(request);
        int checked = 0;
        int passed = 0;
        for (BankAdapterProfileRow row : rows) {
            BankAdapterHealthResult result = recordAdapterHealth(row);
            checked++;
            if ("PASS".equals(result.checkResult())) {
                passed++;
            }
        }
        audit(principal(request).username(), "银行适配器批量健康检查", "bank_adapter_profile", 0);
        return Map.of("checked", checked, "passed", passed, "warnings", checked - passed);
    }

    @PostMapping("/adapters/{bankCode}/health-check")
    public BankAdapterHealthResult checkAdapter(@PathVariable String bankCode, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "BANK");
        BankAdapterProfileRow row = adapterByCode(bankCode);
        BankAdapterHealthResult result = recordAdapterHealth(row);
        audit(principal(request).username(), "银行适配器健康检查-" + bankCode, "bank_adapter_profile", row.id());
        return result;
    }

    @GetMapping("/adapters/health/export")
    public ResponseEntity<byte[]> exportAdapterHealth(HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET", "BANK");
        List<BankAdapterHealthRow> rows = jdbc.sql("""
                select h.id, h.bank_code bankCode, p.bank_name bankName, h.check_item checkItem,
                       h.check_result checkResult, h.latency_ms latencyMs, h.evidence, h.checked_at checkedAt
                from bank_adapter_health h
                left join bank_adapter_profile p on p.bank_code = h.bank_code
                order by h.checked_at desc, h.id desc
                limit 200
                """).query(BankAdapterHealthRow.class).list();
        StringBuilder csv = new StringBuilder("银行编码,银行名称,检查项,结果,耗时毫秒,证据,检查时间\n");
        for (BankAdapterHealthRow row : rows) {
            csv.append(csv(row.bankCode())).append(',')
                .append(csv(row.bankName())).append(',')
                .append(csv(row.checkItem())).append(',')
                .append(csv(row.checkResult())).append(',')
                .append(row.latencyMs()).append(',')
                .append(csv(row.evidence())).append(',')
                .append(csv(row.checkedAt() == null ? "" : row.checkedAt().toString())).append('\n');
        }
        logDataExport(request, "BANK_ADAPTER_HEALTH", "bank-adapter-health.csv", "bank_adapter_health", 0, null, rows.size());
        return csvResponse("bank-adapter-health.csv", csv.toString());
    }

    @GetMapping("/flows")
    public List<BankFlowRow> flows(@RequestParam(required = false) Long communityId, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET", "PROPERTY", "BANK");
        List<Long> allowedCommunityIds = allowedCommunities(request, communityId, "BANK_FLOW", "READ");
        if (allowedCommunityIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
                select bf.id, bf.community_id communityId, c.name communityName,
                       bf.account_id accountId, fa.account_name accountName,
                       bf.direction, bf.amount, bf.counterparty, bf.summary,
                       bf.occurred_at occurredAt, bf.trace_no traceNo
                from bank_flow bf
                join community c on c.id = bf.community_id
                left join fund_account fa on fa.id = bf.account_id
                where bf.community_id in (:allowedCommunityIds)
                order by bf.occurred_at desc, bf.id desc
                limit 300
                """)
            .param("allowedCommunityIds", allowedCommunityIds)
            .query(BankFlowRow.class)
            .list();
    }

    @GetMapping("/flows/export")
    public ResponseEntity<byte[]> exportFlows(@RequestParam(required = false) Long communityId, HttpServletRequest request) {
        List<BankFlowRow> rows = flows(communityId, request);
        String fileName = "bank-flows.csv";
        StringBuilder csv = new StringBuilder("小区,账户,方向,金额,对方户名,摘要,发生时间,流水号\n");
        for (BankFlowRow row : rows) {
            csv.append(csv(row.communityName())).append(',')
                .append(csv(row.accountName())).append(',')
                .append(csv(row.direction())).append(',')
                .append(row.amount()).append(',')
                .append(csv(row.counterparty())).append(',')
                .append(csv(row.summary())).append(',')
                .append(csv(row.occurredAt() == null ? "" : row.occurredAt().toString())).append(',')
                .append(csv(row.traceNo())).append('\n');
        }
        logDataExport(request, "BANK_FLOW", fileName, "bank_flow", communityId == null ? 0 : communityId, communityId, rows.size());
        return csvResponse(fileName, csv.toString());
    }

    @PostMapping("/configs")
    public Map<String, Object> createConfig(@RequestBody BankConfigRequest body, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET");
        TokenService.Principal principal = principal(request);
        access.assertCommunityAccess(principal, body.communityId(), "BANK_FLOW", "WRITE");
        jdbc.sql("""
                insert into community_bank_config(community_id, bank_tenant_id, service_type, fund_account_id,
                                                  merchant_no, status, created_at, updated_at)
                values(:communityId, :bankTenantId, :serviceType, :fundAccountId,
                       :merchantNo, 'ACTIVE', now(), now())
                """)
            .param("communityId", body.communityId())
            .param("bankTenantId", body.bankTenantId())
            .param("serviceType", body.serviceType())
            .param("fundAccountId", body.fundAccountId())
            .param("merchantNo", body.merchantNo())
            .update();
        Long id = jdbc.sql("select max(id) from community_bank_config").query(Long.class).single();
        audit(principal.username(), "配置小区银行服务-" + body.serviceType(), "community_bank_config", id);
        return Map.of("configId", id, "status", "ACTIVE");
    }

    @GetMapping("/reconciliations")
    public List<ReconciliationRow> reconciliations(@RequestParam(required = false) Long communityId, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET", "PROPERTY", "BANK");
        List<Long> allowedCommunityIds = allowedCommunities(request, communityId, "BANK_FLOW", "READ");
        if (allowedCommunityIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
                select r.id, r.community_id communityId, c.name communityName,
                       r.bank_config_id bankConfigId, cfg.service_type serviceType,
                       r.reconcile_date reconcileDate, r.system_amount systemAmount,
                       r.bank_amount bankAmount, r.diff_amount diffAmount,
                       r.matched_count matchedCount, r.unmatched_count unmatchedCount,
                       r.status, r.created_at createdAt
                from reconciliation_record r
                join community c on c.id = r.community_id
                left join community_bank_config cfg on cfg.id = r.bank_config_id
                where r.community_id in (:allowedCommunityIds)
                order by r.reconcile_date desc, r.id desc
                """)
            .param("allowedCommunityIds", allowedCommunityIds)
            .query(ReconciliationRow.class)
            .list();
    }

    @PostMapping("/flows/import")
    public Map<String, Object> importBankFlow(@RequestBody BankFlowImportRequest body, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET", "BANK");
        TokenService.Principal principal = principal(request);
        access.assertCommunityAccess(principal, body.communityId(), "BANK_FLOW", "WRITE");
        Long accountId = body.accountId();
        if (accountId == null || accountId == 0) {
            accountId = jdbc.sql("""
                    select id from fund_account
                    where community_id = :communityId
                    order by case account_type when 'PROPERTY_SERVICE' then 0 else 1 end, id
                    limit 1
                    """)
                .param("communityId", body.communityId())
                .query(Long.class)
                .single();
        }
        String traceNo = body.traceNo() == null || body.traceNo().isBlank() ? "BKIMP" + System.currentTimeMillis() : body.traceNo();
        jdbc.sql("""
                insert into bank_flow(community_id, account_id, direction, amount, counterparty, summary, occurred_at, trace_no)
                values(:communityId, :accountId, :direction, :amount, :counterparty, :summary, :occurredAt, :traceNo)
                """)
            .param("communityId", body.communityId())
            .param("accountId", accountId)
            .param("direction", body.direction() == null ? "IN" : body.direction())
            .param("amount", body.amount())
            .param("counterparty", body.counterparty() == null ? "银行导入" : body.counterparty())
            .param("summary", body.summary() == null ? "银行流水导入" : body.summary())
            .param("occurredAt", body.occurredAt() == null ? LocalDateTime.now() : body.occurredAt())
            .param("traceNo", traceNo)
            .update();
        Long id = jdbc.sql("select max(id) from bank_flow").query(Long.class).single();
        audit(principal.username(), "导入银行流水-" + traceNo, "bank_flow", id);
        return Map.of("flowId", id, "traceNo", traceNo);
    }

    @PostMapping("/reconciliations/run")
    public Map<String, Object> runReconciliation(@RequestBody ReconciliationRunRequest body, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET", "PROPERTY", "BANK");
        TokenService.Principal principal = principal(request);
        access.assertCommunityAccess(principal, body.communityId(), "BANK_FLOW", "READ");
        BankConfigRow config = jdbc.sql("""
                select cfg.id, cfg.community_id communityId, c.name communityName,
                       cfg.bank_tenant_id bankTenantId, t.tenant_name bankTenantName,
                       cfg.service_type serviceType, cfg.fund_account_id fundAccountId,
                       fa.account_name fundAccountName, cfg.merchant_no merchantNo,
                       cfg.status, cfg.created_at createdAt, cfg.updated_at updatedAt
                from community_bank_config cfg
                join community c on c.id = cfg.community_id
                join tenant t on t.id = cfg.bank_tenant_id
                left join fund_account fa on fa.id = cfg.fund_account_id
                where cfg.community_id = :communityId and cfg.service_type = :serviceType and cfg.status = 'ACTIVE'
                order by cfg.id desc limit 1
                """)
            .param("communityId", body.communityId())
            .param("serviceType", body.serviceType())
            .query(BankConfigRow.class)
            .optional()
            .orElseThrow(() -> new IllegalArgumentException("当前小区未配置该银行服务"));
        LocalDate date = body.reconcileDate() == null ? LocalDate.now() : body.reconcileDate();
        BigDecimal systemAmount = jdbc.sql("""
                select coalesce(sum(amount), 0) from payment_order
                where community_id = :communityId and status = 'PAID' and cast(paid_at as date) = :date
                """)
            .param("communityId", body.communityId())
            .param("date", date)
            .query(BigDecimal.class)
            .single();
        BigDecimal bankAmount = jdbc.sql("""
                select coalesce(sum(amount), 0) from bank_flow
                where community_id = :communityId and direction = 'IN' and cast(occurred_at as date) = :date
                """)
            .param("communityId", body.communityId())
            .param("date", date)
            .query(BigDecimal.class)
            .single();
        Integer matchedCount = jdbc.sql("""
                select count(*) from payment_order
                where community_id = :communityId and status = 'PAID' and cast(paid_at as date) = :date
                """)
            .param("communityId", body.communityId())
            .param("date", date)
            .query(Integer.class)
            .single();
        BigDecimal diff = systemAmount.subtract(bankAmount);
        String status = diff.compareTo(BigDecimal.ZERO) == 0 ? "MATCHED" : "DIFF";
        jdbc.sql("""
                insert into reconciliation_record(community_id, bank_config_id, reconcile_date, system_amount,
                                                  bank_amount, diff_amount, matched_count, unmatched_count, status, created_at)
                values(:communityId, :bankConfigId, :date, :systemAmount,
                       :bankAmount, :diffAmount, :matchedCount, :unmatchedCount, :status, now())
                """)
            .param("communityId", body.communityId())
            .param("bankConfigId", config.id())
            .param("date", date)
            .param("systemAmount", systemAmount)
            .param("bankAmount", bankAmount)
            .param("diffAmount", diff)
            .param("matchedCount", matchedCount)
            .param("unmatchedCount", "MATCHED".equals(status) ? 0 : 1)
            .param("status", status)
            .update();
        Long id = jdbc.sql("select max(id) from reconciliation_record").query(Long.class).single();
        writeReconciliationDetails(id, body.communityId(), date);
        audit(principal.username(), "执行银行对账-" + status, "reconciliation_record", id);
        return Map.of("reconciliationId", id, "status", status, "systemAmount", systemAmount, "bankAmount", bankAmount, "diffAmount", diff);
    }

    @GetMapping("/reconciliations/{id}/details")
    public List<ReconciliationDetailRow> reconciliationDetails(@PathVariable long id, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET", "PROPERTY", "BANK");
        TokenService.Principal principal = principal(request);
        Long communityId = jdbc.sql("select community_id from reconciliation_record where id = :id")
            .param("id", id)
            .query(Long.class)
            .single();
        access.assertCommunityAccess(principal, communityId, "BANK_FLOW", "READ");
        return jdbc.sql("""
                select id, reconciliation_id reconciliationId, source_type sourceType, source_id sourceId,
                       order_no orderNo, trace_no traceNo, amount, status, description,
                       handled_status handledStatus, handled_by handledBy, handled_at handledAt,
                       handle_remark handleRemark, created_at createdAt
                from reconciliation_detail where reconciliation_id = :id order by id
                """)
            .param("id", id)
            .query(ReconciliationDetailRow.class)
            .list();
    }

    @GetMapping("/reconciliations/{id}/details/export")
    public ResponseEntity<byte[]> exportReconciliationDetails(@PathVariable long id, HttpServletRequest request) {
        List<ReconciliationDetailRow> rows = reconciliationDetails(id, request);
        Long communityId = jdbc.sql("select community_id from reconciliation_record where id = :id")
            .param("id", id)
            .query(Long.class)
            .single();
        String fileName = "reconciliation-" + id + "-details.csv";
        StringBuilder csv = new StringBuilder("来源,订单号,流水号,金额,状态,处理状态,处理人,处理时间,说明,处理说明\n");
        for (ReconciliationDetailRow row : rows) {
            csv.append(csv(row.sourceType())).append(',')
                .append(csv(row.orderNo())).append(',')
                .append(csv(row.traceNo())).append(',')
                .append(row.amount()).append(',')
                .append(csv(row.status())).append(',')
                .append(csv(row.handledStatus())).append(',')
                .append(csv(row.handledBy())).append(',')
                .append(csv(row.handledAt() == null ? "" : row.handledAt().toString())).append(',')
                .append(csv(row.description())).append(',')
                .append(csv(row.handleRemark())).append('\n');
        }
        logDataExport(request, "BANK_RECONCILIATION", fileName, "reconciliation_record", id, communityId, rows.size());
        return csvResponse(fileName, csv.toString());
    }

    @PostMapping("/reconciliations/{id}/details/{detailId}/resolve")
    public Map<String, Object> resolveReconciliationDetail(@PathVariable long id, @PathVariable long detailId, @RequestBody ReconciliationDetailResolveRequest body, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET", "PROPERTY", "BANK");
        TokenService.Principal principal = principal(request);
        Long communityId = jdbc.sql("select community_id from reconciliation_record where id = :id")
            .param("id", id)
            .query(Long.class)
            .single();
        access.assertCommunityAccess(principal, communityId, "BANK_FLOW", "WRITE");
        ReconciliationDetailRow detail = jdbc.sql("""
                select id, reconciliation_id reconciliationId, source_type sourceType, source_id sourceId,
                       order_no orderNo, trace_no traceNo, amount, status, description,
                       handled_status handledStatus, handled_by handledBy, handled_at handledAt,
                       handle_remark handleRemark, created_at createdAt
                from reconciliation_detail
                where id = :detailId and reconciliation_id = :id
                """)
            .param("detailId", detailId)
            .param("id", id)
            .query(ReconciliationDetailRow.class)
            .optional()
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "未找到对账明细"));
        if ("MATCHED".equals(detail.status())) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "匹配明细无需人工处理");
        }
        String handledStatus = body.handledStatus() == null || body.handledStatus().isBlank() ? "RESOLVED" : body.handledStatus();
        jdbc.sql("""
                update reconciliation_detail
                set handled_status = :handledStatus,
                    handled_by = :handledBy,
                    handled_at = now(),
                    handle_remark = :remark
                where id = :detailId and reconciliation_id = :id
                """)
            .param("handledStatus", handledStatus)
            .param("handledBy", principal.username())
            .param("remark", body.remark() == null || body.remark().isBlank() ? "人工确认差异已处理" : body.remark())
            .param("detailId", detailId)
            .param("id", id)
            .update();
        refreshReconciliationStatus(id);
        audit(principal.username(), "处理银行对账差异-" + handledStatus, "reconciliation_detail", detailId);
        return Map.of("detailId", detailId, "reconciliationId", id, "handledStatus", handledStatus);
    }

    @GetMapping("/disbursements")
    public List<BankDisbursementRow> disbursements(@RequestParam(required = false) Long communityId, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "GOVERNMENT", "STREET", "BANK", "COMMITTEE");
        List<Long> allowedCommunityIds = allowedCommunities(request, communityId, "BANK_FLOW", "READ");
        if (allowedCommunityIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
                select d.id, d.expense_id expenseId, d.community_id communityId, c.name communityName,
                       d.fund_account_id fundAccountId, d.instruction_no instructionNo, d.payee_name payeeName, d.amount, d.purpose,
                       d.status, d.bank_trace_no bankTraceNo, d.created_at createdAt,
                       d.processed_by processedBy, d.processed_at processedAt, d.remark
                from bank_disbursement_instruction d
                join community c on c.id = d.community_id
                where d.community_id in (:allowedCommunityIds)
                order by d.created_at desc
                """)
            .param("allowedCommunityIds", allowedCommunityIds)
            .query(BankDisbursementRow.class)
            .list();
    }

    @PostMapping("/disbursements/{id}/process")
    public Map<String, Object> processDisbursement(@PathVariable long id, @RequestBody BankDisbursementProcessRequest body, HttpServletRequest request) {
        requireAnyRole(request, "ADMIN", "BANK");
        TokenService.Principal principal = principal(request);
        BankDisbursementRow row = disbursementById(id);
        access.assertCommunityAccess(principal, row.communityId(), "BANK_FLOW", "WRITE");
        if (!"PENDING".equals(row.status())) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "只有待放款指令可以处理");
        }
        String decision = body.decision() == null ? "PAY" : body.decision();
        if ("RETURN".equalsIgnoreCase(decision)) {
            jdbc.sql("""
                    update bank_disbursement_instruction
                    set status = 'RETURNED', processed_by = :operator, processed_at = now(), remark = :remark
                    where id = :id
                    """)
                .param("operator", principal.username())
                .param("remark", body.remark() == null ? "银行退回放款指令" : body.remark())
                .param("id", id)
                .update();
            jdbc.sql("update expense_order set status = 'RETURNED', current_node = '银行退回' where id = :expenseId")
                .param("expenseId", row.expenseId())
                .update();
            audit(principal.username(), "银行退回放款指令", "bank_disbursement_instruction", id);
            return Map.of("instructionId", id, "status", "RETURNED");
        }
        Map<String, Object> bankResult = integrations.executeBankDisbursement(id, row.expenseId(), row.amount(), row.payeeName());
        String bankTraceNo = String.valueOf(bankResult.get("bankTraceNo"));
        jdbc.sql("""
                update bank_disbursement_instruction
                set status = 'PAID', bank_trace_no = :bankTraceNo, processed_by = :operator,
                    processed_at = now(), remark = :remark
                where id = :id
                """)
            .param("bankTraceNo", bankTraceNo)
            .param("operator", principal.username())
            .param("remark", body.remark() == null ? "银行直连放款成功" : body.remark())
            .param("id", id)
            .update();
        jdbc.sql("""
                insert into bank_flow(community_id, account_id, direction, amount, counterparty, summary, occurred_at, trace_no)
                values(:communityId, :accountId, 'OUT', :amount, :payeeName, :purpose, now(), :bankTraceNo)
                """)
            .param("communityId", row.communityId())
            .param("accountId", row.fundAccountId())
            .param("amount", row.amount())
            .param("payeeName", row.payeeName())
            .param("purpose", row.purpose())
            .param("bankTraceNo", bankTraceNo)
            .update();
        audit(principal.username(), "银行放款完成", "bank_disbursement_instruction", id);
        return Map.of("instructionId", id, "status", "PAID", "bankTraceNo", bankTraceNo);
    }

    @PostMapping("/callbacks/disbursement")
    public Map<String, Object> disbursementCallback(@RequestBody BankDisbursementCallbackRequest body, HttpServletRequest request) {
        String instructionNo = body.instructionNo() == null ? "" : body.instructionNo();
        String idempotencyKey = callbackIdempotencyKey("BANK_DIRECT", "DISBURSEMENT_CALLBACK", instructionNo);
        String canonicalPayload = integrations.canonicalPayload(Map.of(
            "bankTraceNo", body.bankTraceNo() == null ? "" : body.bankTraceNo(),
            "instructionNo", instructionNo,
            "remark", body.remark() == null ? "" : body.remark(),
            "status", body.status() == null ? "" : body.status()
        ));
        Map<String, Object> verifyResult = integrations.verifyExternalCallback("BANK_DIRECT", "DISBURSEMENT_CALLBACK", instructionNo, canonicalPayload, request.getHeader("X-Bank-Signature"));
        String signatureStatus = String.valueOf(verifyResult.get("signatureStatus"));
        if (!Boolean.TRUE.equals(verifyResult.get("verified"))) {
            logExternalCallback("BANK_DIRECT", "DISBURSEMENT_CALLBACK", instructionNo, signatureStatus, 400, canonicalPayload, "银行回执验签失败", idempotencyKey, "REJECTED");
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "银行回执验签失败");
        }
        IntegrationService.CallbackReplayCheck replayCheck = integrations.verifyCallbackReplay(
            "BANK_DIRECT",
            "DISBURSEMENT_CALLBACK",
            instructionNo,
            request.getHeader("X-Bank-Timestamp"),
            request.getHeader("X-Bank-Nonce")
        );
        if (!replayCheck.accepted()) {
            logExternalCallback("BANK_DIRECT", "DISBURSEMENT_CALLBACK", instructionNo, signatureStatus, 409, canonicalPayload, replayCheck.message(), idempotencyKey, "REJECTED");
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, replayCheck.message());
        }
        if (callbackAlreadyProcessed(idempotencyKey)) {
            logExternalCallback("BANK_DIRECT", "DISBURSEMENT_CALLBACK", instructionNo, signatureStatus, 200, canonicalPayload, "重复银行回执已幂等跳过", idempotencyKey, "DUPLICATE");
            return Map.of("instructionNo", instructionNo, "status", "SUCCESS", "idempotent", true);
        }
        BankDisbursementRow row = jdbc.sql("""
                select d.id, d.expense_id expenseId, d.community_id communityId, c.name communityName,
                       d.fund_account_id fundAccountId, d.instruction_no instructionNo,
                       d.payee_name payeeName, d.amount, d.purpose, d.status,
                       d.bank_trace_no bankTraceNo, d.created_at createdAt,
                       d.processed_by processedBy, d.processed_at processedAt, d.remark
                from bank_disbursement_instruction d
                join community c on c.id = d.community_id
                where d.instruction_no = :instructionNo
                """)
            .param("instructionNo", instructionNo)
            .query(BankDisbursementRow.class)
            .optional()
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "放款指令不存在"));
        String status = body.status() == null || body.status().isBlank() ? "PAID" : body.status().toUpperCase();
        String bankTraceNo = body.bankTraceNo() == null || body.bankTraceNo().isBlank() ? "BANKCB" + System.currentTimeMillis() : body.bankTraceNo();
        if ("RETURNED".equals(status) || "FAILED".equals(status)) {
            jdbc.sql("""
                    update bank_disbursement_instruction
                    set status = 'RETURNED', bank_trace_no = :bankTraceNo, processed_by = 'BANK_CALLBACK',
                        processed_at = now(), remark = :remark
                    where id = :id
                    """)
                .param("bankTraceNo", bankTraceNo)
                .param("remark", body.remark() == null ? "银行回执退回" : body.remark())
                .param("id", row.id())
                .update();
            jdbc.sql("update expense_order set status = 'RETURNED', current_node = '银行回执退回' where id = :expenseId")
                .param("expenseId", row.expenseId())
                .update();
            logExternalCallback("BANK_DIRECT", "DISBURSEMENT_CALLBACK", instructionNo, signatureStatus, 200, canonicalPayload, "银行回执退回，trace=" + bankTraceNo, idempotencyKey, "PROCESSED");
            audit("BANK_CALLBACK", "银行回执退回放款", "bank_disbursement_instruction", row.id());
            return Map.of("instructionId", row.id(), "status", "RETURNED", "bankTraceNo", bankTraceNo);
        }
        jdbc.sql("""
                update bank_disbursement_instruction
                set status = 'PAID', bank_trace_no = :bankTraceNo, processed_by = 'BANK_CALLBACK',
                    processed_at = now(), remark = :remark
                where id = :id
                """)
            .param("bankTraceNo", bankTraceNo)
            .param("remark", body.remark() == null ? "银行回执确认放款" : body.remark())
            .param("id", row.id())
            .update();
        upsertDisbursementBankFlow(row, bankTraceNo);
        logExternalCallback("BANK_DIRECT", "DISBURSEMENT_CALLBACK", instructionNo, signatureStatus, 200, canonicalPayload, "银行回执确认放款，trace=" + bankTraceNo, idempotencyKey, "PROCESSED");
        audit("BANK_CALLBACK", "银行回执确认放款", "bank_disbursement_instruction", row.id());
        return Map.of("instructionId", row.id(), "status", "PAID", "bankTraceNo", bankTraceNo);
    }

    private void upsertDisbursementBankFlow(BankDisbursementRow row, String bankTraceNo) {
        Long existingFlowId = jdbc.sql("""
                select id from bank_flow
                where trace_no in (:oldTraceNo, :newTraceNo)
                   or (community_id = :communityId and account_id = :accountId and direction = 'OUT'
                       and amount = :amount and counterparty = :payeeName and summary = :purpose)
                order by case when trace_no = :newTraceNo then 0 else 1 end, id desc
                limit 1
                """)
            .param("oldTraceNo", row.bankTraceNo() == null ? "" : row.bankTraceNo())
            .param("newTraceNo", bankTraceNo)
            .param("communityId", row.communityId())
            .param("accountId", row.fundAccountId())
            .param("amount", row.amount())
            .param("payeeName", row.payeeName())
            .param("purpose", row.purpose())
            .query(Long.class)
            .optional()
            .orElse(null);
        if (existingFlowId != null) {
            jdbc.sql("""
                    update bank_flow
                    set community_id = :communityId,
                        account_id = :accountId,
                        direction = 'OUT',
                        amount = :amount,
                        counterparty = :payeeName,
                        summary = :purpose,
                        trace_no = :bankTraceNo,
                        occurred_at = now()
                    where id = :id
                    """)
                .param("communityId", row.communityId())
                .param("accountId", row.fundAccountId())
                .param("amount", row.amount())
                .param("payeeName", row.payeeName())
                .param("purpose", row.purpose())
                .param("bankTraceNo", bankTraceNo)
                .param("id", existingFlowId)
                .update();
        } else {
            jdbc.sql("""
                    insert into bank_flow(community_id, account_id, direction, amount, counterparty, summary, occurred_at, trace_no)
                    values(:communityId, :accountId, 'OUT', :amount, :payeeName, :purpose, now(), :bankTraceNo)
                    """)
                .param("communityId", row.communityId())
                .param("accountId", row.fundAccountId())
                .param("amount", row.amount())
                .param("payeeName", row.payeeName())
                .param("purpose", row.purpose())
                .param("bankTraceNo", bankTraceNo)
                .update();
            existingFlowId = jdbc.sql("select max(id) from bank_flow where trace_no = :bankTraceNo")
                .param("bankTraceNo", bankTraceNo)
                .query(Long.class)
                .single();
        }
        jdbc.sql("""
                delete from bank_flow
                where trace_no = :bankTraceNo and direction = 'OUT' and id <> :keepId
                """)
            .param("bankTraceNo", bankTraceNo)
            .param("keepId", existingFlowId)
            .update();
    }

    private List<Long> allowedCommunities(HttpServletRequest request, Long communityId, String dataScope, String action) {
        TokenService.Principal principal = principal(request);
        if (communityId != null) {
            access.assertCommunityAccess(principal, communityId, dataScope, action);
            return List.of(communityId);
        }
        return access.allowedCommunityIds(principal, dataScope, action);
    }

    private int countForCommunities(String sql, List<Long> allowedCommunityIds) {
        Integer count = jdbc.sql(sql)
            .param("allowedCommunityIds", allowedCommunityIds)
            .query(Integer.class)
            .single();
        return count == null ? 0 : count;
    }

    private BigDecimal amountForCommunities(String sql, List<Long> allowedCommunityIds) {
        BigDecimal amount = jdbc.sql(sql)
            .param("allowedCommunityIds", allowedCommunityIds)
            .query(BigDecimal.class)
            .single();
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private int adapterWarningCount() {
        Integer count = jdbc.sql("""
                select count(*)
                from bank_adapter_profile p
                left join bank_adapter_health h on h.id = (
                    select max(latest_health.id) from bank_adapter_health latest_health where latest_health.bank_code = p.bank_code
                )
                where p.status not in ('ACTIVE', 'READY')
                   or coalesce(h.check_result, 'PASS') <> 'PASS'
                """)
            .query(Integer.class)
            .single();
        return count == null ? 0 : count;
    }

    private TokenService.Principal principal(HttpServletRequest request) {
        return (TokenService.Principal) request.getAttribute("principal");
    }

    private BankDisbursementRow disbursementById(long id) {
        return jdbc.sql("""
                select d.id, d.expense_id expenseId, d.community_id communityId, c.name communityName,
                       d.fund_account_id fundAccountId, d.instruction_no instructionNo,
                       d.payee_name payeeName, d.amount, d.purpose, d.status,
                       d.bank_trace_no bankTraceNo, d.created_at createdAt,
                       d.processed_by processedBy, d.processed_at processedAt, d.remark
                from bank_disbursement_instruction d
                join community c on c.id = d.community_id
                where d.id = :id
                """)
            .param("id", id)
            .query(BankDisbursementRow.class)
            .single();
    }

    private BankAdapterProfileRow adapterByCode(String bankCode) {
        return jdbc.sql("""
                select p.id, p.bank_code bankCode, p.bank_name bankName, p.bank_tenant_id bankTenantId,
                       t.tenant_name bankTenantName, p.api_base_url apiBaseUrl,
                       p.sign_algorithm signAlgorithm, p.callback_algorithm callbackAlgorithm,
                       p.statement_mode statementMode, p.disbursement_mode disbursementMode,
                       p.status,
                       h.check_result lastCheckResult, h.evidence lastEvidence, h.checked_at lastCheckedAt
                from bank_adapter_profile p
                left join tenant t on t.id = p.bank_tenant_id
                left join bank_adapter_health h on h.id = (
                    select max(latest_health.id) from bank_adapter_health latest_health where latest_health.bank_code = p.bank_code
                )
                where p.bank_code = :bankCode
                """)
            .param("bankCode", bankCode)
            .query(BankAdapterProfileRow.class)
            .optional()
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "银行适配器不存在"));
    }

    private BankAdapterHealthResult recordAdapterHealth(BankAdapterProfileRow row) {
        long started = System.nanoTime();
        boolean endpointConfigured = row.apiBaseUrl() != null && !row.apiBaseUrl().isBlank();
        boolean algorithmConfigured = row.signAlgorithm() != null && !row.signAlgorithm().isBlank()
            && row.callbackAlgorithm() != null && !row.callbackAlgorithm().isBlank();
        boolean modeConfigured = row.statementMode() != null && !row.statementMode().isBlank()
            && row.disbursementMode() != null && !row.disbursementMode().isBlank();
        String result = endpointConfigured && algorithmConfigured && modeConfigured ? "PASS" : "WARN";
        int latencyMs = Math.max(1, (int) ((System.nanoTime() - started) / 1_000_000));
        String evidence = "endpoint=" + row.apiBaseUrl()
            + "; sign=" + row.signAlgorithm()
            + "; callback=" + row.callbackAlgorithm()
            + "; statement=" + row.statementMode()
            + "; disbursement=" + row.disbursementMode()
            + ("PASS".equals(result) ? "; contractReady=true" : "; contractReady=false");
        jdbc.sql("""
                insert into bank_adapter_health(bank_code, check_item, check_result, latency_ms, evidence, checked_at)
                values(:bankCode, '接口契约/签名/对账/放款配置检查', :checkResult, :latencyMs, :evidence, now())
                """)
            .param("bankCode", row.bankCode())
            .param("checkResult", result)
            .param("latencyMs", latencyMs)
            .param("evidence", evidence)
            .update();
        return new BankAdapterHealthResult(row.bankCode(), result, latencyMs, evidence);
    }

    private void writeReconciliationDetails(long reconciliationId, long communityId, LocalDate date) {
        jdbc.sql("""
                insert into reconciliation_detail(reconciliation_id, source_type, source_id, order_no, trace_no, amount, status, description, handled_status, created_at)
                select :reconciliationId, 'PAYMENT_ORDER', po.id, po.order_no, null, po.amount,
                       case when exists (
                         select 1 from bank_flow bf
                         where bf.community_id = po.community_id and bf.direction = 'IN'
                           and cast(bf.occurred_at as date) = :date and bf.amount = po.amount
                       ) then 'MATCHED' else 'MISSING_BANK_FLOW' end,
                       case when exists (
                         select 1 from bank_flow bf
                         where bf.community_id = po.community_id and bf.direction = 'IN'
                           and cast(bf.occurred_at as date) = :date and bf.amount = po.amount
                       ) then '支付订单与银行流水金额匹配' else '支付订单未找到同日同金额银行流水' end,
                       case when exists (
                         select 1 from bank_flow bf
                         where bf.community_id = po.community_id and bf.direction = 'IN'
                           and cast(bf.occurred_at as date) = :date and bf.amount = po.amount
                       ) then 'AUTO_CLOSED' else 'OPEN' end,
                       now()
                from payment_order po
                where po.community_id = :communityId and po.status in ('PAID', 'PARTIAL_REFUND', 'REFUNDED') and cast(po.paid_at as date) = :date
                """)
            .param("reconciliationId", reconciliationId)
            .param("communityId", communityId)
            .param("date", date)
            .update();
        jdbc.sql("""
                insert into reconciliation_detail(reconciliation_id, source_type, source_id, order_no, trace_no, amount, status, description, handled_status, created_at)
                select :reconciliationId, 'BANK_FLOW', bf.id, null, bf.trace_no, bf.amount,
                       case when exists (
                         select 1 from payment_order po
                         where po.community_id = bf.community_id and po.status in ('PAID', 'PARTIAL_REFUND', 'REFUNDED')
                           and cast(po.paid_at as date) = :date and po.amount = bf.amount
                       ) then 'MATCHED' else 'MISSING_PAYMENT_ORDER' end,
                       case when exists (
                         select 1 from payment_order po
                         where po.community_id = bf.community_id and po.status in ('PAID', 'PARTIAL_REFUND', 'REFUNDED')
                           and cast(po.paid_at as date) = :date and po.amount = bf.amount
                       ) then '银行流水与支付订单金额匹配' else '银行流水未找到同日同金额支付订单' end,
                       case when exists (
                         select 1 from payment_order po
                         where po.community_id = bf.community_id and po.status in ('PAID', 'PARTIAL_REFUND', 'REFUNDED')
                           and cast(po.paid_at as date) = :date and po.amount = bf.amount
                       ) then 'AUTO_CLOSED' else 'OPEN' end,
                       now()
                from bank_flow bf
                where bf.community_id = :communityId and bf.direction = 'IN' and cast(bf.occurred_at as date) = :date
                """)
            .param("reconciliationId", reconciliationId)
            .param("communityId", communityId)
            .param("date", date)
            .update();
    }

    private void refreshReconciliationStatus(long reconciliationId) {
        Integer openDiffCount = jdbc.sql("""
                select count(*) from reconciliation_detail
                where reconciliation_id = :id and status <> 'MATCHED' and handled_status not in ('RESOLVED', 'IGNORED', 'AUTO_CLOSED')
                """)
            .param("id", reconciliationId)
            .query(Integer.class)
            .single();
        Integer diffCount = jdbc.sql("select count(*) from reconciliation_detail where reconciliation_id = :id and status <> 'MATCHED'")
            .param("id", reconciliationId)
            .query(Integer.class)
            .single();
        if (diffCount > 0 && openDiffCount == 0) {
            jdbc.sql("update reconciliation_record set status = 'RESOLVED' where id = :id")
                .param("id", reconciliationId)
                .update();
        }
    }

    private void requireAnyRole(HttpServletRequest request, String... roles) {
        TokenService.Principal principal = principal(request);
        for (String role : roles) {
            if (role.equals(principal.role())) {
                return;
            }
        }
        throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "当前角色无权使用该功能");
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

    private ResponseEntity<byte[]> csvResponse(String filename, String content) {
        byte[] bytes = ("\uFEFF" + content).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(bytes);
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private void logDataExport(HttpServletRequest request, String exportModule, String fileName, String targetType, long targetId, Long communityId, int rowCount) {
        TokenService.Principal principal = principal(request);
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
        if ("ADMIN".equals(principal.role()) || "GOVERNMENT".equals(principal.role()) || "STREET".equals(principal.role())) {
            return "AUTHORIZED_COMMUNITIES";
        }
        return "TENANT:" + principal.tenantId();
    }

    private String exportFilterSummary(String targetType, long targetId, Long communityId, int rowCount) {
        return "target=" + targetType + "#" + targetId
            + "; community=" + (communityId == null ? "AUTHORIZED" : communityId)
            + "; rows=" + rowCount;
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

    public record BankConfigRequest(long communityId, long bankTenantId, String serviceType, Long fundAccountId, String merchantNo) {}
    public record BankWorkbench(int allowedCommunityCount, int activeConfigCount, BigDecimal todayInAmount, BigDecimal todayOutAmount, int pendingDisbursementCount, BigDecimal pendingDisbursementAmount, int diffReconciliationCount, int openReconciliationDetailCount, int adapterWarningCount, List<BankTodoRow> todos, List<BankCommunitySummaryRow> communitySummaries) {}
    public record BankTodoRow(String todoType, long businessId, String communityName, String businessNo, BigDecimal amount, String status, LocalDateTime createdAt) {}
    public record BankCommunitySummaryRow(long communityId, String communityName, int activeConfigCount, BigDecimal inAmount, BigDecimal outAmount, int pendingDisbursementCount, String latestReconciliationStatus, LocalDate latestReconcileDate) {}
    public record BankAdapterProfileRow(long id, String bankCode, String bankName, Long bankTenantId, String bankTenantName, String apiBaseUrl, String signAlgorithm, String callbackAlgorithm, String statementMode, String disbursementMode, String status, String lastCheckResult, String lastEvidence, LocalDateTime lastCheckedAt) {}
    public record BankAdapterHealthRow(long id, String bankCode, String bankName, String checkItem, String checkResult, int latencyMs, String evidence, LocalDateTime checkedAt) {}
    public record BankAdapterHealthResult(String bankCode, String checkResult, int latencyMs, String evidence) {}
    public record BankFlowImportRequest(long communityId, Long accountId, String direction, BigDecimal amount, String counterparty, String summary, LocalDateTime occurredAt, String traceNo) {}
    public record ReconciliationRunRequest(long communityId, String serviceType, LocalDate reconcileDate) {}
    public record BankConfigRow(long id, long communityId, String communityName, long bankTenantId, String bankTenantName, String serviceType, Long fundAccountId, String fundAccountName, String merchantNo, String status, LocalDateTime createdAt, LocalDateTime updatedAt) {}
    public record BankFlowRow(long id, long communityId, String communityName, Long accountId, String accountName, String direction, BigDecimal amount, String counterparty, String summary, LocalDateTime occurredAt, String traceNo) {}
    public record ReconciliationRow(long id, long communityId, String communityName, Long bankConfigId, String serviceType, LocalDate reconcileDate, BigDecimal systemAmount, BigDecimal bankAmount, BigDecimal diffAmount, int matchedCount, int unmatchedCount, String status, LocalDateTime createdAt) {}
    public record ReconciliationDetailRow(long id, long reconciliationId, String sourceType, Long sourceId, String orderNo, String traceNo, BigDecimal amount, String status, String description, String handledStatus, String handledBy, LocalDateTime handledAt, String handleRemark, LocalDateTime createdAt) {}
    public record ReconciliationDetailResolveRequest(String handledStatus, String remark) {}
    public record BankDisbursementRow(long id, long expenseId, long communityId, String communityName, Long fundAccountId, String instructionNo, String payeeName, BigDecimal amount, String purpose, String status, String bankTraceNo, LocalDateTime createdAt, String processedBy, LocalDateTime processedAt, String remark) {}
    public record BankDisbursementProcessRequest(String decision, String remark) {}
    public record BankDisbursementCallbackRequest(String instructionNo, String bankTraceNo, String status, String remark) {}
}
