package com.skillrental.repository;

import com.skillrental.entity.PaymentAttempt;
import com.skillrental.entity.PaymentAttemptStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {
    List<PaymentAttempt> findByPaymentIdOrderByAttemptNumberDesc(Long paymentId);
    Optional<PaymentAttempt> findByMerchantReference(String merchantReference);
    List<PaymentAttempt> findByStatusOrderByInitiatedAtAsc(PaymentAttemptStatus status);
}
