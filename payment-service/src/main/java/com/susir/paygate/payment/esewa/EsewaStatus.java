package com.susir.paygate.payment.esewa;

/**
 * eSewa's answer from its status API: the status (COMPLETE, PENDING, NOT_FOUND, ...)
 * or "ERROR" if eSewa couldn't be reached, plus eSewa's reference id when there is one.
 */
public record EsewaStatus(String status, String refId) {

    static EsewaStatus error() {
        return new EsewaStatus("ERROR", null);
    }
}
