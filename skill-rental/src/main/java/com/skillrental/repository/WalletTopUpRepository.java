package com.skillrental.repository;

import com.skillrental.entity.WalletTopUp;
import com.skillrental.entity.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface WalletTopUpRepository extends JpaRepository<WalletTopUp, Long> {
    Optional<WalletTopUp> findByTxnRef(String txnRef);
    List<WalletTopUp> findTop10ByUserOrderByCreatedAtDesc(User user);
    boolean existsByGatewayTransactionNo(String gatewayTransactionNo);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from WalletTopUp t where t.txnRef = :txnRef")
    Optional<WalletTopUp> findByTxnRefForUpdate(@Param("txnRef") String txnRef);
}
