package com.susir.paygate.payment.esewa;

import com.susir.paygate.payment.entity.Payment;
import com.susir.paygate.payment.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

/**
 * Background job that settles payments stuck in PENDING.
 *
 * A payment stays PENDING when the user closes the eSewa page, eSewa never
 * redirects back, or eSewa couldn't confirm in time. Every few minutes this job
 * asks eSewa's status API about PENDING payments older than a cutoff and applies
 * the same rules as the success callback:
 *   COMPLETE                        → SUCCESS (with eSewa's ref_id)
 *   ERROR / PENDING / AMBIGUOUS     → stays PENDING, tried again next run
 *   anything else (NOT_FOUND, ...)  → FAILED
 *
 * Settings (application.yml, "payments.reconcile"): enabled, interval, older-than.
 */
@Component
@ConditionalOnProperty(name = "payments.reconcile.enabled", havingValue = "true", matchIfMissing = true)
public class PendingPaymentReconciler {

    private static final Logger log = LoggerFactory.getLogger(PendingPaymentReconciler.class);

    private final PaymentRepository payments;
    private final EsewaService esewa;
    private final Duration olderThan;
    private final Clock clock;

    @Autowired
    public PendingPaymentReconciler(PaymentRepository payments, EsewaService esewa,
                                    @Value("${payments.reconcile.older-than:PT15M}") Duration olderThan) {
        this(payments, esewa, olderThan, Clock.systemUTC());
    }

    // Tests pass a fixed clock.
    PendingPaymentReconciler(PaymentRepository payments, EsewaService esewa,
                             Duration olderThan, Clock clock) {
        this.payments = payments;
        this.esewa = esewa;
        this.olderThan = olderThan;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${payments.reconcile.interval:PT5M}",
               initialDelayString = "${payments.reconcile.interval:PT5M}")
    public void reconcile() {
        for (Payment payment : payments.findStalePending(clock.instant().minus(olderThan))) {
            try {
                settle(payment);
            } catch (Exception e) {
                // One bad row must not stop the others; it will be retried next run.
                log.warn("Could not reconcile payment {}: {}", payment.getTransactionId(), e.toString());
            }
        }
    }

    private void settle(Payment payment) {
        EsewaStatus answer = esewa.lookupStatus(payment.getTransactionId(),
                EsewaService.formatAmount(payment.getAmount()));

        if ("COMPLETE".equals(answer.status())) {
            payment.setStatus("SUCCESS");
            payment.setRefId(answer.refId());
        } else if (EsewaService.isUndecided(answer.status())) {
            return;
        } else {
            payment.setStatus("FAILED");
        }
        payments.save(payment);
        log.info("Reconciled payment {} -> {} (eSewa said {})",
                payment.getTransactionId(), payment.getStatus(), answer.status());
    }
}
