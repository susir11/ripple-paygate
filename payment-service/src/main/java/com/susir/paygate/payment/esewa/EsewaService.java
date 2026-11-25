package com.susir.paygate.payment.esewa;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Talks the eSewa ePay v2 protocol:
 *  - builds and signs the form the browser posts to eSewa
 *  - verifies the signed data eSewa sends back on the success redirect
 *
 * Signature = Base64( HMAC-SHA256( secretKey, "name=value,name=value,..." ) )
 * where the names come from signed_field_names, in that order.
 */
@Service
public class EsewaService {

    static final String REQUEST_SIGNED_FIELDS = "total_amount,transaction_uuid,product_code";

    private final EsewaProperties props;
    private final RestClient restClient;
    private final JsonMapper json = JsonMapper.builder().build();

    public EsewaService(EsewaProperties props, RestClient.Builder restClientBuilder) {
        this.props = props;
        this.restClient = restClientBuilder.build();
    }

    public String sign(String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(props.secretKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(raw);
        } catch (Exception e) {
            throw new IllegalStateException("Could not sign eSewa message", e);
        }
    }

    /** The one place amounts become strings, so what we sign is exactly what we send. */
    public static String formatAmount(Double amount) {
        return BigDecimal.valueOf(amount).toPlainString();
    }

    /** Every field the browser must POST to eSewa's form URL, including the signature. */
    public Map<String, String> buildFormFields(String transactionUuid, Double amount) {
        String total = formatAmount(amount);

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("amount", total);
        fields.put("tax_amount", "0");
        fields.put("total_amount", total);
        fields.put("transaction_uuid", transactionUuid);
        fields.put("product_code", props.productCode());
        fields.put("product_service_charge", "0");
        fields.put("product_delivery_charge", "0");
        fields.put("success_url", props.successUrl());
        // eSewa's failure redirect doesn't reliably say which payment failed,
        // so we tell ourselves by putting the id in the URL.
        fields.put("failure_url", props.failureUrl() + "?txn=" + transactionUuid);
        fields.put("signed_field_names", REQUEST_SIGNED_FIELDS);
        fields.put("signature", sign(
                "total_amount=" + total
                        + ",transaction_uuid=" + transactionUuid
                        + ",product_code=" + props.productCode()));
        return fields;
    }

    /**
     * Decodes the base64 "data" eSewa appends to the success URL and checks its signature.
     * Never throws: anything malformed comes back as an invalid callback.
     */
    public EsewaCallback parseCallback(String base64Data) {
        try {
            String decoded = new String(Base64.getDecoder().decode(base64Data), StandardCharsets.UTF_8);
            JsonNode node = json.readTree(decoded);

            String signedFieldNames = text(node, "signed_field_names");
            String signature = text(node, "signature");
            if (signedFieldNames == null || signature == null) {
                return EsewaCallback.invalid();
            }

            // Rebuild the message from the raw text eSewa sent (e.g. "1,000.0"),
            // never from re-formatted numbers, or the signature won't match.
            StringBuilder message = new StringBuilder();
            for (String name : signedFieldNames.split(",")) {
                String value = text(node, name);
                if (value == null) {
                    return EsewaCallback.invalid();
                }
                if (!message.isEmpty()) message.append(',');
                message.append(name).append('=').append(value);
            }

            boolean valid = MessageDigest.isEqual(
                    sign(message.toString()).getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8));

            return new EsewaCallback(valid,
                    text(node, "status"),
                    text(node, "transaction_uuid"),
                    text(node, "total_amount"),
                    text(node, "transaction_code"));
        } catch (Exception e) {
            return EsewaCallback.invalid();
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        return value.isValueNode() ? value.asString() : value.toString();
    }
}
