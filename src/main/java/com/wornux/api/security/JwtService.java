package com.wornux.api.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
public class JwtService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();

    private final JsonMapper jsonMapper;
    private final byte[] secret;
    private final Duration ttl;

    public JwtService(
            JsonMapper jsonMapper,
            @Value("${app.security.jwt.secret:dev-only-change-this-secret-for-api-jwt}") String secret,
            @Value("${app.security.jwt.ttl-minutes:60}") long ttlMinutes) {
        this.jsonMapper = jsonMapper;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    public String generate(UserDetails userDetails) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);
        Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", userDetails.getUsername());
        payload.put("iat", now.getEpochSecond());
        payload.put("exp", expiresAt.getEpochSecond());
        payload.put("authorities", userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList());

        String headerPart = encodeJson(header);
        String payloadPart = encodeJson(payload);
        return headerPart + "." + payloadPart + "." + sign(headerPart + "." + payloadPart);
    }

    public JwtClaims parse(String token) {
        String[] parts = token == null ? new String[0] : token.split("\\.");
        if (parts.length != 3) {
            throw new JwtException("Invalid JWT format.");
        }
        String signedContent = parts[0] + "." + parts[1];
        String expectedSignature = sign(signedContent);
        if (!MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.UTF_8),
                parts[2].getBytes(StandardCharsets.UTF_8))) {
            throw new JwtException("Invalid JWT signature.");
        }

        Map<String, Object> payload = decodeJson(parts[1]);
        String subject = stringClaim(payload, "sub");
        Instant expiresAt = Instant.ofEpochSecond(numberClaim(payload, "exp"));
        if (Instant.now().isAfter(expiresAt)) {
            throw new JwtException("JWT has expired.");
        }
        List<String> authorities = listClaim(payload, "authorities");
        return new JwtClaims(subject, authorities, expiresAt);
    }

    private String encodeJson(Map<String, Object> value) {
        try {
            return BASE64_URL_ENCODER.encodeToString(jsonMapper.writeValueAsBytes(value));
        } catch (Exception exception) {
            throw new JwtException("Could not encode JWT.", exception);
        }
    }

    private Map<String, Object> decodeJson(String value) {
        try {
            return jsonMapper.readValue(BASE64_URL_DECODER.decode(value), new TypeReference<>() {
            });
        } catch (Exception exception) {
            throw new JwtException("Could not decode JWT.", exception);
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return BASE64_URL_ENCODER.encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new JwtException("Could not sign JWT.", exception);
        }
    }

    private String stringClaim(Map<String, Object> payload, String name) {
        Object value = payload.get(name);
        if (value instanceof String string && !string.isBlank()) {
            return string;
        }
        throw new JwtException("Missing JWT claim: " + name);
    }

    private long numberClaim(Map<String, Object> payload, String name) {
        Object value = payload.get(name);
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new JwtException("Missing JWT claim: " + name);
    }

    private List<String> listClaim(Map<String, Object> payload, String name) {
        Object value = payload.get(name);
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .toList();
        }
        return List.of();
    }
}
