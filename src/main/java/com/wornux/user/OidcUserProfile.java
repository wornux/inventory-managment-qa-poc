package com.wornux.user;

import java.util.Locale;

public record OidcUserProfile(String issuer, String subject, String username, String email) {

    public OidcUserProfile normalized() {
        return new OidcUserProfile(
                require("issuer", issuer),
                require("subject", subject),
                require("preferred_username", username),
                require("email", email).toLowerCase(Locale.ROOT));
    }

    private static String require(String claim, String value) {
        String normalized = value == null ? "" : value.trim();

        if (normalized.isBlank()) {
            throw new OidcProvisioningException("Missing required OIDC claim: " + claim);
        }

        return normalized;
    }
}
