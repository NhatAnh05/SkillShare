package com.skillrental.repository;

import com.skillrental.entity.EscrowSettlement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface EscrowSettlementRepository extends JpaRepository<EscrowSettlement, Long> {
    boolean existsByOrderId(Long orderId);
    Optional<EscrowSettlement> findByOrderId(Long orderId);
    List<EscrowSettlement> findTop20ByOrderBySettledAtDescIdDesc();
}
