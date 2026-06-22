package com.susir.paygate.api_gateway;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

// This controller is called by the gateway when the circuit is OPEN —
// meaning payment-service has failed too many times and requests are
// being blocked to give it time to recover.
//
// In remittance terms: like a suspended corridor message.
// Instead of hanging indefinitely, the sender immediately gets a
// clean "service temporarily unavailable" response.
// This protects both the user experience and the downstream service.
@RestController
public class FallbackController {

    // This endpoint is never called directly by users.
    // The gateway routes here automatically when the circuit opens.
    @RequestMapping("/fallback/payment")
    public Mono<ResponseEntity<Map<String, String>>> paymentFallback() {
        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "status", "error",
                        "message", "Payment service is temporarily unavailable. Please try again shortly.",
                        "code", "CIRCUIT_OPEN"
                )));
    }
}