package com.skillrental.repository;

import com.skillrental.entity.DeliveryRevisionRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeliveryRevisionRequestRepository extends JpaRepository<DeliveryRevisionRequest,Long> {
    List<DeliveryRevisionRequest> findByOrderIdOrderByCreatedAtAsc(Long orderId);
}
