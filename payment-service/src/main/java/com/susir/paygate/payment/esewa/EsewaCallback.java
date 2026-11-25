package com.susir.paygate.payment.esewa;

/**
 * What eSewa told us when it redirected the user back to our success URL.
 * signatureValid is false if the data was malformed or tampered with —
 * in that case the other fields may be null and must not be trusted.
 */
public record EsewaCallback(
        boolean signatureValid,
        String status,
        String transactionUuid,
        String totalAmount,
        String transactionCode
) {
    static EsewaCallback invalid() {
        return new EsewaCallback(false, null, null, null, null);
    }
}
