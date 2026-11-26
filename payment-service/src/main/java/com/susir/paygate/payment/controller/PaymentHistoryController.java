package com.susir.paygate.payment.controller;

import com.susir.paygate.payment.entity.Payment;
import com.susir.paygate.payment.repository.PaymentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * GET /api/payments/mine — the logged-in user's 20 most recent payments.
 * The gateway verifies the JWT and passes the username in X-Authenticated-User,
 * so a user can only ever see their own payments.
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentHistoryController {

    private final PaymentRepository payments;

    public PaymentHistoryController(PaymentRepository payments) {
        this.payments = payments;
    }

    @GetMapping("/mine")
    public ResponseEntity<List<PaymentView>> mine(
            @RequestHeader(value = "X-Authenticated-User", required = false) String username) {
        if (username == null || username.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(payments.findTop20ByUsernameOrderByIdDesc(username).stream()
                .map(PaymentView::of)
                .toList());
    }

    /** What we show the user: no internal database id, no username. */
    public record PaymentView(String transactionId, BigDecimal amount, String status,
                              String refId, Instant createdAt) {
        static PaymentView of(Payment p) {
            return new PaymentView(p.getTransactionId(), p.getAmount(), p.getStatus(),
                    p.getRefId(), p.getCreatedAt());
        }
    }
}
