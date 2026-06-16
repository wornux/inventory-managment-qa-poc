package com.wornux.audit;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUserUtils {

    public static final String ANONYMOUS = "ANONYMOUS";

    private CurrentUserUtils() {
    }

    public static String currentUsername() {
        SecurityContext context = SecurityContextHolder.getContext();
        if (context == null) {
            return ANONYMOUS;
        }
        Authentication authentication = context.getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return ANONYMOUS;
        }
        String username = authentication.getName();
        if (username == null || username.isBlank()) {
            return ANONYMOUS;
        }
        return username;
    }
}
