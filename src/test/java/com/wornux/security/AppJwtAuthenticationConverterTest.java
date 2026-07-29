package com.wornux.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wornux.security.authorization.AuthorizationService;
import com.wornux.security.authorization.UserAccessSnapshot;
import com.wornux.security.permission.AppPermission;
import com.wornux.user.AppUser;
import com.wornux.user.AppUserService;
import com.wornux.user.OidcUserProfile;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class AppJwtAuthenticationConverterTest {
    @Mock
    AppUserService service;

    @Mock
    AuthorizationService authorizationService;

    @Test
    void provisionsProfileAndUsesLocalUsernameAndAuthorities() {
        AppUser user = new AppUser("local", "mail@example.com", "issuer", "subject");
        when(authorizationService.cached("remote")).thenReturn(Optional.empty());
        when(service.provisionOidcUser(any())).thenReturn(user);
        when(authorizationService.cache(user))
                .thenReturn(new UserAccessSnapshot(null, "local", true, 10, Set.of(AppPermission.PRODUCT_VIEW)));
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

        var authentication = new AppJwtAuthenticationConverter(service, authorizationService).convert(jwt);

        assertThat(authentication.getName()).isEqualTo("local");
        assertThat(authentication.getAuthorities()).extracting("authority").containsExactly("product:view");

        ArgumentCaptor<OidcUserProfile> profile = ArgumentCaptor.forClass(OidcUserProfile.class);

        verify(service).provisionOidcUser(profile.capture());
        verify(authorizationService).cache(user);
        assertThat(profile.getValue())
                .isEqualTo(new OidcUserProfile("https://issuer", "subject", "remote", "mail@example.com"));
    }

    @Test
    void absentIssuerIsPreserved() {
        AppUser user = new AppUser("local", "mail@example.com", "issuer", "subject");
        when(service.provisionOidcUser(any())).thenReturn(user);
        when(authorizationService.cache(user)).thenReturn(new UserAccessSnapshot(null, "local", true, -1, Set.of()));
        Jwt jwt = new Jwt("token", Instant.EPOCH, Instant.MAX, Map.of("alg", "none"), Map.of("sub", "subject"));

        new AppJwtAuthenticationConverter(service, authorizationService).convert(jwt);

        ArgumentCaptor<OidcUserProfile> profile = ArgumentCaptor.forClass(OidcUserProfile.class);

        verify(service).provisionOidcUser(profile.capture());
        assertThat(profile.getValue().issuer()).isNull();
    }

    @Test
    void cachedAccessSkipsDatabaseProvisioning() {
        var access = new UserAccessSnapshot(7L, "local", true, 10, Set.of(AppPermission.PRODUCT_VIEW));
        when(authorizationService.cached("remote")).thenReturn(Optional.of(access));
        Jwt jwt = new Jwt(
                "token",
                Instant.EPOCH,
                Instant.MAX,
                Map.of("alg", "none"),
                Map.of("sub", "subject", "preferred_username", "remote", "email", "mail@example.com"));

        var authentication = new AppJwtAuthenticationConverter(service, authorizationService).convert(jwt);

        assertThat(authentication.getName()).isEqualTo("local");
        verify(service, never()).provisionOidcUser(any());
    }
}
