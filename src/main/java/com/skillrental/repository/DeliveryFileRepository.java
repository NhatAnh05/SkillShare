package com.skillrental.repository;

import com.skillrental.entity.DeliveryFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface DeliveryFileRepository extends JpaRepository<DeliveryFile,Long> {
    Optional<DeliveryFile> findByIdAndDeliveryId(Long id,Long deliveryId);
    List<DeliveryFile> findByDeliveryId(Long deliveryId);
}
