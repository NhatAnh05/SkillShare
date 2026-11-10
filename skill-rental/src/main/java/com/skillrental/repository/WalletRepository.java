package com.skillrental.repository;

import com.skillrental.entity.User;
import com.skillrental.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, Long> {
    Optional<Wallet> findByUser(User user);
}
