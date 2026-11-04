package com.skillrental.repository;

import com.skillrental.entity.ServiceCatalog;
import com.skillrental.entity.ServiceStatus;
import com.skillrental.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.math.BigDecimal;
import java.util.List;

public interface OfferedServiceRepository extends JpaRepository<ServiceCatalog, Long> {
    List<ServiceCatalog> findByProviderOrderByCreatedAtDesc(User provider);
    List<ServiceCatalog> findByStatusOrderByRatingDesc(ServiceStatus status);
    List<ServiceCatalog> findByProviderAndStatusOrderByCreatedAtDesc(User provider, ServiceStatus status);
    List<ServiceCatalog> findByStatusAndPriceBetweenOrderByPriceAsc(ServiceStatus status, BigDecimal minPrice, BigDecimal maxPrice);
}
