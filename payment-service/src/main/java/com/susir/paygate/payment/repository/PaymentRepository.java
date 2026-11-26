package com.susir.paygate.payment.repository;


import com.susir.paygate.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByTransactionId(String transactionId);

    // A user's 20 most recent payments, newest first.
    List<Payment> findTop20ByUsernameOrderByIdDesc(String username);

    // PENDING payments started before the cutoff. Payments from before created_at
    // existed have no timestamp; they are old by definition, so they count too.
    @Query("select p from Payment p where p.status = 'PENDING' "
            + "and (p.createdAt is null or p.createdAt < :cutoff)")
    List<Payment> findStalePending(@Param("cutoff") Instant cutoff);
}
