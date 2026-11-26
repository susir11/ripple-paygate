package com.susir.paygate.payment.esewa;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EsewaServiceTest {

    static final EsewaProperties SANDBOX = new EsewaProperties(
            "EPAYTEST",
            "8gBm/:&EnhH.1/q",
            "https://rc-epay.esewa.com.np/api/epay/main/v2/form",
            "https://rc.esewa.com.np/api/epay/transaction/status/",
            "http://localhost:8080/api/payments/esewa/success",
            "http://localhost:8080/api/payments/esewa/failure",
            "http://localhost:3000/result.html");

    static final String RESPONSE_SIGNED_FIELDS =
            "transaction_code,status,total_amount,transaction_uuid,product_code,signed_field_names";

    private final EsewaService service = new EsewaService(SANDBOX, RestClient.builder());

    // eSewa's docs show "4Ov7pCI1zIOdwtV2BRMUNjz1upIlT/COTxfLhWvVurE=" for this input, but that
    // value doesn't reproduce. This expected value was computed independently with:
    //   printf '%s' 'total_amount=100,transaction_uuid=11-201-13,product_code=EPAYTEST' \
    //     | openssl dgst -sha256 -hmac '8gBm/:&EnhH.1/q' -binary | base64
    @Test
    void sign_matchesOpensslHmacSha256() {
        assertEquals("5DZywcrTKD0gia/rsSMcrRHmJl+4Tbol6S+lWgdJ94E=",
                service.sign("total_amount=100,transaction_uuid=11-201-13,product_code=EPAYTEST"));
    }

    @Test
    void buildFormFields_signsTotalUuidProductCode() {
        Map<String, String> f = service.buildFormFields("abc-123", new BigDecimal("100.00"));

        assertEquals("100", f.get("amount"));
        assertEquals("100", f.get("total_amount"));
        assertEquals("0", f.get("tax_amount"));
        assertEquals("0", f.get("product_service_charge"));
        assertEquals("0", f.get("product_delivery_charge"));
        assertEquals("abc-123", f.get("transaction_uuid"));
        assertEquals("EPAYTEST", f.get("product_code"));
        assertEquals("http://localhost:8080/api/payments/esewa/success", f.get("success_url"));
        assertEquals("http://localhost:8080/api/payments/esewa/failure?txn=abc-123", f.get("failure_url"));
        assertEquals("total_amount,transaction_uuid,product_code", f.get("signed_field_names"));
        assertEquals(service.sign("total_amount=100,transaction_uuid=abc-123,product_code=EPAYTEST"),
                f.get("signature"));
        assertFalse(f.containsValue("8gBm/:&EnhH.1/q"), "secret must never be sent");
    }

    @Test
    void formatAmount_isTheSameWhateverTheScale() {
        // The DB column has 2 decimal places, the request may have 0 or 1. All must
        // produce the same text, because that text is what gets signed.
        assertEquals("100", EsewaService.formatAmount(new BigDecimal("100")));
        assertEquals("100", EsewaService.formatAmount(new BigDecimal("100.0")));
        assertEquals("100", EsewaService.formatAmount(new BigDecimal("100.00")));
        assertEquals("100.5", EsewaService.formatAmount(new BigDecimal("100.50")));
        assertEquals("1000", EsewaService.formatAmount(new BigDecimal("1E+3")));
    }

    @Test
    void verifyCallback_validSignature() {
        String data = callback("\"1,000.0\"", "total_amount=1,000.0");

        EsewaCallback cb = service.parseCallback(data);

        assertTrue(cb.signatureValid());
        assertEquals("COMPLETE", cb.status());
        assertEquals("abc-123", cb.transactionUuid());
        assertEquals("1,000.0", cb.totalAmount());
        assertEquals("000AWEO", cb.transactionCode());
    }

    @Test
    void verifyCallback_usesRawTextOfNumbers() {
        // eSewa may send total_amount as a JSON number instead of a string
        EsewaCallback cb = service.parseCallback(callback("1000.0", "total_amount=1000.0"));

        assertTrue(cb.signatureValid());
        assertEquals("1000.0", cb.totalAmount());
    }

    @Test
    void verifyCallback_tamperedAmount_invalid() {
        String signedFor100 = callbackJson("\"100.0\"", signatureFor("total_amount=100.0"));
        String tampered = signedFor100.replace("\"100.0\"", "\"1.0\"");

        EsewaCallback cb = service.parseCallback(b64(tampered));

        assertFalse(cb.signatureValid());
    }

    @Test
    void verifyCallback_garbage_invalidNoThrow() {
        assertFalse(service.parseCallback("not-base64!!").signatureValid());
        assertFalse(service.parseCallback(b64("{not json")).signatureValid());
        assertFalse(service.parseCallback(null).signatureValid());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String callback(String totalAmountJson, String totalAmountPair) {
        return b64(callbackJson(totalAmountJson, signatureFor(totalAmountPair)));
    }

    private String signatureFor(String totalAmountPair) {
        return service.sign("transaction_code=000AWEO,status=COMPLETE," + totalAmountPair
                + ",transaction_uuid=abc-123,product_code=EPAYTEST,signed_field_names=" + RESPONSE_SIGNED_FIELDS);
    }

    private static String callbackJson(String totalAmountJson, String signature) {
        return "{\"transaction_code\":\"000AWEO\",\"status\":\"COMPLETE\",\"total_amount\":" + totalAmountJson
                + ",\"transaction_uuid\":\"abc-123\",\"product_code\":\"EPAYTEST\",\"signed_field_names\":\""
                + RESPONSE_SIGNED_FIELDS + "\",\"signature\":\"" + signature + "\"}";
    }

    private static String b64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }
}
