package com.skillrental;

import com.skillrental.entity.AccountStatus;
import com.skillrental.entity.OAuthIdentity;
import com.skillrental.entity.Role;
import com.skillrental.entity.User;
import com.skillrental.repository.OAuthIdentityRepository;
import com.skillrental.repository.CustomerProfileRepository;
import com.skillrental.repository.UserRepository;
import com.skillrental.service.CurrentUserService;
import com.skillrental.service.GoogleOidcUserService;
import com.skillrental.service.GoogleAccountLinkRequiredException;
import com.skillrental.service.GoogleAccountLinkService;
import com.skillrental.config.GoogleLoginFailureHandler;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest(properties = {
        "GOOGLE_CLIENT_ID=test-client-id",
        "GOOGLE_CLIENT_SECRET=test-client-secret"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class GoogleOidcLoginTest {
    @Autowired private MockMvc mvc;
    @Autowired private GoogleOidcUserService googleUsers;
    @Autowired private OAuthIdentityRepository identities;
    @Autowired private UserRepository users;
    @Autowired private CustomerProfileRepository customerProfiles;
    @Autowired private GoogleLoginFailureHandler googleFailures;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private GoogleAccountLinkService googleLinks;
    @Autowired private CurrentUserService currentUser;

    @Test
    void verifiedGoogleSubjectCreatesCustomerAndPrincipalUsesDatabaseEmail() {
        var principal = googleUsers.provisionVerifiedGoogleUser(googleUser(
                "stable-google-sub-1", "New.Customer@Example.test", true, "New Customer"));

        User persisted = users.findByEmailIgnoreCase("new.customer@example.test").orElseThrow();
        OAuthIdentity identity = identities.findByIdentityProviderAndProviderSubject("GOOGLE", "stable-google-sub-1")
                .orElseThrow();
        assertEquals(persisted.getId(), identity.getUser().getId());
        assertEquals("new.customer@example.test", principal.getName());
        assertEquals(Role.CUSTOMER, persisted.getRole());
        assertEquals(AccountStatus.ACTIVE, persisted.getAccountStatus());
        assertTrue(persisted.getEmailVerified());
        assertTrue(customerProfiles.findByUser(persisted).isPresent(), "Google registration should create CustomerProfile");

        var authentication = new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");
        assertEquals(persisted.getId(), currentUser.require(authentication).getId());
    }

    @Test
    void sameGoogleSubjectLogsIntoExistingAccountWithoutCreatingDuplicates() {
        var first = googleUsers.provisionVerifiedGoogleUser(googleUser(
                "stable-google-sub-2", "repeat@example.test", true, "Repeat Customer"));
        long userCount = users.count();
        long identityCount = identities.count();

        var second = googleUsers.provisionVerifiedGoogleUser(googleUser(
                "stable-google-sub-2", "repeat@example.test", true, "Repeat Customer"));

        assertEquals(first.getName(), second.getName());
        assertEquals(userCount, users.count());
        assertEquals(identityCount, identities.count());
    }

    @Test
    void matchingEmailWithoutPreviouslyLinkedSubjectIsNotAutomaticallyLinked() {
        long identityCount = identities.count();
        assertThrows(OAuth2AuthenticationException.class, () -> googleUsers.provisionVerifiedGoogleUser(
                googleUser("new-sub-for-existing-email", "customer@skillrental.vn", true, "Customer")));
        assertTrue(identities.findByIdentityProviderAndProviderSubject("GOOGLE", "new-sub-for-existing-email").isEmpty());
        assertEquals(identityCount, identities.count());
    }

    @Test
    void existingCustomerCanSafelyLinkGoogleAfterPasswordAuthentication() throws Exception {
        User existing = users.saveAndFlush(User.builder()
                .fullName("Existing Customer")
                .email("local.customer@example.test")
                .password(passwordEncoder.encode("valid-test-password"))
                .role(Role.CUSTOMER)
                .accountStatus(AccountStatus.ACTIVE)
                .emailVerified(true)
                .build());
        long userCount = users.count();
        var google = googleUser("linked-google-sub", "local.customer@example.test", true, "Existing Customer");
        var required = assertThrows(GoogleAccountLinkRequiredException.class,
                () -> googleUsers.provisionVerifiedGoogleUser(google));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContextPath("");
        MockHttpServletResponse response = new MockHttpServletResponse();
        googleFailures.onAuthenticationFailure(request, response, required);
        assertEquals("/login?oauthError=linkRequired", response.getRedirectedUrl());

        // Verify the actual Spring Security password filter and success handler, not a simulated success.
        mvc.perform(post("/login").session((MockHttpSession) request.getSession()).with(csrf())
                        .param("username", existing.getEmail())
                        .param("password", "valid-test-password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/marketplace?googleLinked=1"));
        assertEquals(existing.getId(), identities.findByIdentityProviderAndProviderSubject("GOOGLE", "linked-google-sub")
                .orElseThrow().getUser().getId());
        assertEquals(userCount, users.count());
        assertEquals(existing.getEmail(), googleUsers.provisionVerifiedGoogleUser(google).getName());
    }

    @Test
    void pendingGoogleLinkCannotBeClaimedByDifferentSignedInAccount() throws Exception {
        var required = new GoogleAccountLinkRequiredException("pending-sub", "victim@example.test");
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse failed = new MockHttpServletResponse();
        googleFailures.onAuthenticationFailure(request, failed, required);
        assertEquals("/login?oauthError=linkRequired", failed.getRedirectedUrl());
        boolean linked = googleLinks.finishPasswordVerifiedLink(request.getSession(), "attacker@example.test");
        assertTrue(!linked);
        assertTrue(identities.findByIdentityProviderAndProviderSubject("GOOGLE", "pending-sub").isEmpty());
        assertEquals(null, request.getSession().getAttribute(GoogleAccountLinkService.PENDING_SESSION_KEY));
    }

    @Test
    void expiredGoogleLinkDoesNotCreateIdentity() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(GoogleAccountLinkService.PENDING_SESSION_KEY,
                new GoogleAccountLinkService.PendingLink("old-sub", "customer@skillrental.vn", Instant.now().minusSeconds(500)));
        assertTrue(!googleLinks.finishPasswordVerifiedLink(session, "customer@skillrental.vn"));
        assertTrue(identities.findByIdentityProviderAndProviderSubject("GOOGLE", "old-sub").isEmpty());
    }

    @Test
    void oidcUserWithoutUserInfoStillProvidesAttributes() {
        var google = googleUser("idtoken-only-google-sub", "idtoken.only@example.test", true, "ID Token Only");
        var idOnly = new DefaultOidcUser(google.getAuthorities(), google.getIdToken(), "email");
        var principal = googleUsers.provisionVerifiedGoogleUser(idOnly);
        assertEquals("idtoken.only@example.test", principal.getName());
        assertEquals("idtoken-only-google-sub", principal.getAttributes().get("sub"));
    }

    @Test
    void unverifiedGoogleEmailIsRejectedWithoutCreatingAccount() {
        long userCount = users.count();
        assertThrows(OAuth2AuthenticationException.class, () -> googleUsers.provisionVerifiedGoogleUser(
                googleUser("unverified-sub", "unverified@example.test", false, "Unverified")));
        assertTrue(users.findByEmailIgnoreCase("unverified@example.test").isEmpty());
        assertEquals(userCount, users.count());
    }

    @Test
    void linkedLockedAccountCannotSignIn() {
        var created = googleUsers.provisionVerifiedGoogleUser(googleUser(
                "locked-google-sub", "locked.google@example.test", true, "Locked Google"));
        User linked = users.findByEmailIgnoreCase(created.getName()).orElseThrow();
        linked.setAccountStatus(AccountStatus.LOCKED);
        users.saveAndFlush(linked);

        assertThrows(OAuth2AuthenticationException.class, () -> googleUsers.provisionVerifiedGoogleUser(
                googleUser("locked-google-sub", "locked.google@example.test", true, "Locked Google")));
    }

    @Test
    void loginPageKeepsPasswordLoginAndOnlyOffersGoogleWhenConfigured() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/login"))
                .andExpect(model().attribute("googleLoginEnabled", true));
    }

    @Test
    void googleAuthorizationStartsOidcRedirectWithLocalCallback() throws Exception {
        MvcResult result = mvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String location = result.getResponse().getRedirectedUrl();
        assertTrue(location.startsWith("https://accounts.google.com/o/oauth2/v2/auth?"));
        assertTrue(location.contains("client_id=test-client-id"));
        assertTrue(location.contains("redirect_uri="));
        assertTrue(location.contains("login%2Foauth2%2Fcode%2Fgoogle")
                || location.contains("login/oauth2/code/google"));
    }

    private DefaultOidcUser googleUser(String subject, String email, boolean verified, String name) {
        Instant now = Instant.now();
        Map<String, Object> claims = Map.of(
                "sub", subject,
                "email", email,
                "email_verified", verified,
                "name", name,
                "iss", "https://accounts.google.com",
                "aud", List.of("test-client"),
                "iat", now.getEpochSecond(),
                "exp", now.plusSeconds(300).getEpochSecond());
        OidcIdToken token = new OidcIdToken(UUID.randomUUID().toString(), now, now.plusSeconds(300), claims);
        OidcUserInfo userInfo = new OidcUserInfo(claims);
        return new DefaultOidcUser(List.of(new SimpleGrantedAuthority("SCOPE_openid")), token, userInfo, "email");
    }
}
