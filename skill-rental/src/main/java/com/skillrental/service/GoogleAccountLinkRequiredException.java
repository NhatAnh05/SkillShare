package com.skillrental.service;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

/** A verified Google identity matched a registered Customer email; password proof is required. */
public final class GoogleAccountLinkRequiredException extends OAuth2AuthenticationException {
    private final String googleSubject;
    private final String verifiedEmail;

    public GoogleAccountLinkRequiredException(String googleSubject, String verifiedEmail) {
        super(new OAuth2Error("google_account_link_required"), "Existing account requires password-based linking.");
        this.googleSubject = googleSubject;
        this.verifiedEmail = verifiedEmail;
    }

    public String getGoogleSubject() { return googleSubject; }
    public String getVerifiedEmail() { return verifiedEmail; }
}
