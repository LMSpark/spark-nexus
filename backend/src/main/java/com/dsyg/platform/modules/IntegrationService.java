package com.dsyg.platform.modules;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class IntegrationService {
    private final String mode;
    private final String wechatCallbackSecret;
    private final String alipayCallbackSecret;
    private final String bankCallbackSecret;
    private final String identityApiKey;
    private final long callbackMaxSkewSeconds;
    private final JdbcClient jdbc;

    public IntegrationService(
        @Value("${platform.integrations.mode}") String mode,
        @Value("${platform.integrations.callback-max-skew-seconds:300}") long callbackMaxSkewSeconds,
        @Value("${platform.integrations.wechat.callback-secret:}") String wechatCallbackSecret,
        @Value("${platform.integrations.alipay.callback-secret:}") String alipayCallbackSecret,
        @Value("${platform.integrations.bank.callback-secret:}") String bankCallbackSecret,
        @Value("${platform.integrations.identity.api-key:}") String identityApiKey,
        JdbcClient jdbc
    ) {
        this.mode = mode;
        this.callbackMaxSkewSeconds = callbackMaxSkewSeconds;
        this.wechatCallbackSecret = wechatCallbackSecret;
        this.alipayCallbackSecret = alipayCallbackSecret;
        this.bankCallbackSecret = bankCallbackSecret;
        this.identityApiKey = identityApiKey;
        this.jdbc = jdbc;
    }

    public Map<String, Object> createWechatPrepay(long billId, BigDecimal amount, String description) {
        Map<String, Object> result = Map.of(
            "mode", mode,
            "provider", "WECHAT_PAY",
            "billId", billId,
            "amount", amount,
            "description", description,
            "prepayId", "dev".equals(mode) ? "wx_dev_" + UUID.randomUUID() : "pending-real-wechat-sdk",
            "nonceStr", UUID.randomUUID().toString().replace("-", ""),
            "signature", "dev-signature"
        );
        logCall("WECHAT_PAY", "CREATE_PREPAY", "billId=" + billId + ", amount=" + amount, result.toString(), "SUCCESS");
        return result;
    }

    public CallbackReplayCheck verifyCallbackReplay(String provider, String eventType, String businessNo, String timestampHeader, String nonce) {
        if ("dev".equals(mode) && (timestampHeader == null || timestampHeader.isBlank() || nonce == null || nonce.isBlank())) {
            return new CallbackReplayCheck(true, "DEV_BYPASS", "dev 模式允许缺省时间戳和 nonce");
        }
        if (timestampHeader == null || timestampHeader.isBlank()) {
            return new CallbackReplayCheck(false, "MISSING_TIMESTAMP", "回调缺少时间戳");
        }
        if (nonce == null || nonce.isBlank()) {
            return new CallbackReplayCheck(false, "MISSING_NONCE", "回调缺少 nonce");
        }
        long callbackTimestamp;
        try {
            callbackTimestamp = Long.parseLong(timestampHeader.trim());
        } catch (NumberFormatException ex) {
            return new CallbackReplayCheck(false, "INVALID_TIMESTAMP", "回调时间戳不是 Unix 秒");
        }
        long skew = Math.abs(Instant.now().getEpochSecond() - callbackTimestamp);
        if (skew > callbackMaxSkewSeconds) {
            return new CallbackReplayCheck(false, "STALE_TIMESTAMP", "回调时间戳超出允许窗口 " + callbackMaxSkewSeconds + " 秒");
        }
        try {
            jdbc.sql("""
                insert into external_callback_nonce(adapter_code, event_type, business_no, nonce_value, callback_timestamp, received_at)
                values(:adapterCode, :eventType, :businessNo, :nonce, :callbackTimestamp, now())
                """)
                .param("adapterCode", provider)
                .param("eventType", eventType)
                .param("businessNo", businessNo == null ? "" : businessNo)
                .param("nonce", nonce.trim())
                .param("callbackTimestamp", callbackTimestamp)
                .update();
        } catch (DuplicateKeyException ex) {
            return new CallbackReplayCheck(false, "REPLAY_NONCE", "nonce 已使用，疑似重放");
        }
        return new CallbackReplayCheck(true, "ACCEPTED", "时间戳和 nonce 校验通过，偏差 " + skew + " 秒");
    }

    public Map<String, Object> createAlipayTrade(long billId, BigDecimal amount, String description) {
        String tradeNo = "dev".equals(mode) ? "ali_dev_" + UUID.randomUUID() : "pending-real-alipay-sdk";
        Map<String, Object> result = Map.of(
            "mode", mode,
            "provider", "ALIPAY",
            "billId", billId,
            "amount", amount,
            "description", description,
            "tradeNo", tradeNo,
            "payUrl", "dev".equals(mode) ? "https://dev.alipay.local/pay/" + tradeNo : "pending-real-alipay-pay-url",
            "signature", "dev-alipay-signature"
        );
        logCall("ALIPAY", "CREATE_TRADE", "billId=" + billId + ", amount=" + amount, result.toString(), "SUCCESS");
        return result;
    }

    public Map<String, Object> verifyPaymentCallback(String provider, String orderNo, String payload) {
        Map<String, Object> result = Map.of(
            "mode", mode,
            "provider", provider,
            "orderNo", orderNo,
            "verified", "dev".equals(mode),
            "result", "dev".equals(mode) ? "CALLBACK_VERIFIED" : "PENDING_REAL_SIGNATURE_VERIFY"
        );
        logCall(provider, "VERIFY_PAYMENT_CALLBACK", "orderNo=" + orderNo + ", payload=" + limit(payload), result.toString(), "SUCCESS");
        return result;
    }

    public Map<String, Object> verifyExternalCallback(String provider, String eventType, String businessNo, String payload, String signature) {
        boolean verified = "dev".equals(mode) || expectedCallbackSignature(provider, businessNo, payload).equals(signature == null ? "" : signature.trim());
        String signatureStatus = verified ? "VERIFIED" : ((signature == null || signature.isBlank()) ? "MISSING_SIGNATURE" : "INVALID_SIGNATURE");
        Map<String, Object> result = Map.of(
            "mode", mode,
            "provider", provider,
            "eventType", eventType,
            "businessNo", businessNo,
            "verified", verified,
            "signatureStatus", signatureStatus,
            "result", verified ? "CALLBACK_VERIFIED" : "CALLBACK_REJECTED"
        );
        logCall(provider, "VERIFY_EXTERNAL_CALLBACK", "eventType=" + eventType + ", businessNo=" + businessNo + ", signature=" + maskSignature(signature), result.toString(), verified ? "SUCCESS" : "FAILED");
        return result;
    }

    public String canonicalPayload(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return "";
        }
        return new TreeMap<>(payload).entrySet().stream()
            .map(entry -> entry.getKey() + "=" + canonicalValue(entry.getValue()))
            .collect(Collectors.joining("&"));
    }

    public String expectedCallbackSignature(String provider, String businessNo, String payload) {
        String secret = callbackSecret(provider);
        if (secret == null || secret.isBlank()) {
            return "";
        }
        String text = (businessNo == null ? "" : businessNo) + "|" + (payload == null ? "" : payload);
        return hmacSha256(secret, text);
    }

    public Map<String, Object> refundPayment(String provider, String orderNo, BigDecimal amount, String reason) {
        String refundNoPrefix = "ALIPAY".equals(provider) ? "alipay_refund_" : "wx_refund_";
        String refundNo = "dev".equals(mode) ? refundNoPrefix + UUID.randomUUID() : "pending-real-refund-sdk";
        Map<String, Object> result = Map.of(
            "mode", mode,
            "provider", provider,
            "orderNo", orderNo,
            "amount", amount,
            "reason", reason,
            "refundNo", refundNo,
            "result", "dev".equals(mode) ? "REFUND_ACCEPTED" : "PENDING_REAL_REFUND"
        );
        logCall(provider, "REFUND_PAYMENT", "orderNo=" + orderNo + ", amount=" + amount + ", reason=" + reason, result.toString(), "SUCCESS");
        return result;
    }

    public Map<String, Object> issueTaxInvoice(String requestNo, String buyerName, String buyerTaxNo, String invoiceItem, String taxCategoryCode, BigDecimal taxRate, BigDecimal amount) {
        String taxInvoiceNo = "dev".equals(mode) ? "DT" + System.currentTimeMillis() : "PENDING_TAX_PLATFORM_INVOICE_NO";
        String platformCode = "dev".equals(mode) ? "LEQI_DEV_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) : "PENDING_TAX_PLATFORM_CODE";
        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("mode", mode);
        result.put("provider", "TAX_DIGITAL");
        result.put("requestNo", requestNo);
        result.put("buyerName", buyerName);
        result.put("buyerTaxNo", buyerTaxNo);
        result.put("invoiceItem", invoiceItem);
        result.put("taxCategoryCode", taxCategoryCode);
        result.put("taxRate", taxRate);
        result.put("amount", amount);
        result.put("taxInvoiceNo", taxInvoiceNo);
        result.put("taxPlatformCode", platformCode);
        result.put("pdfUrl", "dev".equals(mode) ? "mock://tax-invoice/" + taxInvoiceNo + ".pdf" : "PENDING_REAL_TAX_PDF_URL");
        result.put("result", "dev".equals(mode) ? "TAX_INVOICE_ISSUED" : "PENDING_REAL_TAX_PLATFORM");
        logCall("TAX_DIGITAL", "ISSUE_INVOICE", "requestNo=" + requestNo + ", amount=" + amount + ", buyerTaxNo=" + buyerTaxNo, result.toString(), "SUCCESS");
        return result;
    }

    public Map<String, Object> redCancelTaxInvoice(String requestNo, String taxInvoiceNo, String reason) {
        String redInvoiceNo = "dev".equals(mode) ? "RED" + System.currentTimeMillis() : "PENDING_REAL_RED_INVOICE_NO";
        Map<String, Object> result = Map.of(
            "mode", mode,
            "provider", "TAX_DIGITAL",
            "requestNo", requestNo,
            "taxInvoiceNo", taxInvoiceNo,
            "redInvoiceNo", redInvoiceNo,
            "reason", reason,
            "result", "dev".equals(mode) ? "RED_CANCEL_ACCEPTED" : "PENDING_REAL_RED_CANCEL"
        );
        logCall("TAX_DIGITAL", "RED_CANCEL_INVOICE", "requestNo=" + requestNo + ", taxInvoiceNo=" + taxInvoiceNo + ", reason=" + reason, result.toString(), "SUCCESS");
        return result;
    }

    public Map<String, Object> syncPaymentStatement(String provider, String statementDate, int tradeCount, BigDecimal totalAmount) {
        Map<String, Object> result = Map.of(
            "mode", mode,
            "provider", provider,
            "statementDate", statementDate,
            "tradeCount", tradeCount,
            "totalAmount", totalAmount,
            "result", "dev".equals(mode) ? "STATEMENT_SYNCED" : "PENDING_REAL_STATEMENT_API"
        );
        logCall(provider, "SYNC_PAYMENT_STATEMENT", "statementDate=" + statementDate, result.toString(), "SUCCESS");
        return result;
    }

    public Map<String, Object> refundExpense(long expenseId) {
        Map<String, Object> result = Map.of(
            "mode", mode,
            "provider", "BANK_DIRECT",
            "expenseId", expenseId,
            "result", "dev".equals(mode) ? "REFUND_ACCEPTED" : "PENDING_BANK_API"
        );
        logCall("BANK_DIRECT", "REFUND_EXPENSE", "expenseId=" + expenseId, result.toString(), "SUCCESS");
        return result;
    }

    public Map<String, Object> executeBankDisbursement(long instructionId, long expenseId, BigDecimal amount, String payeeName) {
        Map<String, Object> result = Map.of(
            "mode", mode,
            "provider", "BANK_DIRECT",
            "instructionId", instructionId,
            "expenseId", expenseId,
            "amount", amount,
            "payeeName", payeeName,
            "bankTraceNo", "dev".equals(mode) ? "BD" + System.currentTimeMillis() : "PENDING_BANK_TRACE",
            "result", "dev".equals(mode) ? "DISBURSEMENT_ACCEPTED" : "PENDING_BANK_API"
        );
        logCall("BANK_DIRECT", "EXECUTE_DISBURSEMENT", "instructionId=" + instructionId + ", expenseId=" + expenseId + ", amount=" + amount, result.toString(), "SUCCESS");
        return result;
    }

    public Map<String, Object> verifyIdentity(String name, String phone, String identityNo, String expectedName, String expectedPhoneMask, String expectedIdentityMask) {
        boolean phoneMatched = phone != null && expectedPhoneMask != null && expectedPhoneMask.endsWith(last(phone, 4));
        boolean identityMatched = identityNo != null && expectedIdentityMask != null && expectedIdentityMask.endsWith(last(identityNo, 4));
        boolean nameMatched = expectedName != null && expectedName.equals(name);
        boolean contractMatched = nameMatched && phoneMatched && identityMatched;
        boolean missingRealConfig = !"dev".equals(mode) && (identityApiKey == null || identityApiKey.isBlank());
        boolean passed = "dev".equals(mode) ? contractMatched : !missingRealConfig && contractMatched;
        Map<String, Object> result = Map.of(
            "mode", mode,
            "provider", "IDENTITY",
            "verified", passed,
            "nameMatched", nameMatched,
            "phoneMatched", phoneMatched,
            "identityMatched", identityMatched,
            "result", passed ? ("dev".equals(mode) ? "VERIFIED" : "REAL_ADAPTER_CONTRACT_VERIFIED") : (missingRealConfig ? "MISSING_IDENTITY_API_KEY" : "VERIFY_FAILED")
        );
        logCall("IDENTITY", "VERIFY_OWNER", "name=" + name + ", phoneTail=" + last(phone, 4), result.toString(), passed ? "SUCCESS" : "FAILED");
        return result;
    }

    public Map<String, Object> sendSms(String phone, String templateCode, String content) {
        String maskedPhone = phone == null || phone.length() < 7 ? "" : phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
        Map<String, Object> result = Map.of(
            "mode", mode,
            "provider", "SMS",
            "templateCode", templateCode,
            "phone", maskedPhone,
            "result", "dev".equals(mode) ? "SMS_DEV_SENT" : "PENDING_REAL_SMS"
        );
        logCall("SMS", "SEND_SMS", "phone=" + maskedPhone + ", template=" + templateCode + ", content=" + content, result.toString(), "SUCCESS");
        return result;
    }

    public Map<String, Object> sendWechatCommunityNotice(long communityId, String receiverRole, String templateCode, String title, String content, String sponsor) {
        Map<String, Object> result = Map.of(
            "mode", mode,
            "provider", "WECHAT_MESSAGE",
            "communityId", communityId,
            "receiverRole", receiverRole == null || receiverRole.isBlank() ? "OWNER" : receiverRole,
            "templateCode", templateCode == null || templateCode.isBlank() ? "COMMUNITY_GOVERNANCE_NOTICE" : templateCode,
            "sponsor", sponsor == null || sponsor.isBlank() ? "社区治理通知" : sponsor,
            "result", "dev".equals(mode) ? "WECHAT_MESSAGE_DEV_SENT" : "PENDING_REAL_WECHAT_MESSAGE"
        );
        logCall(
            "WECHAT_MESSAGE",
            "SEND_COMMUNITY_NOTICE",
            "communityId=" + communityId + ", receiverRole=" + receiverRole + ", template=" + templateCode + ", sponsor=" + sponsor,
            result + ", title=" + title + ", content=" + content,
            "SUCCESS"
        );
        return result;
    }

    private void logCall(String adapterCode, String operation, String requestSummary, String responseSummary, String status) {
        jdbc.sql("""
            insert into integration_call_log(adapter_code, operation, request_summary, response_summary, status, trace_no, created_at)
            values(:adapterCode, :operation, :requestSummary, :responseSummary, :status, :traceNo, now())
            """)
            .param("adapterCode", adapterCode)
            .param("operation", operation)
            .param("requestSummary", limit(requestSummary))
            .param("responseSummary", limit(responseSummary))
            .param("status", status)
            .param("traceNo", adapterCode + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16))
            .update();
    }

    private String limit(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 900 ? value : value.substring(0, 900);
    }

    private String callbackSecret(String provider) {
        if ("ALIPAY".equals(provider)) {
            return alipayCallbackSecret;
        }
        if ("BANK_DIRECT".equals(provider)) {
            return bankCallbackSecret;
        }
        return wechatCallbackSecret;
    }

    private String canonicalValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Map<?, ?> mapValue) {
            return mapValue.entrySet().stream()
                .collect(Collectors.toMap(entry -> String.valueOf(entry.getKey()), Map.Entry::getValue, (left, right) -> right, TreeMap::new))
                .entrySet().stream()
                .map(entry -> entry.getKey() + "=" + canonicalValue(entry.getValue()))
                .collect(Collectors.joining("&", "{", "}"));
        }
        return String.valueOf(value).trim();
    }

    private String hmacSha256(String secret, String text) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot sign callback payload", ex);
        }
    }

    private String maskSignature(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= 10 ? "***" : trimmed.substring(0, 4) + "****" + trimmed.substring(trimmed.length() - 4);
    }

    private String last(String value, int count) {
        if (value == null) {
            return "";
        }
        String trimmed = value.replaceAll("\\s+", "");
        return trimmed.length() <= count ? trimmed : trimmed.substring(trimmed.length() - count);
    }

    public record CallbackReplayCheck(boolean accepted, String status, String message) {}
}
