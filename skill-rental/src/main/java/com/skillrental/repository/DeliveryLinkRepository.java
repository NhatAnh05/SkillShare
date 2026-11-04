package com.skillrental.repository;

import com.skillrental.entity.DeliveryLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeliveryLinkRepository extends JpaRepository<DeliveryLink,Long> {
    List<DeliveryLink> findByDeliveryId(Long deliveryId);
}
