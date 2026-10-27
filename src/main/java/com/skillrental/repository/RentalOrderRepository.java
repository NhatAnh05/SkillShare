package com.skillrental.repository;

import com.skillrental.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RentalOrderRepository extends JpaRepository<RentalOrder,Long> {
    Optional<RentalOrder> findByBooking(Booking booking);
    List<RentalOrder> findByCustomerOrderByIdDesc(User customer);
    List<RentalOrder> findByProviderOrderByIdDesc(User provider);
    long countByCustomerAndStatus(User customer, OrderStatus status);
}
