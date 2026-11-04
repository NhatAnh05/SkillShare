package com.skillrental.repository;

import com.skillrental.entity.Cart;
import com.skillrental.entity.CartItem;
import com.skillrental.entity.CartStatus;
import com.skillrental.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    Optional<CartItem> findByCartAndServiceId(Cart cart, Long serviceId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from CartItem item where item.cart = :cart and item.service.id = :serviceId")
    Optional<CartItem> findByCartAndServiceIdForUpdate(@Param("cart") Cart cart,
                                                       @Param("serviceId") Long serviceId);
    Optional<CartItem> findByIdAndCart(Long id, Cart cart);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from CartItem item join fetch item.cart where item.id = :id")
    Optional<CartItem> findByIdForUpdate(@Param("id") Long id);
    void deleteByCartAndServiceId(Cart cart, Long serviceId);

    @Query("select coalesce(sum(item.quantity), 0) from CartItem item " +
            "where item.cart.customer = :customer and item.cart.status = :status")
    Long sumQuantityByCustomerAndCartStatus(@Param("customer") User customer,
                                            @Param("status") CartStatus status);
}
