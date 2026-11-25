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
import java.util.UUID;

/**
 * The eSewa payment flow:
 *
 *  1. POST /initiate  (logged-in user, via gateway + JWT)
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

    @PostMapping("/initiate")
    public ResponseEntity<EsewaInitiateResponse> initiate(@RequestBody EsewaInitiateRequest request) {
        if (request.amount() == null || request.amount() <= 0) {
            return ResponseEntity.badRequest().build();
        }

        Payment payment = new Payment();
        payment.setTransactionId(UUID.randomUUID().toString());
        payment.setAmount(request.amount());
        payment.setStatus("PENDING");
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
            return toResultPage(false, txn);
        }

        // Already decided (e.g. user refreshed the page): just show the outcome again.
        if (!"PENDING".equals(payment.getStatus())) {
            return toResultPage("SUCCESS".equals(payment.getStatus()), txn);
        }

        String expectedAmount = EsewaService.formatAmount(payment.getAmount());
        boolean verified = cb.signatureValid()
                && "COMPLETE".equals(cb.status())
                && sameAmount(cb.totalAmount(), payment.getAmount())
                && "COMPLETE".equals(esewa.checkStatus(txn, expectedAmount));

        payment.setStatus(verified ? "SUCCESS" : "FAILED");
        if (verified) {
            payment.setRefId(cb.transactionCode());
        }
        payments.save(payment);

        return toResultPage(verified, txn);
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
        return toResultPage(false, txn);
    }

    /** eSewa may send "1,000.0" — compare as numbers, ignoring thousands separators. */
    private static boolean sameAmount(String esewaAmount, Double ourAmount) {
        if (esewaAmount == null) return false;
        try {
            return new BigDecimal(esewaAmount.replace(",", ""))
                    .compareTo(BigDecimal.valueOf(ourAmount)) == 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private ResponseEntity<Void> toResultPage(boolean success, String txn) {
        String location = props.resultPageUrl()
                + "?status=" + (success ? "success" : "failed")
                + "&txn=" + (txn == null ? "" : URLEncoder.encode(txn, StandardCharsets.UTF_8));
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(location)).build();
    }
}
