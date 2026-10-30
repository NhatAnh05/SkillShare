package com.skillrental.repository;

import com.skillrental.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User,Long> {
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findByResetToken(String token);
    Optional<User> findByEmailVerificationToken(String token);
    boolean existsByEmailIgnoreCase(String email);

    // Serialize OTP attempts/resends for one account, including concurrent HTTP requests.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where lower(u.email) = lower(:email)")
    Optional<User> findByEmailForOtpUpdate(@Param("email") String email);
}
