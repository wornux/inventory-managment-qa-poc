package com.wornux.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wornux.user.AppUser;
import com.wornux.user.AppUserService;
import com.wornux.user.OidcProvisioningException;
import com.wornux.user.OidcUserProfile;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AppOidcUserServiceTest {
    @Mock
    AppUserService users;

    @Mock
    OidcUserService delegate;

    @Mock
    OidcUserRequest request;

    private AppOidcUserService service;

    @BeforeEach
    void setUp() {
        service = new AppOidcUserService(users);
        ReflectionTestUtils.setField(service, "delegate", delegate);
    }

    @Test
    void mergesProviderAndApplicationAuthoritiesAndProvisionsProfile() {
        var provider = provider(Map.of(
                "iss",
                "https://issuer",
                "sub",
                "subject",
                "preferred_username",
                "remote",
                "email",
                "mail@example.com"));
        AppUser local = new AppUser("local", "mail@example.com", "https://issuer", "subject");
        when(delegate.loadUser(request)).thenReturn(provider);
        when(users.provisionOidcUser(any())).thenReturn(local);
        when(users.authorities(local)).thenReturn(List.of(new SimpleGrantedAuthority("product:view")));

        var result = service.loadUser(request);

        assertThat(result.getName()).isEqualTo("remote");
        assertThat(result.getAuthorities())
                .extracting("authority")
                .containsExactlyInAnyOrder("OIDC_USER", "product:view");

        ArgumentCaptor<OidcUserProfile> profile = ArgumentCaptor.forClass(OidcUserProfile.class);

        verify(users).provisionOidcUser(profile.capture());
        assertThat(profile.getValue())
                .isEqualTo(new OidcUserProfile("https://issuer", "subject", "remote", "mail@example.com"));
    }

    @Test
    void profileAllowsAbsentIssuer() {
        var provider = provider(Map.of("sub", "subject", "preferred_username", "remote", "email", "mail@example.com"));
        AppUser local = new AppUser("local", "mail@example.com", "issuer", "subject");
        when(delegate.loadUser(request)).thenReturn(provider);
        when(users.provisionOidcUser(any())).thenReturn(local);
        when(users.authorities(local)).thenReturn(List.of());

        service.loadUser(request);

        ArgumentCaptor<OidcUserProfile> profile = ArgumentCaptor.forClass(OidcUserProfile.class);

        verify(users).provisionOidcUser(profile.capture());
        assertThat(profile.getValue().issuer()).isNull();
    }

    @Test
    void translatesProvisioningAndAuthenticationFailuresToOAuthErrors() {
        when(delegate.loadUser(request)).thenReturn(provider(Map.of("sub", "subject", "preferred_username", "remote")));
        doThrow(new OidcProvisioningException("cannot provision"))
                .doThrow(new BadCredentialsException("disabled"))
                .when(users)
                .provisionOidcUser(any());

        assertOAuthFailure("cannot provision", OidcProvisioningException.class);
        assertOAuthFailure("disabled", BadCredentialsException.class);
    }

    private void assertOAuthFailure(String message, Class<? extends RuntimeException> cause) {
        assertThatThrownBy(() -> service.loadUser(request))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class, exception -> {
                    assertThat(exception.getError().getErrorCode()).isEqualTo("oidc_provisioning_failed");
                    assertThat(exception).hasMessage(message).hasCauseInstanceOf(cause);
                });
    }

    private static DefaultOidcUser provider(Map<String, Object> claims) {
        var token = new OidcIdToken("token", Instant.EPOCH, Instant.MAX, claims);
        return new DefaultOidcUser(List.of(new SimpleGrantedAuthority("OIDC_USER")), token, "preferred_username");
    }
}
