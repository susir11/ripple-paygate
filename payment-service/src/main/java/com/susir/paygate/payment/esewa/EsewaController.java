package com.susir.paygate.payment.esewa;

import com.susir.paygate.payment.entity.Payment;
import com.susir.paygate.payment.repository.PaymentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/**
 * The eSewa payment flow:
 *
 *  1. POST /initiate  (logged-in user, via gateway + JWT; the gateway tells us who
 *     it is in the X-Authenticated-User header)
 *     → save a PENDING payment, return the signed form for the browser to post to eSewa.
 *  2. User pays on eSewa's site.
 *  3. eSewa redirects the browser to GET /success?data=...  or  GET /failure?txn=...
 *     These are public (no JWT) because the browser arrives from eSewa without our token.
 *     Trust comes from eSewa's signature plus a server-to-server status check instead.
 *  4. We update the payment and redirect the browser to the frontend's result page.
 */
@RestController
@RequestMapping("/api/payments/esewa")
public class EsewaController {

    private final EsewaService esewa;
    private final PaymentRepository payments;
    private final EsewaProperties props;

    public EsewaController(EsewaService esewa, PaymentRepository payments, EsewaProperties props) {
        this.esewa = esewa;
        this.payments = payments;
        this.props = props;
    }

    // Largest amount the NUMERIC(12,2) column can hold.
    static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999.99");

    @PostMapping("/initiate")
    public ResponseEntity<EsewaInitiateResponse> initiate(
            @RequestHeader(value = "X-Authenticated-User", required = false) String username,
            @RequestBody EsewaInitiateRequest request) {
        // The gateway sets this header only after verifying the JWT.
        if (username == null || username.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!isValidAmount(request.amount())) {
            return ResponseEntity.badRequest().build();
        }

        Payment payment = new Payment();
        payment.setTransactionId(UUID.randomUUID().toString());
        payment.setAmount(request.amount());
        payment.setStatus("PENDING");
        payment.setUsername(username);
        payment.setCreatedAt(Instant.now());
        payments.save(payment);

        return ResponseEntity.ok(new EsewaInitiateResponse(
                props.formUrl(),
                esewa.buildFormFields(payment.getTransactionId(), payment.getAmount())));
    }

    @GetMapping("/success")
    public ResponseEntity<Void> success(@RequestParam(required = false) String data) {
        EsewaCallback cb = esewa.parseCallback(data);
        String txn = cb.transactionUuid();

        Payment payment = txn == null ? null : payments.findByTransactionId(txn).orElse(null);
        if (payment == null) {
            return toResultPage("failed", txn);
        }

        // Already decided (e.g. user refreshed the page): just show the outcome again.
        if (!"PENDING".equals(payment.getStatus())) {
            return toResultPage("SUCCESS".equals(payment.getStatus()) ? "success" : "failed", txn);
        }

        // Data that fails eSewa's signature didn't come from eSewa, so it must not
        // change anything — otherwise anyone who knows a transaction id could flip it.
        if (!cb.signatureValid()) {
            return toResultPage("failed", txn);
        }

        String esewaStatus = "COMPLETE".equals(cb.status()) && sameAmount(cb.totalAmount(), payment.getAmount())
                ? esewa.checkStatus(txn, EsewaService.formatAmount(payment.getAmount()))
                : "MISMATCH";

        if ("COMPLETE".equals(esewaStatus)) {
            payment.setStatus("SUCCESS");
            payment.setRefId(cb.transactionCode());
            payments.save(payment);
            return toResultPage("success", txn);
        }

        // Not a definite answer yet (eSewa unreachable, still processing, or "halted"):
        // keep it PENDING so a later visit to this same link - or the background
        // PendingPaymentReconciler - can verify again, instead of telling someone
        // who paid that it failed.
        if (EsewaService.isUndecided(esewaStatus)) {
            return toResultPage("pending", txn);
        }

        payment.setStatus("FAILED");
        payments.save(payment);
        return toResultPage("failed", txn);
    }

    @GetMapping("/failure")
    public ResponseEntity<Void> failure(@RequestParam(required = false) String txn) {
        if (txn != null) {
            payments.findByTransactionId(txn)
                    .filter(p -> "PENDING".equals(p.getStatus()))
                    .ifPresent(p -> {
                        p.setStatus("FAILED");
                        payments.save(p);
                    });
        }
        return toResultPage("failed", txn);
    }

    /** Positive, at most 2 decimal places (paisa), and small enough for the column. */
    private static boolean isValidAmount(BigDecimal amount) {
        return amount != null
                && amount.signum() > 0
                && amount.stripTrailingZeros().scale() <= 2
                && amount.compareTo(MAX_AMOUNT) <= 0;
    }

    /** eSewa may send "1,000.0" — compare as numbers, ignoring thousands separators. */
    private static boolean sameAmount(String esewaAmount, BigDecimal ourAmount) {
        if (esewaAmount == null) return false;
        try {
            return new BigDecimal(esewaAmount.replace(",", "")).compareTo(ourAmount) == 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private ResponseEntity<Void> toResultPage(String status, String txn) {
        String location = props.resultPageUrl()
                + "?status=" + status
                + "&txn=" + (txn == null ? "" : URLEncoder.encode(txn, StandardCharsets.UTF_8));
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(location)).build();
    }
}
