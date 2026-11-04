package com.skillrental.repository;

import com.skillrental.entity.TermsAcceptance;
import com.skillrental.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TermsAcceptanceRepository extends JpaRepository<TermsAcceptance, Long> {
    List<TermsAcceptance> findByUserOrderByAcceptedAtDesc(User user);
    Optional<TermsAcceptance> findFirstByUserAndPolicyTypeOrderByAcceptedAtDesc(User user, String policyType);
}
