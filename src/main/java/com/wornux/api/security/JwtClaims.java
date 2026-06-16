package com.wornux.api.security;

import java.time.Instant;
import java.util.List;

public record JwtClaims(String subject, List<String> authorities, Instant expiresAt) {
}
