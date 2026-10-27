package com.skillrental.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

import java.util.Collections;
import java.util.Iterator;

@Configuration
public class GoogleOAuthConfiguration {
    @Bean
    GoogleLoginSettings googleLoginSettings(
            @Value("${GOOGLE_CLIENT_ID:}") String clientId,
            @Value("${GOOGLE_CLIENT_SECRET:}") String clientSecret) {
        boolean hasClientId = clientId != null && !clientId.isBlank();
        boolean hasClientSecret = clientSecret != null && !clientSecret.isBlank();
        if (hasClientId != hasClientSecret) {
            throw new IllegalStateException("Set both GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET to enable Google login.");
        }
        if (!hasClientId) return new GoogleLoginSettings(null, false);

        ClientRegistration registration = ClientRegistration.withRegistrationId("google")
                .clientId(clientId.trim())
                .clientSecret(clientSecret.trim())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .issuerUri("https://accounts.google.com")
                .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
                .userNameAttributeName("sub")
                .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .clientName("Google")
                .scope("openid", "profile", "email")
                .build();
        return new GoogleLoginSettings(registration, true);
    }

    @Bean
    ClientRegistrationRepository clientRegistrationRepository(GoogleLoginSettings settings) {
        if (settings.enabled()) return new InMemoryClientRegistrationRepository(settings.registration());
        return new EmptyClientRegistrationRepository();
    }

    public record GoogleLoginSettings(ClientRegistration registration, boolean enabled) {}

    private static final class EmptyClientRegistrationRepository implements ClientRegistrationRepository,
            Iterable<ClientRegistration> {
        @Override
        public ClientRegistration findByRegistrationId(String registrationId) {
            return null;
        }

        @Override
        public Iterator<ClientRegistration> iterator() {
            return Collections.emptyIterator();
        }
    }
}
