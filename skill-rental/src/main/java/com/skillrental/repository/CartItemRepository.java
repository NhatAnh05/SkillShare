package com.skillrental.repository;

import com.skillrental.entity.Cart;
import com.skillrental.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    Optional<CartItem> findByCartAndServiceId(Cart cart, Long serviceId);
    void deleteByCartAndServiceId(Cart cart, Long serviceId);
}
