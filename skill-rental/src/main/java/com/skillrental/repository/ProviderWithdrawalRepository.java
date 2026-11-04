package com.skillrental.repository;

import com.skillrental.entity.ProviderWithdrawal;
import com.skillrental.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ProviderWithdrawalRepository extends JpaRepository<ProviderWithdrawal, Long> {
    // Serialize OTP verification, cancellation, rejection and approval for the same withdrawal.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from ProviderWithdrawal w where w.id = :id")
    Optional<ProviderWithdrawal> findByIdForUpdate(@Param("id") Long id);

    List<ProviderWithdrawal> findByProviderOrderByCreatedAtDesc(User provider);
    List<ProviderWithdrawal> findByStatusOrderByCreatedAtAsc(String status);
    List<ProviderWithdrawal> findAllByOrderByCreatedAtDesc();
}
