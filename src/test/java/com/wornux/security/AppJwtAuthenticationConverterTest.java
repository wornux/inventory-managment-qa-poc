package com.wornux.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wornux.user.AppUser;
import com.wornux.user.AppUserService;
import com.wornux.user.OidcUserProfile;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class AppJwtAuthenticationConverterTest {
    @Mock
    AppUserService service;

    @Test
    void provisionsProfileAndUsesLocalUsernameAndAuthorities() {
        AppUser user = new AppUser("local", "mail@example.com", "issuer", "subject");
        when(service.provisionOidcUser(any())).thenReturn(user);
        when(service.authorities(user)).thenReturn(List.of(new SimpleGrantedAuthority("product:view")));
        Jwt jwt = new Jwt(
                "token",
                Instant.EPOCH,
                Instant.MAX,
                Map.of("alg", "none"),
                Map.of(
                        "iss",
                        URI.create("https://issuer"),
                        "sub",
                        "subject",
                        "preferred_username",
                        "remote",
                        "email",
                        "mail@example.com"));

        var authentication = new AppJwtAuthenticationConverter(service).convert(jwt);

        assertThat(authentication.getName()).isEqualTo("local");
        assertThat(authentication.getAuthorities()).extracting("authority").containsExactly("product:view");

        ArgumentCaptor<OidcUserProfile> profile = ArgumentCaptor.forClass(OidcUserProfile.class);

        verify(service).provisionOidcUser(profile.capture());
        assertThat(profile.getValue())
                .isEqualTo(new OidcUserProfile("https://issuer", "subject", "remote", "mail@example.com"));
    }

    @Test
    void absentIssuerIsPreserved() {
        AppUser user = new AppUser("local", "mail@example.com", "issuer", "subject");
        when(service.provisionOidcUser(any())).thenReturn(user);
        when(service.authorities(user)).thenReturn(List.of());
        Jwt jwt = new Jwt("token", Instant.EPOCH, Instant.MAX, Map.of("alg", "none"), Map.of("sub", "subject"));

        new AppJwtAuthenticationConverter(service).convert(jwt);

        ArgumentCaptor<OidcUserProfile> profile = ArgumentCaptor.forClass(OidcUserProfile.class);

        verify(service).provisionOidcUser(profile.capture());
        assertThat(profile.getValue().issuer()).isNull();
    }
}
