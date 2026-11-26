package com.susir.paygate.payment.esewa;

import com.susir.paygate.payment.entity.Payment;
import com.susir.paygate.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PendingPaymentReconcilerTest {

    static final Instant NOW = Instant.parse("2026-11-26T12:00:00Z");

    private PaymentRepository repo;
    private EsewaService esewa;
    private PendingPaymentReconciler reconciler;

    @BeforeEach
    void setUp() {
        repo = mock(PaymentRepository.class);
        esewa = mock(EsewaService.class);
        reconciler = new PendingPaymentReconciler(repo, esewa, Duration.ofMinutes(15),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void onlyLooksAtPaymentsOlderThanTheCutoff() {
        when(repo.findStalePending(any())).thenReturn(List.of());

        reconciler.reconcile();

        verify(repo).findStalePending(NOW.minus(Duration.ofMinutes(15)));
    }

    @Test
    void complete_marksSuccessWithRefId() {
        Payment p = stalePending();
        when(esewa.lookupStatus("abc-123", "100")).thenReturn(new EsewaStatus("COMPLETE", "R1"));

        reconciler.reconcile();

        assertEquals("SUCCESS", p.getStatus());
        assertEquals("R1", p.getRefId());
        verify(repo).save(p);
    }

    @Test
    void definiteNo_marksFailed() {
        for (String answer : new String[]{"NOT_FOUND", "CANCELED"}) {
            Payment p = stalePending();
            when(esewa.lookupStatus("abc-123", "100")).thenReturn(new EsewaStatus(answer, null));

            reconciler.reconcile();

            assertEquals("FAILED", p.getStatus(), answer);
        }
    }

    @Test
    void uncertainAnswer_staysPendingForNextRun() {
        for (String answer : new String[]{"ERROR", "PENDING", "AMBIGUOUS"}) {
            Payment p = stalePending();
            when(esewa.lookupStatus("abc-123", "100")).thenReturn(new EsewaStatus(answer, null));

            reconciler.reconcile();

            assertEquals("PENDING", p.getStatus(), answer);
        }
        verify(repo, never()).save(any());
    }

    @Test
    void oneBrokenPaymentDoesNotStopTheOthers() {
        Payment broken = stalePending();
        broken.setAmount(null);   // e.g. a bad legacy row
        Payment good = new Payment();
        good.setTransactionId("def-456");
        good.setAmount(new BigDecimal("50.00"));
        good.setStatus("PENDING");
        when(repo.findStalePending(any())).thenReturn(List.of(broken, good));
        when(esewa.lookupStatus("def-456", "50")).thenReturn(new EsewaStatus("COMPLETE", "R2"));

        reconciler.reconcile();

        assertEquals("SUCCESS", good.getStatus());
    }

    private Payment stalePending() {
        Payment p = new Payment();
        p.setTransactionId("abc-123");
        p.setAmount(new BigDecimal("100.00"));
        p.setStatus("PENDING");
        when(repo.findStalePending(any())).thenReturn(List.of(p));
        return p;
    }
}
