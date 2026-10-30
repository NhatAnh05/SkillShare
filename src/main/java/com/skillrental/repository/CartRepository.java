package com.skillrental.repository;

import com.skillrental.entity.Cart;
import com.skillrental.entity.CartStatus;
import com.skillrental.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {
    Optional<Cart> findFirstByCustomerAndStatusOrderByUpdatedAtDesc(User customer, CartStatus status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cart c where c.customer = :customer and c.status = :status " +
            "order by c.updatedAt desc")
    List<Cart> findActiveForUpdate(@Param("customer") User customer, @Param("status") CartStatus status);
    List<Cart> findByCustomerOrderByUpdatedAtDesc(User customer);
}
