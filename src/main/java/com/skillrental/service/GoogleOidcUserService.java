package com.skillrental.service;

import com.skillrental.entity.AccountStatus;
import com.skillrental.entity.CustomerProfile;
import com.skillrental.entity.OAuthIdentity;
import com.skillrental.entity.Role;
import com.skillrental.entity.User;
import com.skillrental.repository.OAuthIdentityRepository;
import com.skillrental.repository.CustomerProfileRepository;
import com.skillrental.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GoogleOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {
    private static final String GOOGLE_PROVIDER = "GOOGLE";

    private final OAuth2UserService<OidcUserRequest, OidcUser> delegate = new OidcUserService();
    private final OAuthIdentityRepository identities;
    private final UserRepository users;
    private final CustomerProfileRepository customerProfiles;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public OidcUser loadUser(OidcUserRequest request) throws OAuth2AuthenticationException {
        return provisionVerifiedGoogleUser(delegate.loadUser(request));
    }

    @Transactional
    public OidcUser provisionVerifiedGoogleUser(OidcUser googleUser) {
        String subject = googleUser.getSubject();
        String providerEmail = normalizeEmail(googleUser.getClaimAsString("email"));
        if (subject == null || subject.isBlank() || subject.length() > 255) {
            throw authenticationFailure("invalid_google_subject", "Google did not provide a valid stable account identifier.");
        }
        if (!Boolean.TRUE.equals(googleUser.getClaimAsBoolean("email_verified"))) {
            throw authenticationFailure("unverified_google_email", "Google email must be verified before login.");
        }
        if (providerEmail == null || providerEmail.length() > 160) {
            throw authenticationFailure("invalid_google_email", "Google did not provide a valid email address.");
        }

        OAuthIdentity identity = identities.findByIdentityProviderAndProviderSubject(GOOGLE_PROVIDER, subject)
                .orElse(null);
        User user;
        if (identity != null) {
            user = identity.getUser();
            if (user.getRole() != Role.CUSTOMER) {
                throw authenticationFailure("invalid_google_account_role", "This Google identity is not linked to a Customer account.");
            }
            if (user.getAccountStatus() != AccountStatus.ACTIVE
                    || !Boolean.TRUE.equals(user.getEmailVerified())) {
                throw authenticationFailure("disabled_google_account", "This account is not permitted to sign in.");
            }
            if (!providerEmail.equals(identity.getEmailAtProvider())) {
                identity.setEmailAtProvider(providerEmail);
                identities.save(identity);
            }
        } else {
            User existingUser = users.findByEmailIgnoreCase(providerEmail).orElse(null);
            if (existingUser != null) {
                if (existingUser.getRole() != Role.CUSTOMER) {
                    throw authenticationFailure("invalid_google_account_role", "Only Customer accounts can link Google login.");
                }
                if (existingUser.getAccountStatus() != AccountStatus.ACTIVE
                        || !Boolean.TRUE.equals(existingUser.getEmailVerified())) {
                    throw authenticationFailure("disabled_google_account", "The existing account is not active or verified.");
                }
                // The verified Google email alone is NOT sufficient to take over an existing account.
                // The user must also prove knowledge of the current SkillShare password.
                throw new GoogleAccountLinkRequiredException(subject, providerEmail);
            }
            String name = normalizeName(googleUser.getClaimAsString("name"), providerEmail);
            user = users.saveAndFlush(User.builder()
                    .fullName(name)
                    .email(providerEmail)
                    .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .role(Role.CUSTOMER)
                    .accountStatus(AccountStatus.ACTIVE)
                    .emailVerified(true)
                    .build());
            identity = OAuthIdentity.builder()
                    .user(user)
                    .identityProvider(GOOGLE_PROVIDER)
                    .providerSubject(subject)
                    .emailAtProvider(providerEmail)
                    .build();
            identities.saveAndFlush(identity);
            // Registration via email/password creates a CustomerProfile. Google registration must do so too.
            customerProfiles.saveAndFlush(CustomerProfile.builder().user(user).build());
        }

        Collection<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
        return new SkillShareOidcUser(authorities, googleUser.getIdToken(), googleUser.getUserInfo(), user.getEmail());
    }

    private String normalizeEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeName(String name, String email) {
        String value = name == null || name.isBlank() ? email : name.trim();
        return value.length() <= 120 ? value : value.substring(0, 120);
    }

    private OAuth2AuthenticationException authenticationFailure(String code, String message) {
        return new OAuth2AuthenticationException(new OAuth2Error(code), message);
    }

    private static final class SkillShareOidcUser implements OidcUser {
        private final Collection<? extends GrantedAuthority> authorities;
        private final OidcIdToken idToken;
        private final OidcUserInfo userInfo;
        private final String accountEmail;

        private SkillShareOidcUser(Collection<? extends GrantedAuthority> authorities, OidcIdToken idToken,
                                   OidcUserInfo userInfo, String accountEmail) {
            this.authorities = authorities;
            this.idToken = idToken;
            this.userInfo = userInfo;
            this.accountEmail = accountEmail;
        }

        @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
        @Override public String getName() { return accountEmail; }
        @Override public java.util.Map<String, Object> getAttributes() {
            return userInfo != null ? userInfo.getClaims() : idToken.getClaims();
        }
        @Override public java.util.Map<String, Object> getClaims() { return idToken.getClaims(); }
        @Override public OidcUserInfo getUserInfo() { return userInfo; }
        @Override public OidcIdToken getIdToken() { return idToken; }
    }
}
