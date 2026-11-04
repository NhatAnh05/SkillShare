package com.skillrental.config;

import com.skillrental.service.GoogleAccountLinkRequiredException;
import com.skillrental.service.GoogleAccountLinkService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

/** Exposes actionable, fixed error codes without putting OAuth tokens or private exception data in URLs. */
@Slf4j
@Component
public class GoogleLoginFailureHandler implements AuthenticationFailureHandler {
    private final RedirectStrategy redirects = new DefaultRedirectStrategy();

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        String error = "failed";
        if (exception instanceof GoogleAccountLinkRequiredException link) {
            request.getSession(true).setAttribute(GoogleAccountLinkService.PENDING_SESSION_KEY,
                    new GoogleAccountLinkService.PendingLink(link.getGoogleSubject(), link.getVerifiedEmail(), Instant.now()));
            error = "linkRequired";
        } else {
            var session = request.getSession(false);
            if (session != null) session.removeAttribute(GoogleAccountLinkService.PENDING_SESSION_KEY);
            if (exception instanceof OAuth2AuthenticationException oauth) {
                error = switch (oauth.getError().getErrorCode()) {
                    case "unverified_google_email" -> "emailUnverified";
                    case "disabled_google_account" -> "accountDisabled";
                    case "invalid_google_account_role" -> "unsupportedAccount";
                    default -> "failed";
                };
            }
        }
        // Avoid logging exception messages: token endpoint messages can include sensitive data.
        String code = exception instanceof OAuth2AuthenticationException oauth
                ? oauth.getError().getErrorCode() : exception.getClass().getSimpleName();
        log.warn("Google OIDC sign-in rejected; reasonCode={}", code.replaceAll("[^a-zA-Z0-9_-]", ""));
        redirects.sendRedirect(request, response, "/login?oauthError=" + error);
    }
}
