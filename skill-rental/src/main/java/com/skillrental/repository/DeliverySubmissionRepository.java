package com.skillrental.repository;

import com.skillrental.entity.DeliverySubmission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeliverySubmissionRepository extends JpaRepository<DeliverySubmission,Long> {
    List<DeliverySubmission> findByOrderIdOrderByVersionDesc(Long orderId);
    Optional<DeliverySubmission> findFirstByOrderIdOrderByVersionDesc(Long orderId);
    Optional<DeliverySubmission> findByIdAndOrderId(Long id,Long orderId);
}
