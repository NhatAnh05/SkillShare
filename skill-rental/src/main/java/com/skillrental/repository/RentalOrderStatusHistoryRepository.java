package com.skillrental.repository;

import com.skillrental.entity.RentalOrderStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RentalOrderStatusHistoryRepository extends JpaRepository<RentalOrderStatusHistory, Long> {
    List<RentalOrderStatusHistory> findByOrderIdOrderByCreatedAtAsc(Long orderId);
    Optional<RentalOrderStatusHistory> findFirstByOrderIdOrderByCreatedAtDesc(Long orderId);
}
