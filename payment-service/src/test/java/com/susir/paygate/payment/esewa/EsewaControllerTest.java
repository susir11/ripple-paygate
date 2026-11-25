package com.susir.paygate.payment.esewa;

import com.susir.paygate.payment.entity.Payment;
import com.susir.paygate.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class EsewaControllerTest {

    static final String RESULT = "http://localhost:3000/result.html";

    private EsewaService esewa;
    private PaymentRepository repo;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        esewa = mock(EsewaService.class);
        repo = mock(PaymentRepository.class);
        when(repo.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        mvc = MockMvcBuilders
                .standaloneSetup(new EsewaController(esewa, repo, EsewaServiceTest.SANDBOX))
                .build();
    }

    // ── initiate ─────────────────────────────────────────────────────────────

    @Test
    void initiate_savesPendingAndReturnsFields() throws Exception {
        when(esewa.buildFormFields(anyString(), eq(100.0)))
                .thenReturn(Map.of("total_amount", "100.0", "signature", "SIG"));

        mvc.perform(post("/api/payments/esewa/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100.0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formUrl").value("https://rc-epay.esewa.com.np/api/epay/main/v2/form"))
                .andExpect(jsonPath("$.fields.signature").value("SIG"));

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(repo).save(saved.capture());
        assertEquals("PENDING", saved.getValue().getStatus());
        assertEquals(100.0, saved.getValue().getAmount());
        assertNotNull(saved.getValue().getTransactionId());
        verify(esewa).buildFormFields(saved.getValue().getTransactionId(), 100.0);
    }

    @Test
    void initiate_rejectsNonPositiveAmount() throws Exception {
        for (String body : new String[]{"{\"amount\":0}", "{\"amount\":-5}", "{}"}) {
            mvc.perform(post("/api/payments/esewa/initiate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }
        verify(repo, never()).save(any());
    }

    // ── success ──────────────────────────────────────────────────────────────

    @Test
    void success_validAndComplete_marksSuccess() throws Exception {
        Payment p = pending(100.0);
        callback(true, "COMPLETE", "100.0");
        when(esewa.checkStatus("abc-123", "100.0")).thenReturn("COMPLETE");

        expectRedirect("/api/payments/esewa/success?data=D", RESULT + "?status=success&txn=abc-123");

        assertEquals("SUCCESS", p.getStatus());
        assertEquals("000AWEO", p.getRefId());
        verify(repo).save(p);
    }

    @Test
    void success_amountWithComma_matches() throws Exception {
        Payment p = pending(1000.0);
        callback(true, "COMPLETE", "1,000.0");
        when(esewa.checkStatus("abc-123", "1000.0")).thenReturn("COMPLETE");

        expectRedirect("/api/payments/esewa/success?data=D", RESULT + "?status=success&txn=abc-123");

        assertEquals("SUCCESS", p.getStatus());
    }

    @Test
    void success_badSignature_marksFailed() throws Exception {
        Payment p = pending(100.0);
        callback(false, "COMPLETE", "100.0");

        expectRedirect("/api/payments/esewa/success?data=D", RESULT + "?status=failed&txn=abc-123");

        assertEquals("FAILED", p.getStatus());
        verify(esewa, never()).checkStatus(any(), any());
    }

    @Test
    void success_statusCheckNotComplete_marksFailed() throws Exception {
        Payment p = pending(100.0);
        callback(true, "COMPLETE", "100.0");
        when(esewa.checkStatus("abc-123", "100.0")).thenReturn("ERROR");

        expectRedirect("/api/payments/esewa/success?data=D", RESULT + "?status=failed&txn=abc-123");

        assertEquals("FAILED", p.getStatus());
    }

    @Test
    void success_amountMismatch_marksFailed() throws Exception {
        Payment p = pending(100.0);
        callback(true, "COMPLETE", "1.0");
        when(esewa.checkStatus(any(), any())).thenReturn("COMPLETE");

        expectRedirect("/api/payments/esewa/success?data=D", RESULT + "?status=failed&txn=abc-123");

        assertEquals("FAILED", p.getStatus());
    }

    @Test
    void success_alreadySuccess_doesNotChange() throws Exception {
        Payment p = pending(100.0);
        p.setStatus("SUCCESS");
        callback(true, "COMPLETE", "100.0");

        expectRedirect("/api/payments/esewa/success?data=D", RESULT + "?status=success&txn=abc-123");

        assertEquals("SUCCESS", p.getStatus());
        verify(repo, never()).save(any());
        verify(esewa, never()).checkStatus(any(), any());
    }

    @Test
    void success_unknownTransaction_redirectsFailed() throws Exception {
        callback(true, "COMPLETE", "100.0");
        when(repo.findByTransactionId("abc-123")).thenReturn(Optional.empty());

        expectRedirect("/api/payments/esewa/success?data=D", RESULT + "?status=failed&txn=abc-123");

        verify(repo, never()).save(any());
    }

    @Test
    void success_garbageData_redirectsFailed() throws Exception {
        when(esewa.parseCallback("junk")).thenReturn(EsewaCallback.invalid());

        expectRedirect("/api/payments/esewa/success?data=junk", RESULT + "?status=failed&txn=");

        verify(repo, never()).save(any());
    }

    // ── failure ──────────────────────────────────────────────────────────────

    @Test
    void failure_marksPendingFailed() throws Exception {
        Payment p = pending(100.0);

        expectRedirect("/api/payments/esewa/failure?txn=abc-123", RESULT + "?status=failed&txn=abc-123");

        assertEquals("FAILED", p.getStatus());
        verify(repo).save(p);
    }

    @Test
    void failure_doesNotOverwriteSuccess() throws Exception {
        Payment p = pending(100.0);
        p.setStatus("SUCCESS");

        expectRedirect("/api/payments/esewa/failure?txn=abc-123", RESULT + "?status=failed&txn=abc-123");

        assertEquals("SUCCESS", p.getStatus());
        verify(repo, never()).save(any());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Payment pending(double amount) {
        Payment p = new Payment();
        p.setTransactionId("abc-123");
        p.setAmount(amount);
        p.setStatus("PENDING");
        when(repo.findByTransactionId("abc-123")).thenReturn(Optional.of(p));
        return p;
    }

    private void callback(boolean valid, String status, String totalAmount) {
        when(esewa.parseCallback("D"))
                .thenReturn(new EsewaCallback(valid, status, "abc-123", totalAmount, "000AWEO"));
    }

    private void expectRedirect(String url, String location) throws Exception {
        mvc.perform(get(url))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", location));
    }
}
