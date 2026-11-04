package com.skillrental.repository;

import com.skillrental.entity.Refund;
import com.skillrental.entity.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RefundRepository extends JpaRepository<Refund, Long> {
    List<Refund> findByPaymentIdOrderByRequestedAtDesc(Long paymentId);
    List<Refund> findByStatusOrderByRequestedAtAsc(RefundStatus status);
}
