package com.skillrental.repository;
import com.skillrental.entity.OrderWorkUpdate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface OrderWorkUpdateRepository extends JpaRepository<OrderWorkUpdate,Long> {
 List<OrderWorkUpdate> findByOrderIdOrderByIdDesc(Long orderId);
}
