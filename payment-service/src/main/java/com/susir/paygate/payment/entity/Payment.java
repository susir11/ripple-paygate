package com.susir.paygate.payment.entity;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;


// The table itself is created and changed by the Flyway scripts in
// src/main/resources/db/migration — Hibernate only checks that this class matches.
@Entity
@Table(name = "payments")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String transactionId;

    // Money is BigDecimal, never double: 0.1 + 0.2 is exactly 0.3 here.
    // Stored as NUMERIC(12,2) — up to 9,999,999,999.99 with paisa precision.
    @Column(precision = 12, scale = 2)
    private BigDecimal amount;

    private String status;  // "PENDING", "SUCCESS" or "FAILED"
    private String refId;   // eSewa's transaction code, set once a payment is verified

    // Who made the payment — the gateway passes the logged-in username along.
    // Null for payments created before payments were linked to users.
    private String username;

    // When the payment was started. Used to find old PENDING payments to re-check.
    private Instant createdAt;
}
