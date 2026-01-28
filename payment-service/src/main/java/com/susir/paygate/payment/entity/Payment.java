package com.susir.paygate.payment.entity;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Entity
@Table(name = "payments")   // Custom table name in Postgres
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class Payment {

    @Id  // <--- THIS IS THE MISSING PIECE
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String transactionId;
    private Double amount;
    private String status;  // e.g., "SUCCESS" or "PENDING"
}
