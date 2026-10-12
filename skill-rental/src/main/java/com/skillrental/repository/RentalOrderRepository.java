package com.skillrental.repository;

import com.skillrental.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RentalOrderRepository extends JpaRepository<RentalOrder,Long> {
    List<RentalOrder> findByCustomerOrderByIdDesc(User customer);
    long countByCustomerAndStatus(User customer, OrderStatus status);
}
