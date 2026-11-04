package com.skillrental.repository;

import com.skillrental.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment,Long> {
    Optional<Payment> findByOrder(RentalOrder order);
}
