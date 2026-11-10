package com.skillrental.repository;

import com.skillrental.entity.Wallet;
import com.skillrental.entity.WalletTransaction;
import com.skillrental.entity.WalletTransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {
    List<WalletTransaction> findByWalletOrderByIdDesc(Wallet wallet);
    List<WalletTransaction> findByWalletAndTypeOrderByIdDesc(Wallet wallet, WalletTransactionType type);
}
