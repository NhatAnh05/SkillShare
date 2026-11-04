package com.skillrental.service;

import com.skillrental.entity.AccountStatus;
import com.skillrental.entity.OAuthIdentity;
import com.skillrental.entity.Role;
import com.skillrental.entity.User;
import com.skillrental.repository.OAuthIdentityRepository;
import com.skillrental.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.time.Instant;

/** Links Google only after a verified OIDC response AND a successful local password login. */
@Service
@RequiredArgsConstructor
public class GoogleAccountLinkService {
    public static final String PENDING_SESSION_KEY = "skillshare.google.pending_link";
    private static final long MAX_AGE_SECONDS = 300;
    private final OAuthIdentityRepository identities;
    private final UserRepository users;

    public record PendingLink(String subject, String email, Instant createdAt) implements Serializable {}

    /** Returns true if a Google identity was newly linked or already linked to this account. */
    @Transactional
    public boolean finishPasswordVerifiedLink(HttpSession session, String loggedInEmail) {
        if (session == null) return false;
        Object value = session.getAttribute(PENDING_SESSION_KEY);
        session.removeAttribute(PENDING_SESSION_KEY); // one use only, including rejected attempts
        if (!(value instanceof PendingLink pending) || pending.createdAt() == null
                || pending.createdAt().isAfter(Instant.now())
                || pending.createdAt().isBefore(Instant.now().minusSeconds(MAX_AGE_SECONDS))
                || pending.subject() == null || pending.subject().isBlank()
                || pending.email() == null || !pending.email().equalsIgnoreCase(loggedInEmail)) return false;

        User user = users.findByEmailIgnoreCase(loggedInEmail).orElse(null);
        if (user == null || user.getRole() != Role.CUSTOMER || user.getAccountStatus() != AccountStatus.ACTIVE
                || !Boolean.TRUE.equals(user.getEmailVerified())) return false;

        OAuthIdentity bySubject = identities.findByIdentityProviderAndProviderSubject("GOOGLE", pending.subject()).orElse(null);
        if (bySubject != null) return bySubject.getUser().getId().equals(user.getId());
        if (identities.findByUserIdAndIdentityProvider(user.getId(), "GOOGLE").isPresent()) return false;

        identities.saveAndFlush(OAuthIdentity.builder().user(user).identityProvider("GOOGLE")
                .providerSubject(pending.subject()).emailAtProvider(pending.email()).build());
        return true;
    }
}
