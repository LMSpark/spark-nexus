package com.dsyg.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApiSmokeTest {
    @Autowired
    MockMvc mockMvc;
    @Autowired
    JdbcClient jdbc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void rootRedirectsToFrontend() throws Exception {
        mockMvc.perform(get("/"))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", "http://localhost:5683/"));
    }

    @Test
    void loginAndDashboardWorkAgainstSeedData() throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                .contentType("application/json")
                .content("{\"username\":\"gov\",\"password\":\"admin123\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token", notNullValue()))
            .andReturn()
            .getResponse()
            .getContentAsString();

        String token = tokenFrom(body);
        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.apiPrefix").value("/api"));
        String adminBody = mockMvc.perform(post("/api/auth/login")
                .contentType("application/json")
                .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token", notNullValue()))
            .andReturn()
            .getResponse()
            .getContentAsString();
        String adminToken = tokenFrom(adminBody);

        mockMvc.perform(get("/api/supervision/dashboard")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.metrics.length()", greaterThan(0)))
            .andExpect(jsonPath("$.ranks.length()", greaterThan(0)))
            .andExpect(jsonPath("$.alerts.length()", greaterThan(0)));
        mockMvc.perform(get("/api/supervision/screen/topics")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].topicCode", hasItem("FUND_RISK")))
            .andExpect(jsonPath("$[*].topicCode", hasItem("PAYMENT_RISK")));
        mockMvc.perform(get("/api/supervision/screen/topics/export")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"screen-topics.csv\""));
        mockMvc.perform(get("/api/gateway/status")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.apiPrefix").value("/api"))
            .andExpect(jsonPath("$.allowedOrigins", hasItem("http://localhost:*")))
            .andExpect(jsonPath("$.publicPaths", hasItem("/api/auth/login")))
            .andExpect(jsonPath("$.routeGroups[*].moduleCode", hasItem("BANK")))
            .andExpect(jsonPath("$.policies[*].policyCode", hasItem("JWT_AUTH")))
            .andExpect(jsonPath("$.policies[*].policyCode", hasItem("CORS")));
        mockMvc.perform(get("/api/supervision/alerts/export")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"supervision-alerts.csv\""))
            .andExpect(content().contentType("text/csv;charset=UTF-8"));
        mockMvc.perform(get("/api/supervision/credit-scores/export")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"community-credit-scores.csv\""))
            .andExpect(content().contentType("text/csv;charset=UTF-8"));
        mockMvc.perform(post("/api/supervision/credit-scores/generate")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.generatedCount", greaterThan(0)))
            .andExpect(jsonPath("$.ruleVersion", equalTo("2026-A")));
        mockMvc.perform(get("/api/supervision/credit-rules")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].factorCode", hasItems("PAYMENT_RATE", "OPEN_ALERT", "OVERDUE_BILL", "AUDIT_CHAIN")));
        mockMvc.perform(get("/api/supervision/credit-runs")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].ruleVersion", hasItem("2026-A")));
        mockMvc.perform(get("/api/supervision/credit-rules/export")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"credit-score-rules.csv\""));
        long scoreId = jdbc.sql("select id from community_credit_score order by id limit 1")
            .query(Long.class)
            .single();
        mockMvc.perform(get("/api/supervision/credit-scores/" + scoreId + "/factors")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].factorCode", hasItem("PAYMENT_RATE")))
            .andExpect(jsonPath("$[*].factorCode", hasItem("AUDIT_CHAIN")));
        mockMvc.perform(get("/api/supervision/credit-scores/" + scoreId + "/factors/export")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"community-credit-factors-" + scoreId + ".csv\""));

        mockMvc.perform(get("/api/acceptance/checklist")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.completionScore", greaterThan(0)))
            .andExpect(jsonPath("$.items.length()", greaterThan(0)))
            .andExpect(jsonPath("$.items[2].evidenceCounts[4]", greaterThan(0)));
        mockMvc.perform(get("/api/auth/capabilities")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.navKeys", hasItem("registrations")))
            .andExpect(jsonPath("$.capabilities.length()", greaterThan(0)));
        mockMvc.perform(post("/api/fee-standards")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("""
                    {
                      "communityId": 1,
                      "feeType": "监管越权测试",
                      "billingMode": "FIXED",
                      "unitPrice": 1.00,
                      "cycle": "MONTHLY",
                      "effectiveFrom": "2026-06-01"
                    }
                    """))
            .andExpect(status().isForbidden());

        String drillBody = mockMvc.perform(post("/api/acceptance/drill/run")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DONE"))
            .andExpect(jsonPath("$.paymentOrderId", notNullValue()))
            .andExpect(jsonPath("$.receiptId", notNullValue()))
            .andExpect(jsonPath("$.taxInvoiceId", notNullValue()))
            .andExpect(jsonPath("$.reconciliationId", notNullValue()))
            .andReturn()
            .getResponse()
            .getContentAsString();
        long reconciliationId = objectMapper.readTree(drillBody).path("reconciliationId").asLong();
        long paymentOrderId = objectMapper.readTree(drillBody).path("paymentOrderId").asLong();
        long receiptId = objectMapper.readTree(drillBody).path("receiptId").asLong();
        String orderNo = jdbc.sql("select order_no from payment_order where id = :id")
            .param("id", paymentOrderId)
            .query(String.class)
            .single();
        Long documentId = jdbc.sql("""
                select id from expense_document
                where verification_status = 'VERIFIED'
                order by id limit 1
                """)
            .query(Long.class)
            .single();
        Long documentExpenseId = jdbc.sql("select expense_id from expense_document where id = :id")
            .param("id", documentId)
            .query(Long.class)
            .single();
        mockMvc.perform(get("/api/acceptance/checklist")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.completionScore", equalTo(100)))
            .andExpect(jsonPath("$.items[?(@.module=='物业收费闭环')].status", hasItem("DONE")))
            .andExpect(jsonPath("$.items[?(@.module=='政府监管闭环')].status", hasItem("DONE")))
            .andExpect(jsonPath("$.items[?(@.module=='公共收益与审批')].status", hasItem("DONE")))
            .andExpect(jsonPath("$.items[?(@.module=='住户服务闭环')].status", hasItem("DONE")))
            .andExpect(jsonPath("$.items[?(@.module=='信创与数据交换')].status", hasItem("DONE")))
            .andExpect(jsonPath("$.items[?(@.module=='API网关治理')].status", hasItem("DONE")))
            .andExpect(jsonPath("$.items[?(@.module=='部署运行就绪')].status", hasItem("DONE")))
            .andExpect(jsonPath("$.items[?(@.module=='外部接口与安全')].status", hasItem("DONE")));
        mockMvc.perform(get("/api/expenses/" + documentExpenseId + "/documents/" + documentId + "/download")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", containsString("expense-document-")))
            .andExpect(content().contentType("application/pdf"));

        String paymentCallbackNoncePrefix = "pay-callback-" + orderNo + "-" + System.nanoTime();
        mockMvc.perform(post("/api/payments/wechat/callback")
                .contentType("application/json")
                .header("X-Callback-Timestamp", String.valueOf(java.time.Instant.now().getEpochSecond()))
                .header("X-Callback-Nonce", paymentCallbackNoncePrefix + "-1")
                .content("{\"orderNo\":\"" + orderNo + "\",\"status\":\"SUCCESS\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUCCESS"));
        mockMvc.perform(post("/api/payments/wechat/callback")
                .contentType("application/json")
                .header("X-Callback-Timestamp", String.valueOf(java.time.Instant.now().getEpochSecond()))
                .header("X-Callback-Nonce", paymentCallbackNoncePrefix + "-2")
                .content("{\"orderNo\":\"" + orderNo + "\",\"status\":\"SUCCESS\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.idempotent").value(true));
        Integer callbackFlowCount = jdbc.sql("select count(*) from bank_flow where trace_no = :traceNo")
            .param("traceNo", orderNo)
            .query(Integer.class)
            .single();
        org.assertj.core.api.Assertions.assertThat(callbackFlowCount).isEqualTo(1);
        Integer duplicateCallbackCount = jdbc.sql("""
                select count(*) from external_callback_receipt
                where business_no = :orderNo and process_status = 'DUPLICATE'
                """)
            .param("orderNo", orderNo)
            .query(Integer.class)
            .single();
        org.assertj.core.api.Assertions.assertThat(duplicateCallbackCount).isGreaterThanOrEqualTo(1);
        Integer nonceCount = jdbc.sql("""
                select count(*) from external_callback_nonce
                where business_no = :orderNo and event_type = 'PAYMENT_CALLBACK'
                """)
            .param("orderNo", orderNo)
            .query(Integer.class)
            .single();
        org.assertj.core.api.Assertions.assertThat(nonceCount).isGreaterThanOrEqualTo(2);
        mockMvc.perform(post("/api/payments/wechat/callback")
                .contentType("application/json")
                .header("X-Callback-Timestamp", String.valueOf(java.time.Instant.now().getEpochSecond()))
                .header("X-Callback-Nonce", paymentCallbackNoncePrefix + "-2")
                .content("{\"orderNo\":\"" + orderNo + "\",\"status\":\"SUCCESS\"}"))
            .andExpect(status().isConflict());
        mockMvc.perform(post("/api/payments/wechat/callback")
                .contentType("application/json")
                .header("X-Callback-Timestamp", String.valueOf(java.time.Instant.now().minusSeconds(3600).getEpochSecond()))
                .header("X-Callback-Nonce", "pay-callback-" + orderNo + "-stale")
                .content("{\"orderNo\":\"" + orderNo + "\",\"status\":\"SUCCESS\"}"))
            .andExpect(status().isConflict());

        mockMvc.perform(get("/api/bank/reconciliations/" + reconciliationId + "/details/export")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"reconciliation-" + reconciliationId + "-details.csv\""))
            .andExpect(content().contentType("text/csv;charset=UTF-8"));
        mockMvc.perform(get("/api/bank/flows")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()", greaterThan(0)));
        mockMvc.perform(get("/api/bank/flows/export")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"bank-flows.csv\""))
            .andExpect(content().contentType("text/csv;charset=UTF-8"));
        mockMvc.perform(get("/api/registrations/export")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"registration-applications.csv\""))
            .andExpect(content().contentType("text/csv;charset=UTF-8"));
        mockMvc.perform(get("/api/authorizations/export")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"data-authorizations.csv\""))
            .andExpect(content().contentType("text/csv;charset=UTF-8"));

        mockMvc.perform(get("/api/acceptance/checklist/export")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"acceptance-checklist.csv\""))
            .andExpect(content().contentType("text/csv;charset=UTF-8"));
        mockMvc.perform(get("/api/audit/export-logs")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].exportModule", hasItem("ACCEPTANCE")))
            .andExpect(jsonPath("$[*].exportModule", hasItem("SUPERVISION_ALERT")))
            .andExpect(jsonPath("$[*].exportModule", hasItem("COMMUNITY_CREDIT_SCORE")))
            .andExpect(jsonPath("$[*].exportModule", hasItem("BANK_RECONCILIATION")))
            .andExpect(jsonPath("$[*].exportModule", hasItem("BANK_FLOW")))
            .andExpect(jsonPath("$[*].exportModule", hasItem("REGISTRATION_APPLICATION")))
            .andExpect(jsonPath("$[*].exportModule", hasItem("DATA_AUTHORIZATION")))
            .andExpect(jsonPath("$[*].dataScope", hasItem(startsWith("COMMUNITY:"))))
            .andExpect(jsonPath("$[*].dataScope", hasItem("AUTHORIZED_COMMUNITIES")))
            .andExpect(jsonPath("$[*].filterSummary", hasItem(containsString("rows="))));

        mockMvc.perform(post("/api/integrations/security-checks/run")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total", greaterThan(0)));
        mockMvc.perform(post("/api/integrations/identity/test")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content("""
                    {
                      "name": "张女士",
                      "phone": "13800001024",
                      "identityNo": "420101199001012381",
                      "expectedName": "张女士",
                      "expectedPhoneMask": "138****1024",
                      "expectedIdentityMask": "4201**********2381"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.verified").value(true))
            .andExpect(jsonPath("$.result").value("VERIFIED"));
        mockMvc.perform(get("/api/integrations/security-checks")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()", greaterThan(0)));
        mockMvc.perform(get("/api/integrations/security-checks/export")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"integration-security-checks.csv\""))
            .andExpect(content().contentType("text/csv;charset=UTF-8"));
        mockMvc.perform(get("/api/integrations/production-readiness")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", notNullValue()))
            .andExpect(jsonPath("$.checks.length()", greaterThan(0)));
        mockMvc.perform(get("/api/integrations/production-readiness/export")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"integration-production-readiness.csv\""))
            .andExpect(content().contentType("text/csv;charset=UTF-8"));
        mockMvc.perform(get("/api/integrations/deployment-readiness")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].component", hasItem("MySQL/Flyway 数据库")))
            .andExpect(jsonPath("$.items[*].component", hasItem("Redis 缓存/会话预留")))
            .andExpect(jsonPath("$.items[*].component", hasItem("MinIO 附件存储预留")))
            .andExpect(jsonPath("$.items[*].component", hasItem("Web/H5 CORS 来源配置")));
        mockMvc.perform(get("/api/integrations/deployment-readiness/export")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"deployment-readiness.csv\""))
            .andExpect(content().contentType("text/csv;charset=UTF-8"));
        mockMvc.perform(get("/api/integrations/database-compatibility")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentVendor", notNullValue()))
            .andExpect(jsonPath("$.items[*].vendorCode", hasItem("DM")))
            .andExpect(jsonPath("$.items[*].vendorCode", hasItem("KINGBASE")));
        mockMvc.perform(get("/api/integrations/database-compatibility/export")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"database-compatibility.csv\""))
            .andExpect(content().contentType("text/csv;charset=UTF-8"));
        mockMvc.perform(post("/api/integrations/data-exchange/packages")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("""
                    {
                      "targetParty": "GOVERNMENT",
                      "dataDomains": ["BILLING", "BANK_FLOW", "PUBLIC_REVENUE", "RISK_ALERT", "CREDIT_SCORE", "EXPORT_LOG"]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.packageNo", notNullValue()))
            .andExpect(jsonPath("$.status").value("READY"));
        mockMvc.perform(get("/api/integrations/data-exchange/packages")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].targetParty", hasItem("GOVERNMENT")));
        mockMvc.perform(get("/api/integrations/data-exchange/packages/export")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"data-exchange-packages.csv\""))
            .andExpect(content().contentType("text/csv;charset=UTF-8"));
        String streetToken = tokenFrom(mockMvc.perform(post("/api/auth/login")
                .contentType("application/json")
                .content("{\"username\":\"street\",\"password\":\"admin123\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
        mockMvc.perform(get("/api/integrations/data-exchange/packages?communityId=3")
                .header("Authorization", "Bearer " + streetToken))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/integrations/data-exchange/packages")
                .header("Authorization", "Bearer " + streetToken)
                .contentType("application/json")
                .content("""
                    {
                      "targetParty": "GOVERNMENT",
                      "communityId": 3,
                      "dataDomains": ["BILLING", "BANK_FLOW"]
                    }
                    """))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/integrations/callbacks")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].eventType", hasItem("PAYMENT_CALLBACK")))
            .andExpect(jsonPath("$[*].processStatus", hasItem("DUPLICATE")));
        mockMvc.perform(get("/api/integrations/callbacks/export")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"external-callback-receipts.csv\""))
            .andExpect(content().contentType("text/csv;charset=UTF-8"));
        mockMvc.perform(get("/api/integrations/callback-nonces")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].eventType", hasItem("PAYMENT_CALLBACK")))
            .andExpect(jsonPath("$[*].businessNo", hasItem(orderNo)));
        mockMvc.perform(get("/api/integrations/callback-nonces/export")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"external-callback-nonces.csv\""))
            .andExpect(content().contentType("text/csv;charset=UTF-8"));
        mockMvc.perform(get("/api/integrations/sensitive-fields")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].protection", hasItem("MASKED_VERIFIED")))
            .andExpect(jsonPath("$[*].actor", hasItem("admin")));
        mockMvc.perform(get("/api/integrations/sensitive-fields/export")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"sensitive-field-audits.csv\""))
            .andExpect(content().contentType("text/csv;charset=UTF-8"));
        mockMvc.perform(get("/api/audit/export-logs")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].exportModule", hasItem("INTEGRATION_SECURITY")))
            .andExpect(jsonPath("$[*].exportModule", hasItem("INTEGRATION_READINESS")))
            .andExpect(jsonPath("$[*].exportModule", hasItem("DEPLOYMENT_READINESS")))
            .andExpect(jsonPath("$[*].exportModule", hasItem("EXTERNAL_CALLBACK")))
            .andExpect(jsonPath("$[*].exportModule", hasItem("EXTERNAL_CALLBACK_NONCE")))
            .andExpect(jsonPath("$[*].exportModule", hasItem("SENSITIVE_FIELD_AUDIT")))
            .andExpect(jsonPath("$[*].exportModule", hasItem("DATABASE_COMPATIBILITY")))
            .andExpect(jsonPath("$[*].exportModule", hasItem("DATA_EXCHANGE")))
            .andExpect(jsonPath("$[*].dataScope", hasItem(startsWith("TENANT:"))))
            .andExpect(jsonPath("$[*].filterSummary", hasItem(containsString("target="))));
        mockMvc.perform(get("/api/integrations/calls")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].operation", hasItem("VERIFY_OWNER")));

        String propertyBody = mockMvc.perform(post("/api/auth/login")
                .contentType("application/json")
                .content("{\"username\":\"property\",\"password\":\"admin123\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token", notNullValue()))
            .andReturn()
            .getResponse()
            .getContentAsString();
        String propertyToken = tokenFrom(propertyBody);
        mockMvc.perform(get("/api/auth/capabilities")
                .header("Authorization", "Bearer " + propertyToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.navKeys", hasItem("billing")))
            .andExpect(jsonPath("$.capabilities.length()", greaterThan(0)));
        mockMvc.perform(get("/api/integrations/configs")
                .header("Authorization", "Bearer " + propertyToken))
            .andExpect(status().isForbidden());

        String ownerBody = mockMvc.perform(post("/api/auth/login")
                .contentType("application/json")
                .content("{\"username\":\"owner\",\"password\":\"admin123\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token", notNullValue()))
            .andReturn()
            .getResponse()
            .getContentAsString();
        String ownerToken = tokenFrom(ownerBody);
        mockMvc.perform(get("/api/owners/portal")
                .header("Authorization", "Bearer " + ownerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.messages.length()", greaterThan(0)))
            .andExpect(jsonPath("$.messages[0].readStatus", notNullValue()))
            .andExpect(jsonPath("$.receipts.length()", greaterThan(0)))
            .andExpect(jsonPath("$.receipts[0].receiptNo", startsWith("EPR-")));
        mockMvc.perform(get("/api/payments/receipts")
                .header("Authorization", "Bearer " + ownerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].receiptNo", hasItem(startsWith("EPR-"))));
        mockMvc.perform(get("/api/payments/receipts/" + receiptId + "/pdf")
                .header("Authorization", "Bearer " + ownerToken))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", containsString(".pdf")));
        mockMvc.perform(get("/api/owners/portal?houseId=3")
                .header("Authorization", "Bearer " + ownerToken))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/messages/1/read")
                .header("Authorization", "Bearer " + ownerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("READ"));

        String unboundUsername = "owner_unbound_" + System.currentTimeMillis();
        mockMvc.perform(post("/api/resident/register")
                .contentType("application/json")
                .content("""
                    {
                      "username": "%s",
                      "password": "admin123",
                      "name": "未绑定业主",
                      "phone": "13800007777"
                    }
                    """.formatted(unboundUsername)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REGISTERED"));
        String unboundOwnerBody = mockMvc.perform(post("/api/auth/login")
                .contentType("application/json")
                .content("{\"username\":\"" + unboundUsername + "\",\"password\":\"admin123\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        mockMvc.perform(get("/api/owners/portal")
                .header("Authorization", "Bearer " + tokenFrom(unboundOwnerBody)))
            .andExpect(status().isForbidden());
    }

    private String tokenFrom(String body) throws Exception {
        return objectMapper.readTree(body).get("token").asText();
    }
}
