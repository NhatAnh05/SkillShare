package com.skillrental.repository;

import com.skillrental.entity.OAuthIdentity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface OAuthIdentityRepository extends JpaRepository<OAuthIdentity, Long> {
    Optional<OAuthIdentity> findByIdentityProviderAndProviderSubject(String identityProvider, String providerSubject);
    Optional<OAuthIdentity> findByUserIdAndIdentityProvider(Long userId, String identityProvider);
}
