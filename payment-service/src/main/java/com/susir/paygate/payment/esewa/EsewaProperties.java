package com.susir.paygate.payment.esewa;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * eSewa ePay v2 settings, bound from the "esewa:" block in application.yml.
 * Defaults there point at eSewa's public sandbox (UAT) merchant EPAYTEST.
 * The secret key stays on the server — it is only used to sign and verify.
 */
@ConfigurationProperties(prefix = "esewa")
public record EsewaProperties(
        String productCode,
        String secretKey,
        String formUrl,
        String statusUrl,
        String successUrl,
        String failureUrl,
        String resultPageUrl
) {
}
