package com.dsyg.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MultiTenantAcceptanceTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcClient jdbc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void propertyRegistrationAndCommunityAuthorizationCreateRealDataBoundary() throws Exception {
        String adminToken = login("admin", "admin123");
        String username = "property_accept_" + System.currentTimeMillis();

        JsonNode submitResult = postJson("/api/registrations/tenants", "", """
            {
              "tenantType": "PROPERTY",
              "tenantName": "验收物业服务有限公司",
              "unifiedCreditCode": "91420100ACCEPT00001",
              "contactName": "验收经理",
              "contactPhone": "13800009991",
              "adminUsername": "%s",
              "adminPassword": "admin123",
              "adminDisplayName": "验收物业管理员"
            }
            """.formatted(username));
        long applicationId = registrationId(submitResult.get("applicationNo").asText());

        postJson("/api/registrations/" + applicationId + "/approve", adminToken, "{\"comment\":\"验收通过\"}");
        String propertyToken = login(username, "admin123");
        long tenantId = jdbc.sql("select tenant_id from user_tenant_relation r join app_user u on u.id = r.user_id where u.username = :username")
            .param("username", username)
            .query(Long.class)
            .single();

        mockMvc.perform(get("/api/communities").header("Authorization", bearer(propertyToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()", equalTo(0)));

        JsonNode relationResult = postJson("/api/registrations/community-relations", propertyToken, """
            {
              "tenantId": %d,
              "communityId": 1,
              "relationType": "PROPERTY_SERVICE",
              "startDate": "2026-06-01",
              "dataScopes": ["COMMUNITY_PROFILE", "HOUSE", "BILLING", "REPAIR", "COMPLAINT"],
              "permissions": ["READ", "WRITE"]
            }
            """.formatted(tenantId));
        long relationApplicationId = registrationId(relationResult.get("applicationNo").asText());
        postJson("/api/registrations/" + relationApplicationId + "/approve", adminToken, "{\"comment\":\"服务关系通过\"}");

        mockMvc.perform(get("/api/communities").header("Authorization", bearer(propertyToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].id", hasItem(1)));

        long relationId = jdbc.sql("""
                select id from tenant_community_relation
                where tenant_id = :tenantId and community_id = 1 and relation_type = 'PROPERTY_SERVICE'
                order by id desc limit 1
                """)
            .param("tenantId", tenantId)
            .query(Long.class)
            .single();
        mockMvc.perform(post("/api/communities/1/relations/" + relationId + "/disable")
                .header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DISABLED"))
            .andExpect(jsonPath("$.revokedAuthorizations", greaterThanOrEqualTo(1)));

        mockMvc.perform(get("/api/communities").header("Authorization", bearer(propertyToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()", equalTo(0)));

        Integer activeAuthorizationCount = jdbc.sql("""
                select count(*) from data_authorization
                where grantee_tenant_id = :tenantId and community_id = 1 and status = 'ACTIVE'
                """)
            .param("tenantId", tenantId)
            .query(Integer.class)
            .single();
        org.assertj.core.api.Assertions.assertThat(activeAuthorizationCount).isZero();
    }

    @Test
    void bankServiceAndReconciliationAreBoundByCommunityAuthorization() throws Exception {
        String adminToken = login("admin", "admin123");
        String bankToken = login("bank", "admin123");

        JsonNode result = postJson("/api/registrations/bank-services", bankToken, """
            {
              "bankTenantId": 5,
              "communityId": 1,
              "serviceType": "COLLECTION",
              "merchantNo": "MCH-ACCEPT-001"
            }
            """);
        long applicationId = registrationId(result.get("applicationNo").asText());
        postJson("/api/registrations/" + applicationId + "/approve", adminToken, "{\"comment\":\"银行服务通过\"}");

        mockMvc.perform(get("/api/bank/configs?communityId=1").header("Authorization", bearer(bankToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$[*].serviceType", hasItem("COLLECTION")));
        mockMvc.perform(get("/api/registrations/community-options").header("Authorization", bearer(bankToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].id", hasItem(1)));
        mockMvc.perform(get("/api/bank/workbench").header("Authorization", bearer(bankToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.allowedCommunityCount", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.activeConfigCount", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.communitySummaries[*].communityId", hasItem(1)));
        mockMvc.perform(get("/api/bank/adapters").header("Authorization", bearer(bankToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].bankCode", hasItems("HANKOU_BANK", "CCB_WUHAN")));
        mockMvc.perform(post("/api/bank/adapters/health-check").header("Authorization", bearer(bankToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.checked", greaterThanOrEqualTo(2)))
            .andExpect(jsonPath("$.passed", greaterThanOrEqualTo(2)));
        mockMvc.perform(get("/api/bank/adapters/health/export").header("Authorization", bearer(bankToken)))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", containsString("bank-adapter-health.csv")));

        mockMvc.perform(post("/api/bank/reconciliations/run")
                .header("Authorization", bearer(bankToken))
                .contentType("application/json")
                .content("""
                    {
                      "communityId": 1,
                      "serviceType": "COLLECTION",
                      "reconcileDate": "2026-06-01"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reconciliationId").exists())
            .andExpect(jsonPath("$.status").exists());

        postJson("/api/bank/flows/import", bankToken, """
            {
              "communityId": 1,
              "direction": "IN",
              "amount": 88.88,
              "counterparty": "差异客户",
              "summary": "验收差异流水",
              "occurredAt": "2026-06-01T10:00:00",
              "traceNo": "ACCEPT-DIFF-001"
            }
            """);
        JsonNode diffRun = postJson("/api/bank/reconciliations/run", bankToken, """
            {
              "communityId": 1,
              "serviceType": "COLLECTION",
              "reconcileDate": "2026-06-01"
            }
            """);
        long reconciliationId = diffRun.get("reconciliationId").asLong();
        JsonNode details = getJson("/api/bank/reconciliations/" + reconciliationId + "/details", bankToken);
        long diffDetailId = 0;
        for (JsonNode detail : details) {
            if (!"MATCHED".equals(detail.get("status").asText())) {
                diffDetailId = detail.get("id").asLong();
                break;
            }
        }
        postJson("/api/bank/reconciliations/" + reconciliationId + "/details/" + diffDetailId + "/resolve", bankToken, """
            {
              "handledStatus": "RESOLVED",
              "remark": "验收确认差异已处理"
            }
            """);
        mockMvc.perform(get("/api/bank/reconciliations/" + reconciliationId + "/details").header("Authorization", bearer(bankToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].handledStatus", hasItem("RESOLVED")));
        Integer handledDetailCount = jdbc.sql("""
                select count(*) from reconciliation_detail
                where reconciliation_id = :reconciliationId
                  and handled_status in ('AUTO_CLOSED', 'RESOLVED', 'IGNORED')
                """)
            .param("reconciliationId", reconciliationId)
            .query(Integer.class)
            .single();
        org.assertj.core.api.Assertions.assertThat(handledDetailCount).isGreaterThanOrEqualTo(1);
        mockMvc.perform(get("/api/bank/reconciliations/" + reconciliationId + "/details/export").header("Authorization", bearer(bankToken)))
            .andExpect(status().isOk());
        Integer reconciliationExportCount = jdbc.sql("""
                select count(*) from data_export_log
                where export_module = 'BANK_RECONCILIATION'
                  and target_id = :reconciliationId
                  and data_scope = 'COMMUNITY:1'
                """)
            .param("reconciliationId", reconciliationId)
            .query(Integer.class)
            .single();
        org.assertj.core.api.Assertions.assertThat(reconciliationExportCount).isGreaterThanOrEqualTo(1);
    }

    @Test
    void paymentRefundCreatesRefundOrderOutboundFlowAndSmsLog() throws Exception {
        String ownerToken = login("owner", "admin123");
        String propertyToken = login("property", "admin123");

        JsonNode prepay = postJson("/api/payments/wechat/prepay", ownerToken, "{\"billId\":1}");
        JsonNode confirm = postJson("/api/payments/wechat/confirm", ownerToken, """
            {
              "billId": 1,
              "orderNo": "%s",
              "prepayId": "%s"
            }
            """.formatted(prepay.get("orderNo").asText(), prepay.get("prepayId").asText()));
        long receiptId = confirm.get("receiptId").asLong();
        org.assertj.core.api.Assertions.assertThat(receiptId).isGreaterThan(0);
        mockMvc.perform(get("/api/payments/receipts").header("Authorization", bearer(propertyToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].receiptNo", hasItem(startsWith("EPR-"))))
            .andExpect(jsonPath("$[*].status", hasItem("ISSUED")));
        mockMvc.perform(get("/api/payments/receipts/" + receiptId + "/pdf").header("Authorization", bearer(propertyToken)))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", containsString(".pdf")));
        mockMvc.perform(get("/api/payments/receipts/export").header("Authorization", bearer(propertyToken)))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"electronic-payment-receipts.csv\""));
        mockMvc.perform(get("/api/billing/export").header("Authorization", bearer(propertyToken)))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"billing-bills.csv\""))
            .andExpect(content().string(containsString("欠费金额")));
        JsonNode taxInvoice = postJson("/api/payments/receipts/" + receiptId + "/tax-invoice", propertyToken, """
            {
              "buyerName": "张女士",
              "buyerTaxNo": "PERSONAL",
              "invoiceItem": "物业费",
              "taxCategoryCode": "304080299",
              "taxRate": 0.06
            }
            """);
        long taxInvoiceId = taxInvoice.get("invoiceId").asLong();
        org.assertj.core.api.Assertions.assertThat(taxInvoiceId).isGreaterThan(0);
        mockMvc.perform(get("/api/tax/invoices").header("Authorization", bearer(propertyToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].status", hasItem("ISSUED")))
            .andExpect(jsonPath("$[*].taxInvoiceNo", hasItem(startsWith("DT"))));
        mockMvc.perform(get("/api/tax/invoices/export").header("Authorization", bearer(propertyToken)))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"tax-invoices.csv\""));
        mockMvc.perform(get("/api/tax/invoices/" + taxInvoiceId + "/pdf").header("Authorization", bearer(propertyToken)))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", containsString(".pdf")))
            .andExpect(content().contentType("application/pdf"));
        mockMvc.perform(post("/api/tax/invoices/" + taxInvoiceId + "/red-cancel")
                .header("Authorization", bearer(propertyToken))
                .contentType("application/json")
                .content("{\"reason\":\"验收红冲\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("RED_CANCELLED"));

        JsonNode refund = postJson("/api/payments/wechat/refund", propertyToken, """
            {
              "billId": 1,
              "reason": "验收退款"
            }
            """);

        org.assertj.core.api.Assertions.assertThat(refund.get("statementId").asLong()).isGreaterThan(0);
        mockMvc.perform(get("/api/payments/refunds").header("Authorization", bearer(propertyToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].refundNo", hasItem(refund.get("refundNo").asText())))
            .andExpect(jsonPath("$[*].status", hasItem("REFUNDED")));
        mockMvc.perform(get("/api/payments/statements").header("Authorization", bearer(propertyToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].tradeType", hasItem("REFUND")))
            .andExpect(jsonPath("$[*].orderNo", hasItem(refund.get("refundNo").asText())));
        mockMvc.perform(get("/api/bank/reconciliations").header("Authorization", bearer(propertyToken)))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/integrations/calls").header("Authorization", bearer(login("admin", "admin123"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].operation", hasItem("REFUND_PAYMENT")));

        mockMvc.perform(post("/api/payments/wechat/statement-sync")
                .header("Authorization", bearer(propertyToken))
                .contentType("application/json")
                .content("{\"statementDate\":\"2026-06-01\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.syncedCount", greaterThanOrEqualTo(1)));
        mockMvc.perform(get("/api/payments/statements").header("Authorization", bearer(propertyToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].tradeType", hasItems("PAYMENT", "REFUND")));
    }

    @Test
    void publicRevenueExpenseApprovalCreatesAndPaysBankDisbursement() throws Exception {
        String adminToken = login("admin", "admin123");
        String ownerToken = login("owner", "admin123");
        postJson("/api/approval-rules", adminToken, """
            {
              "communityId": 1,
              "expenseThreshold": 1000.00,
              "voteThreshold": 1000.00,
              "approvalTimeoutHours": 24,
              "voteRatio": 0.01,
              "status": "ACTIVE"
            }
            """);
        JsonNode expense = postJson("/api/expenses", adminToken, """
            {
              "communityId": 1,
              "orderType": "PAYMENT",
              "title": "验收公共收益小额维修付款",
              "amount": 1200.00,
              "invoiceNo": "INV-ACCEPT-DISB",
              "contractNo": "HT-ACCEPT-DISB",
              "invoiceIssuer": "验收供应商",
              "invoiceAmount": 1200.00,
              "invoiceIssueDate": "2026-06-01",
              "invoiceFileUrl": "mock://invoice/accept-disb",
              "contractParty": "验收供应商",
              "contractAmount": 1200.00,
              "contractSignDate": "2026-06-01",
              "contractFileUrl": "mock://contract/accept-disb"
            }
            """);
        long expenseId = expense.get("expenseId").asLong();

        postJson("/api/approvals/" + expenseId + "/approve", adminToken, "{}");
        postJson("/api/approvals/" + expenseId + "/approve", adminToken, "{}");
        long voteId = jdbc.sql("select id from vote where related_expense_id = :expenseId order by id desc limit 1")
            .param("expenseId", expenseId)
            .query(Long.class)
            .single();
        mockMvc.perform(post("/api/votes/" + voteId + "/cast")
                .header("Authorization", bearer(ownerToken))
                .contentType("application/json")
                .content("{\"decision\":\"AGREE\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.decision", equalTo("AGREE")))
            .andExpect(jsonPath("$.signatureHash").exists());
        mockMvc.perform(get("/api/votes/" + voteId + "/ballots").header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].decision", hasItem("AGREE")));
        mockMvc.perform(get("/api/approvals/" + expenseId + "/nodes").header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.nodeCode == 'OWNER_VOTE')].status", hasItem("APPROVED")));
        mockMvc.perform(get("/api/workflow/templates").header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].templateCode", hasItem("PUBLIC_EXPENSE")));
        mockMvc.perform(get("/api/workflow/templates/PUBLIC_EXPENSE/nodes").header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].nodeCode", hasItems("COMMITTEE_REVIEW", "OWNER_VOTE", "BANK_PAYMENT")));
        mockMvc.perform(post("/api/workflow/templates/PUBLIC_EXPENSE/nodes")
                .header("Authorization", bearer(adminToken))
                .contentType("application/json")
                .content("""
                    {
                      "nodeCode": "COMMITTEE_REVIEW",
                      "nodeName": "业委会审批",
                      "roleCode": "COMMITTEE",
                      "sortNo": 2,
                      "conditionCode": "ALWAYS",
                      "timeoutHours": 24,
                      "status": "ACTIVE"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", equalTo("SAVED")));
        mockMvc.perform(get("/api/approvals/" + expenseId + "/events").header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].eventType", hasItems("CREATE_NODE", "APPROVED")));
        mockMvc.perform(get("/api/workflow/events/export").header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("UPSERT_NODE")));
        mockMvc.perform(get("/api/workflow/events/export").header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", containsString("workflow-events.csv")));
        long instructionId = jdbc.sql("""
                select id from bank_disbursement_instruction
                where expense_id = :expenseId
                order by id desc limit 1
                """)
            .param("expenseId", expenseId)
            .query(Long.class)
            .single();

        mockMvc.perform(get("/api/bank/disbursements").header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].instructionNo").exists())
            .andExpect(jsonPath("$[*].status", hasItem("PENDING")));

        mockMvc.perform(post("/api/bank/disbursements/" + instructionId + "/process")
                .header("Authorization", bearer(adminToken))
                .contentType("application/json")
                .content("{\"decision\":\"PAY\",\"remark\":\"验收银行直连放款\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", equalTo("PAID")))
            .andExpect(jsonPath("$.bankTraceNo").exists());
        String instructionNo = jdbc.sql("select instruction_no from bank_disbursement_instruction where id = :id")
            .param("id", instructionId)
            .query(String.class)
            .single();
        String callbackBankTraceNo = "BANK-CB-IDEMPOTENT-" + instructionNo;
        mockMvc.perform(post("/api/bank/callbacks/disbursement")
                .contentType("application/json")
                .header("X-Bank-Timestamp", String.valueOf(java.time.Instant.now().getEpochSecond()))
                .header("X-Bank-Nonce", "bank-callback-" + instructionNo + "-1")
                .content("""
                    {
                      "instructionNo": "%s",
                      "bankTraceNo": "%s",
                      "status": "PAID",
                      "remark": "银行回执确认"
                    }
                    """.formatted(instructionNo, callbackBankTraceNo)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", equalTo("PAID")))
            .andExpect(jsonPath("$.bankTraceNo", equalTo(callbackBankTraceNo)));
        mockMvc.perform(post("/api/bank/callbacks/disbursement")
                .contentType("application/json")
                .header("X-Bank-Timestamp", String.valueOf(java.time.Instant.now().getEpochSecond()))
                .header("X-Bank-Nonce", "bank-callback-" + instructionNo + "-2")
                .content("""
                    {
                      "instructionNo": "%s",
                      "bankTraceNo": "%s",
                      "status": "PAID",
                      "remark": "银行回执重复确认"
                    }
                    """.formatted(instructionNo, callbackBankTraceNo)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", equalTo("SUCCESS")))
            .andExpect(jsonPath("$.idempotent", equalTo(true)));
        mockMvc.perform(post("/api/bank/callbacks/disbursement")
                .contentType("application/json")
                .header("X-Bank-Timestamp", String.valueOf(java.time.Instant.now().getEpochSecond()))
                .header("X-Bank-Nonce", "bank-callback-" + instructionNo + "-2")
                .content("""
                    {
                      "instructionNo": "%s",
                      "bankTraceNo": "%s",
                      "status": "PAID",
                      "remark": "银行回执重放"
                    }
                    """.formatted(instructionNo, callbackBankTraceNo)))
            .andExpect(status().isConflict());
        Integer disbursementFlowCount = jdbc.sql("""
                select count(*) from bank_flow
                where direction = 'OUT' and amount = 1200.00
                  and summary = '验收公共收益小额维修付款'
                  and trace_no = :callbackBankTraceNo
                """)
            .param("callbackBankTraceNo", callbackBankTraceNo)
            .query(Integer.class)
            .single();
        org.assertj.core.api.Assertions.assertThat(disbursementFlowCount).isEqualTo(1);
        Integer bankNonceCount = jdbc.sql("""
                select count(*) from external_callback_nonce
                where adapter_code = 'BANK_DIRECT' and event_type = 'DISBURSEMENT_CALLBACK'
                  and business_no = :instructionNo
                """)
            .param("instructionNo", instructionNo)
            .query(Integer.class)
            .single();
        org.assertj.core.api.Assertions.assertThat(bankNonceCount).isGreaterThanOrEqualTo(2);

        mockMvc.perform(get("/api/bank/disbursements").header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].status", hasItem("PAID")));
        JsonNode manualVoucher = postJson("/api/finance/vouchers", adminToken, """
            {
              "communityId": 1,
              "debitSubject": "银行存款-公共收益专户",
              "creditSubject": "公共收益收入",
              "amount": 880.00
            }
            """);
        long manualVoucherId = manualVoucher.get("voucherId").asLong();
        org.assertj.core.api.Assertions.assertThat(manualVoucher.get("voucherNo").asText()).startsWith("PZ-MAN-");
        mockMvc.perform(post("/api/finance/vouchers/batch-review")
                .header("Authorization", bearer(adminToken))
                .contentType("application/json")
                .content("{\"voucherIds\":[" + manualVoucherId + "],\"decision\":\"approve\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.updatedCount", equalTo(1)))
            .andExpect(jsonPath("$.status", equalTo("APPROVED")));
        mockMvc.perform(post("/api/finance/vouchers/batch-book")
                .header("Authorization", bearer(adminToken))
                .contentType("application/json")
                .content("{\"voucherIds\":[" + manualVoucherId + "]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.bookedCount", equalTo(1)))
            .andExpect(jsonPath("$.status", equalTo("BOOKED")));
        mockMvc.perform(get("/api/finance/vouchers").header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].sourceType", hasItem("MANUAL")))
            .andExpect(jsonPath("$[*].status", hasItem("BOOKED")));
        mockMvc.perform(get("/api/finance/ledger/pdf").header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", containsString("finance-ledger.pdf")))
            .andExpect(content().contentType("application/pdf"));
        mockMvc.perform(get("/api/finance/cash-flow/pdf").header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", containsString("cash-flow.pdf")))
            .andExpect(content().contentType("application/pdf"));
    }

    @Test
    void residentCanBindMultipleHousesWithoutOverwritingOwnerProfiles() throws Exception {
        String adminToken = login("admin", "admin123");
        String username = "resident_multi_" + System.currentTimeMillis();

        postJson("/api/resident/register", "", """
            {
              "username": "%s",
              "password": "admin123",
              "name": "多房屋住户",
              "phone": "13800008888"
            }
            """.formatted(username));
        String residentToken = login(username, "admin123");

        long firstApplicationId = submitHouseBinding(residentToken, "鼎盛阳光一期", "1栋", "1101", "多房屋住户");
        postJson("/api/resident/house-bindings/" + firstApplicationId + "/approve", adminToken, "{\"comment\":\"第一套通过\"}");

        long secondApplicationId = submitHouseBinding(residentToken, "鼎盛阳光二期", "5栋", "0601", "多房屋住户");
        postJson("/api/resident/house-bindings/" + secondApplicationId + "/approve", adminToken, "{\"comment\":\"第二套通过\"}");

        mockMvc.perform(get("/api/resident/house-bindings/me").header("Authorization", bearer(residentToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[?(@.isPrimary == 1)].roomNo", hasItem("1101")))
            .andExpect(jsonPath("$[*].roomNo", hasItem("0601")));

        mockMvc.perform(get("/api/owners/portal?houseId=1").header("Authorization", bearer(residentToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.profile.roomNo", equalTo("1101")));
        mockMvc.perform(get("/api/owners/portal?houseId=3").header("Authorization", bearer(residentToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.profile.roomNo", equalTo("0601")));

        JsonNode secondCommunitySurvey = postJson("/api/votes", adminToken, """
            {
              "communityId": 2,
              "title": "多房屋第二小区满意度问卷",
              "voteType": "SURVEY",
              "startAt": "2026-06-01T09:00:00",
              "endAt": "2026-06-08T18:00:00",
              "questionTitle": "第二小区服务是否满意？",
              "options": ["满意", "一般", "不满意"]
            }
            """);
        long secondVoteId = secondCommunitySurvey.get("voteId").asLong();
        getJson("/api/votes/" + secondVoteId + "/survey", residentToken);
        long secondQuestionId = surveyQuestionId(secondVoteId, "第二小区服务");
        long secondOptionId = firstSurveyOptionId(secondQuestionId);
        postJson("/api/votes/" + secondVoteId + "/survey-submit", residentToken, """
            {
              "answers": [
                {"questionId": %d, "optionId": %d}
              ]
            }
            """.formatted(secondQuestionId, secondOptionId));
        mockMvc.perform(get("/api/votes/" + secondVoteId + "/survey-results").header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].responseCount", hasItem(1)));

        JsonNode secondHouseWorkOrder = postJson("/api/work-orders", residentToken, """
            {
              "houseId": 3,
              "orderType": "REPAIR",
              "title": "第二小区多房屋报修",
              "description": "第二套房屋门禁故障",
              "priority": "NORMAL"
            }
            """);
        long secondWorkOrderId = secondHouseWorkOrder.get("workOrderId").asLong();
        postJson("/api/work-orders/" + secondWorkOrderId + "/reply", adminToken, """
            {
              "status": "DONE",
              "reply": "第二小区工单已完成"
            }
            """);
        postJson("/api/work-orders/" + secondWorkOrderId + "/evaluate", residentToken, """
            {
              "score": 5,
              "comment": "第二套房屋处理满意"
            }
            """);
    }

    @Test
    void residentServiceFlowRequiresSurveySubmissionAndWorkOrderEvaluation() throws Exception {
        String adminToken = login("admin", "admin123");
        String ownerToken = login("owner", "admin123");
        String propertyToken = login("property", "admin123");

        JsonNode vote = postJson("/api/votes", adminToken, """
            {
              "communityId": 1,
              "title": "验收住户服务满意度问卷",
              "voteType": "SURVEY",
              "startAt": "2026-06-01T09:00:00",
              "endAt": "2026-06-08T18:00:00",
              "questionTitle": "您对报修处理是否满意？",
              "options": ["满意", "基本满意", "不满意"]
            }
            """);
        long voteId = vote.get("voteId").asLong();
        getJson("/api/votes/" + voteId + "/survey", ownerToken);
        long questionId = surveyQuestionId(voteId, "报修处理");
        long optionId = firstSurveyOptionId(questionId);
        postJson("/api/votes/" + voteId + "/survey-submit", ownerToken, """
            {
              "answers": [
                {"questionId": %d, "optionId": %d}
              ]
            }
            """.formatted(questionId, optionId));
        mockMvc.perform(get("/api/votes/" + voteId + "/survey-results").header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].responseCount", hasItem(1)));

        JsonNode workOrder = postJson("/api/work-orders", ownerToken, """
            {
              "orderType": "REPAIR",
              "title": "验收住户报修",
              "description": "水管漏水验收工单",
              "priority": "NORMAL"
            }
            """);
        long workOrderId = workOrder.get("workOrderId").asLong();
        postJson("/api/work-orders/" + workOrderId + "/reply", propertyToken, """
            {
              "status": "DONE",
              "reply": "已完成维修并回访"
            }
            """);
        postJson("/api/work-orders/" + workOrderId + "/evaluate", ownerToken, """
            {
              "score": 5,
              "comment": "处理及时"
            }
            """);
        mockMvc.perform(get("/api/repairs").header("Authorization", bearer(propertyToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].id", hasItem((int) workOrderId)))
            .andExpect(jsonPath("$[*].satisfactionScore", hasItem(5)));
    }

    private long submitHouseBinding(String token, String communityName, String building, String roomNo, String name) throws Exception {
        JsonNode result = postJson("/api/resident/house-bindings", token, """
            {
              "communityName": "%s",
              "building": "%s",
              "roomNo": "%s",
              "residentType": "OWNER",
              "name": "%s",
              "phone": "13800008888",
              "identityNo": "420101199001011234"
            }
            """.formatted(communityName, building, roomNo, name));
        return registrationId(result.get("applicationNo").asText());
    }

    private String login(String username, String password) throws Exception {
        JsonNode result = postJson("/api/auth/login", "", """
            {"username":"%s","password":"%s"}
            """.formatted(username, password));
        return result.get("token").asText();
    }

    private JsonNode postJson(String url, String token, String json) throws Exception {
        var request = post(url).contentType("application/json").content(json);
        if (token != null && !token.isBlank()) {
            request.header("Authorization", bearer(token));
        }
        String response = mockMvc.perform(request)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return objectMapper.readTree(response);
    }

    private JsonNode getJson(String url, String token) throws Exception {
        String response = mockMvc.perform(get(url).header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return objectMapper.readTree(response);
    }

    private long surveyQuestionId(long voteId, String titlePart) {
        return jdbc.sql("""
                select id from survey_question
                where vote_id = :voteId and question_title like :title
                order by id desc limit 1
                """)
            .param("voteId", voteId)
            .param("title", "%" + titlePart + "%")
            .query(Long.class)
            .single();
    }

    private long firstSurveyOptionId(long questionId) {
        return jdbc.sql("""
                select id from survey_option
                where question_id = :questionId
                order by sort_no, id
                limit 1
                """)
            .param("questionId", questionId)
            .query(Long.class)
            .single();
    }

    private long registrationId(String applicationNo) {
        return jdbc.sql("select id from registration_application where application_no = :applicationNo")
            .param("applicationNo", applicationNo)
            .query(Long.class)
            .single();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
