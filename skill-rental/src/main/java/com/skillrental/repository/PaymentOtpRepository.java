package com.skillrental.repository;

import com.skillrental.entity.PaymentOtp;
import com.skillrental.entity.RentalOrder;
import com.skillrental.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentOtpRepository extends JpaRepository<PaymentOtp, Long> {
    Optional<PaymentOtp> findFirstByOrderAndUserAndConsumedFalseAndExpiresAtAfterOrderByIdDesc(
            RentalOrder order, User user, LocalDateTime now);
    List<PaymentOtp> findByOrderAndConsumedFalse(RentalOrder order);
}
