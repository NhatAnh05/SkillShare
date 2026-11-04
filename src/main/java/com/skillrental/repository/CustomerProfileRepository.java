package com.skillrental.repository;

import com.skillrental.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CustomerProfileRepository extends JpaRepository<CustomerProfile,Long> {
    Optional<CustomerProfile> findByUser(User user);
}
