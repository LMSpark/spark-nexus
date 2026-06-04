package com.dsyg.platform;

import com.dsyg.platform.modules.IntegrationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "platform.integrations.mode=prod",
    "platform.integrations.wechat.callback-secret=test-wechat-callback-secret",
    "platform.integrations.alipay.callback-secret=test-alipay-callback-secret",
    "platform.integrations.bank.callback-secret=test-bank-callback-secret",
    "platform.integrations.identity.api-key=test-identity-api-key"
})
@AutoConfigureMockMvc
class ProductionCallbackSecurityTest {
    @Autowired
    MockMvc mockMvc;
    @Autowired
    IntegrationService integrations;
    @Autowired
    JdbcClient jdbc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void productionPaymentCallbackRequiresSignatureAndRejectsReplayNonce() throws Exception {
        String token = login("admin", "admin123");
        String prepayBody = mockMvc.perform(post("/api/payments/wechat/prepay")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"billId\":1}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderNo", notNullValue()))
            .andReturn()
            .getResponse()
            .getContentAsString();
        String orderNo = objectMapper.readTree(prepayBody).path("orderNo").asText();
        String callbackJson = "{\"orderNo\":\"" + orderNo + "\",\"status\":\"SUCCESS\"}";
        String callbackPayload = "orderNo=" + orderNo + "&status=SUCCESS";
        String signature = integrations.expectedCallbackSignature("WECHAT_PAY", orderNo, callbackPayload);
        String timestamp = String.valueOf(java.time.Instant.now().getEpochSecond());
        String nonce = "prod-pay-callback-" + orderNo;

        mockMvc.perform(post("/api/integrations/callbacks/signature-diagnostics")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("""
                    {
                      "provider": "WECHAT_PAY",
                      "eventType": "PAYMENT_CALLBACK",
                      "businessNo": "%s",
                      "payload": {"status":"SUCCESS","orderNo":"%s"},
                      "signature": "%s"
                    }
                    """.formatted(orderNo, orderNo, signature)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.verified").value(true))
            .andExpect(jsonPath("$.canonicalPayload").value(callbackPayload))
            .andExpect(jsonPath("$.expectedSignature").value("生产模式不回显期望签名，请使用渠道侧签名值比对验签结果"));

        mockMvc.perform(post("/api/payments/wechat/callback")
                .contentType("application/json")
                .header("X-Callback-Timestamp", timestamp)
                .header("X-Callback-Nonce", nonce + "-missing-signature")
                .content(callbackJson))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/payments/wechat/callback")
                .contentType("application/json")
                .header("X-Signature", signature)
                .header("X-Callback-Timestamp", timestamp)
                .header("X-Callback-Nonce", nonce)
                .content(callbackJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.idempotent").value(false));

        mockMvc.perform(post("/api/payments/wechat/callback")
                .contentType("application/json")
                .header("X-Signature", signature)
                .header("X-Callback-Timestamp", timestamp)
                .header("X-Callback-Nonce", nonce)
                .content(callbackJson))
            .andExpect(status().isConflict());
    }

    @Test
    void productionBankCallbackUsesCanonicalSignatureAndIdempotency() throws Exception {
        String instructionNo = "PROD-DISB-" + System.currentTimeMillis();
        jdbc.sql("""
                insert into bank_disbursement_instruction(expense_id, community_id, fund_account_id,
                                                          instruction_no, payee_name, amount, purpose,
                                                          status, created_at)
                values(1, 1, 1, :instructionNo, '生产验签供应商', 88.88, '生产银行回执验签测试',
                       'PENDING', now())
                """)
            .param("instructionNo", instructionNo)
            .update();

        String payload = "bankTraceNo=BANK-PROD-CB-001&instructionNo=" + instructionNo + "&remark=银行生产回执验签&status=PAID";
        String signature = integrations.expectedCallbackSignature("BANK_DIRECT", instructionNo, payload);
        String timestamp = String.valueOf(java.time.Instant.now().getEpochSecond());
        String callbackJson = """
            {
              "status": "PAID",
              "remark": "银行生产回执验签",
              "instructionNo": "%s",
              "bankTraceNo": "BANK-PROD-CB-001"
            }
            """.formatted(instructionNo);

        mockMvc.perform(post("/api/bank/callbacks/disbursement")
                .contentType("application/json")
                .header("X-Bank-Timestamp", timestamp)
                .header("X-Bank-Nonce", "prod-bank-callback-" + instructionNo + "-missing-signature")
                .content(callbackJson))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/bank/callbacks/disbursement")
                .contentType("application/json")
                .header("X-Bank-Signature", signature)
                .header("X-Bank-Timestamp", timestamp)
                .header("X-Bank-Nonce", "prod-bank-callback-" + instructionNo + "-1")
                .content(callbackJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PAID"))
            .andExpect(jsonPath("$.bankTraceNo").value("BANK-PROD-CB-001"));

        mockMvc.perform(post("/api/bank/callbacks/disbursement")
                .contentType("application/json")
                .header("X-Bank-Signature", signature)
                .header("X-Bank-Timestamp", timestamp)
                .header("X-Bank-Nonce", "prod-bank-callback-" + instructionNo + "-2")
                .content(callbackJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.idempotent").value(true));

        mockMvc.perform(post("/api/bank/callbacks/disbursement")
                .contentType("application/json")
                .header("X-Bank-Signature", signature)
                .header("X-Bank-Timestamp", timestamp)
                .header("X-Bank-Nonce", "prod-bank-callback-" + instructionNo + "-2")
                .content(callbackJson))
            .andExpect(status().isConflict());
    }

    @Test
    void productionIdentityAdapterRequiresConfigAndVerifiesContract() throws Exception {
        String token = login("admin", "admin123");
        mockMvc.perform(post("/api/integrations/identity/test")
                .header("Authorization", "Bearer " + token)
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
            .andExpect(jsonPath("$.result").value("REAL_ADAPTER_CONTRACT_VERIFIED"));
    }

    private String login(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                .contentType("application/json")
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token", notNullValue()))
            .andReturn()
            .getResponse()
            .getContentAsString();
        return objectMapper.readTree(body).path("token").asText();
    }
}
