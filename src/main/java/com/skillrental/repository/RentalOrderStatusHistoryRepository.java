package com.skillrental.repository;

import com.skillrental.entity.RentalOrderStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RentalOrderStatusHistoryRepository extends JpaRepository<RentalOrderStatusHistory, Long> {
    List<RentalOrderStatusHistory> findByOrderIdOrderByCreatedAtAsc(Long orderId);
}
