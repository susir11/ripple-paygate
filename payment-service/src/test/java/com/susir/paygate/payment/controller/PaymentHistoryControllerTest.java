package com.susir.paygate.payment.controller;

import com.susir.paygate.payment.entity.Payment;
import com.susir.paygate.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaymentHistoryControllerTest {

    private PaymentRepository repo;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        repo = mock(PaymentRepository.class);
        mvc = MockMvcBuilders.standaloneSetup(new PaymentHistoryController(repo)).build();
    }

    @Test
    void mine_returnsOnlyTheCallersPayments() throws Exception {
        Payment p = new Payment();
        p.setTransactionId("abc-123");
        p.setAmount(new BigDecimal("100.00"));
        p.setStatus("SUCCESS");
        p.setRefId("000AWEO");
        p.setUsername("alice");
        p.setCreatedAt(Instant.parse("2026-11-26T10:00:00Z"));
        when(repo.findTop20ByUsernameOrderByIdDesc("alice")).thenReturn(List.of(p));

        mvc.perform(get("/api/payments/mine").header("X-Authenticated-User", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].transactionId").value("abc-123"))
                .andExpect(jsonPath("$[0].amount").value(100.00))
                .andExpect(jsonPath("$[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$[0].refId").value("000AWEO"))
                // internal fields are not exposed
                .andExpect(jsonPath("$[0].id").doesNotExist())
                .andExpect(jsonPath("$[0].username").doesNotExist());

        verify(repo).findTop20ByUsernameOrderByIdDesc("alice");
    }

    @Test
    void mine_withoutAuthenticatedUser_isUnauthorized() throws Exception {
        mvc.perform(get("/api/payments/mine"))
                .andExpect(status().isUnauthorized());
        verify(repo, never()).findTop20ByUsernameOrderByIdDesc(any());
    }
}
