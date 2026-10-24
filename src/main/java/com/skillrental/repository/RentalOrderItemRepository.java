package com.skillrental.repository;

import com.skillrental.entity.RentalOrderItem;
import com.skillrental.entity.OrderStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RentalOrderItemRepository extends JpaRepository<RentalOrderItem, Long> {
    List<RentalOrderItem> findByOrderIdOrderByIdAsc(Long orderId);
    List<RentalOrderItem> findByServiceId(Long serviceId);
    List<RentalOrderItem> findByProviderId(Long providerId);

    @Query("select i.service.id, count(distinct i.order.id) from RentalOrderItem i " +
           "where i.service is not null and i.order.status <> :cancelledStatus group by i.service.id")
    List<Object[]> countOrdersPerService(@Param("cancelledStatus") OrderStatus cancelledStatus);
}
