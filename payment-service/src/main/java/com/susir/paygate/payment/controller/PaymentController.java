package com.susir.paygate.payment.controller;

import com.susir.paygate.payment.entity.Payment;
import com.susir.paygate.payment.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    @Autowired
    private PaymentRepository paymentRepository;

    @PostMapping("/process")
    public Payment process(@RequestBody Payment payment) {
        // Logic: Create a unique ID and set status before saving
        payment.setTransactionId(UUID.randomUUID().toString()); //
        payment.setStatus("SUCCESS");
        return paymentRepository.save(payment);
    }
}