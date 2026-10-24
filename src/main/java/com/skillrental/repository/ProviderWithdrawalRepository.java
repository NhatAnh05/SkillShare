package com.skillrental.repository;

import com.skillrental.entity.ProviderWithdrawal;
import com.skillrental.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProviderWithdrawalRepository extends JpaRepository<ProviderWithdrawal, Long> {
    List<ProviderWithdrawal> findByProviderOrderByCreatedAtDesc(User provider);
    List<ProviderWithdrawal> findByStatusOrderByCreatedAtAsc(String status);
    List<ProviderWithdrawal> findAllByOrderByCreatedAtDesc();
}
