package com.susir.paygate.payment.esewa;

import java.util.Map;

/** Where the browser should POST, and the exact fields (signature included) to send. */
public record EsewaInitiateResponse(String formUrl, Map<String, String> fields) {
}
