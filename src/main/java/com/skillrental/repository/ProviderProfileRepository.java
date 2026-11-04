package com.skillrental.repository;

import com.skillrental.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ProviderProfileRepository extends JpaRepository<ProviderProfile,Long> {
    Optional<ProviderProfile> findByUser(User user);
}
