package com.skillrental.repository;

import com.skillrental.entity.User;
import com.skillrental.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;

public interface UserRepository extends JpaRepository<User,Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForRoleUpdate(@Param("id") Long id);
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findByResetToken(String token);
    Optional<User> findByEmailVerificationToken(String token);
    boolean existsByEmailIgnoreCase(String email);
    List<User> findByRole(Role role);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForNotificationUpdate(@Param("id") Long id);

    @Query("select u from User u where u.role in :roles and u.accountStatus = com.skillrental.entity.AccountStatus.ACTIVE and u.emailVerified = true order by u.id")
    List<User> findNotificationRecipients(@Param("roles") java.util.Collection<Role> roles);

    @Query("select u from User u where u.role in (com.skillrental.entity.Role.CUSTOMER, com.skillrental.entity.Role.PROVIDER) and u.accountStatus = com.skillrental.entity.AccountStatus.ACTIVE and u.emailVerified = true and (lower(u.fullName) like :term escape '!' or lower(u.email) like :term escape '!' or u.id = :id) order by u.id")
    List<User> searchNotificationRecipients(@Param("term") String term, @Param("id") Long id, org.springframework.data.domain.Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForChatUpdate(@Param("id") Long id);
    // Serialize OTP attempts/resends for one account, including concurrent HTTP requests.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where lower(u.email) = lower(:email)")
    Optional<User> findByEmailForOtpUpdate(@Param("email") String email);
}
