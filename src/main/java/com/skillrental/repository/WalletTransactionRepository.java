package com.skillrental.repository;

import com.skillrental.entity.UserWallet;
import com.skillrental.entity.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {
    List<WalletTransaction> findByWalletOrderByCreatedAtDesc(UserWallet wallet);
}
