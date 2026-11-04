package com.skillrental.repository;

import com.skillrental.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RentalOrderRepository extends JpaRepository<RentalOrder,Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select o from RentalOrder o where o.id = :id")
    Optional<RentalOrder> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
    Optional<RentalOrder> findByBooking(Booking booking);
    List<RentalOrder> findByCustomerOrderByIdDesc(User customer);
    List<RentalOrder> findByProviderOrderByIdDesc(User provider);
    @org.springframework.data.jpa.repository.Query("select (count(o) > 0) from RentalOrder o where (o.customer.id = :userId or o.provider.id = :userId) and o.status not in :closed")
    boolean hasUnfinishedOrders(@org.springframework.data.repository.query.Param("userId") Long userId,
            @org.springframework.data.repository.query.Param("closed") java.util.Collection<OrderStatus> closed);
    long countByCustomerAndStatus(User customer, OrderStatus status);
}
