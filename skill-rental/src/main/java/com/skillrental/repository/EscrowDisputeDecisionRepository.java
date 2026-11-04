package com.skillrental.repository;

import com.skillrental.entity.EscrowDisputeDecision;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface EscrowDisputeDecisionRepository extends JpaRepository<EscrowDisputeDecision, Long> {
    boolean existsByOrderId(Long orderId);
    Optional<EscrowDisputeDecision> findByOrderId(Long orderId);
    List<EscrowDisputeDecision> findTop20ByOrderByDecidedAtDescIdDesc();
}
