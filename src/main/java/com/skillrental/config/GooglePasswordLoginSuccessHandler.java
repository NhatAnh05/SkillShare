package com.skillrental.config;

import com.skillrental.service.GoogleAccountLinkService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** A local password login is the second proof required to link a pre-existing Customer account. */
@Slf4j
@Component
@RequiredArgsConstructor
public class GooglePasswordLoginSuccessHandler implements AuthenticationSuccessHandler {
    private final GoogleAccountLinkService links;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        boolean linked = false;
        try {
            linked = links.finishPasswordVerifiedLink(request.getSession(false), authentication.getName());
        } catch (RuntimeException ex) {
            // A link failure should never break an otherwise valid password login.
            log.warn("Google account linking was not completed; reasonType={}", ex.getClass().getSimpleName());
        }
        new DefaultRedirectStrategy().sendRedirect(request, response, linked ? "/marketplace?googleLinked=1" : "/workspace");
    }
}
