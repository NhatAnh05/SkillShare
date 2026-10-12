package com.skillrental.repository;

import com.skillrental.entity.Cart;
import com.skillrental.entity.CartStatus;
import com.skillrental.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {
    Optional<Cart> findFirstByCustomerAndStatusOrderByUpdatedAtDesc(User customer, CartStatus status);
    List<Cart> findByCustomerOrderByUpdatedAtDesc(User customer);
}
