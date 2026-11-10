package com.skillrental.repository;

import com.skillrental.entity.Escrow;
import com.skillrental.entity.EscrowStatus;
import com.skillrental.entity.RentalOrder;
import com.skillrental.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface EscrowRepository extends JpaRepository<Escrow, Long> {
    Optional<Escrow> findByOrder(RentalOrder order);
    Optional<Escrow> findByOrderId(Long orderId);
    List<Escrow> findByProviderOrderByIdDesc(User provider);
    List<Escrow> findByStatusIn(List<EscrowStatus> statuses);
}
