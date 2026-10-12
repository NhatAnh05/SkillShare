package com.skillrental.repository;

import com.skillrental.entity.RentalOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RentalOrderItemRepository extends JpaRepository<RentalOrderItem, Long> {
    List<RentalOrderItem> findByOrderIdOrderByIdAsc(Long orderId);
    List<RentalOrderItem> findByServiceId(Long serviceId);
    List<RentalOrderItem> findByProviderId(Long providerId);
}
