package com.wornux.usecases.uc015_keycloak_oauth_authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.vaadin.flow.spring.security.AuthenticationContext;
import com.wornux.security.AppJwtAuthenticationConverter;
import com.wornux.security.KeycloakAdminBootstrap;
import com.wornux.security.KeycloakAdminBootstrapProperties;
import com.wornux.security.KeycloakAdminClient;
import com.wornux.ui.MainLayout;
import com.wornux.ui.security.UiAccessService;
import com.wornux.user.AppUser;
import com.wornux.user.AppUserRepository;
import com.wornux.user.AppUserService;
import com.wornux.user.OidcProvisioningException;
import com.wornux.user.OidcUserProfile;
import com.wornux.user.Role;
import com.wornux.user.RoleRepository;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UC015KeycloakOauthAuthenticationTest {

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private RoleRepository roleRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void mainFlow_autoProvisionedOidcUserGetsViewerRoleAndNoPassword() {
        AppUserService service = appUserService();
        Role viewer = new Role("INVENTORY_VIEWER", "Inventory Viewer", "Default role");
        when(appUserRepository.findByOidcIssuerAndOidcSubject("https://issuer.test", "subject-1"))
                .thenReturn(Optional.empty());
        when(appUserRepository.existsByUsernameIgnoreCase("viewer")).thenReturn(false);
        when(appUserRepository.existsByEmailIgnoreCase("viewer@example.com")).thenReturn(false);
        when(roleRepository.findByCode("INVENTORY_VIEWER")).thenReturn(Optional.of(viewer));
        when(appUserRepository.save(org.mockito.ArgumentMatchers.any(AppUser.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AppUser user = service.provisionOidcUser(new OidcUserProfile(
                "https://issuer.test",
                "subject-1",
                "viewer",
                "VIEWER@EXAMPLE.COM"));

        assertThat(user.getOidcIssuer()).isEqualTo("https://issuer.test");
        assertThat(user.getOidcSubject()).isEqualTo("subject-1");
        assertThat(user.getUsername()).isEqualTo("viewer");
        assertThat(user.getEmail()).isEqualTo("viewer@example.com");
        assertThat(user.getRoles()).containsExactly(viewer);
        assertThat(Arrays.stream(AppUser.class.getDeclaredFields()).map(java.lang.reflect.Field::getName))
                .doesNotContain("passwordHash");
    }

    @Test
    void af2_missingRequiredClaimRejectsProvisioning() {
        AppUserService service = appUserService();

        assertThatThrownBy(() -> service.provisionOidcUser(new OidcUserProfile(
                "https://issuer.test",
                "subject-1",
                "viewer",
                " ")))
                .isInstanceOf(OidcProvisioningException.class)
                .hasMessage("Missing required OIDC claim: email");
    }

    @Test
    void af3_inactiveLocalUserIsDenied() {
        AppUserService service = appUserService();
        AppUser existing = user("viewer", "viewer@example.com", "https://issuer.test", "subject-1");
        ReflectionTestUtils.setField(existing, "id", 7L);
        existing.deactivate();
        when(appUserRepository.findByOidcIssuerAndOidcSubject("https://issuer.test", "subject-1"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.provisionOidcUser(new OidcUserProfile(
                "https://issuer.test",
                "subject-1",
                "viewer",
                "viewer@example.com")))
                .isInstanceOf(DisabledException.class)
                .hasMessage("Account is inactive.");
    }

    @Test
    void af4_usernameConflictRejectsProvisioning() {
        AppUserService service = appUserService();
        when(appUserRepository.findByOidcIssuerAndOidcSubject("https://issuer.test", "subject-2"))
                .thenReturn(Optional.empty());
        when(appUserRepository.existsByUsernameIgnoreCase("viewer")).thenReturn(true);

        assertThatThrownBy(() -> service.provisionOidcUser(new OidcUserProfile(
                "https://issuer.test",
                "subject-2",
                "viewer",
                "viewer@example.com")))
                .isInstanceOf(OidcProvisioningException.class)
                .hasMessage("Username already exists for another local user.");
    }

    @Test
    void af5_adminBootstrapFailureFailsStartup() {
        KeycloakAdminBootstrapProperties properties = bootstrapProperties();
        KeycloakAdminClient keycloakAdminClient = mock(KeycloakAdminClient.class);
        AppUserService appUserService = mock(AppUserService.class);
        when(keycloakAdminClient.adminToken(properties)).thenThrow(new IllegalStateException("connection refused"));
        KeycloakAdminBootstrap bootstrap = new KeycloakAdminBootstrap(properties, keycloakAdminClient, appUserService);

        assertThatThrownBy(() -> bootstrap.run(mock(ApplicationArguments.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Keycloak admin bootstrap failed.")
                .hasRootCauseMessage("connection refused");
    }

    @Test
    void af6_apiJwtCreatesAuthenticatedLocalUserPrincipal() {
        AppUser appUser = user("api-user", "api@example.com", "https://issuer.test", "api-subject");
        appUser.addRole(new Role("INVENTORY_VIEWER", "Inventory Viewer", "Default role"));
        AppUserService appUserService = mock(AppUserService.class);
        when(appUserService.provisionOidcUser(new OidcUserProfile(
                "https://issuer.test",
                "api-subject",
                "api-user",
                "api@example.com")))
                .thenReturn(appUser);
        when(appUserService.authorities(appUser)).thenCallRealMethod();
        AppJwtAuthenticationConverter converter = new AppJwtAuthenticationConverter(appUserService);
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuer("https://issuer.test")
                .subject("api-subject")
                .claim("preferred_username", "api-user")
                .claim("email", "api@example.com")
                .build();

        JwtAuthenticationToken authentication = (JwtAuthenticationToken) converter.convert(jwt);

        assertThat(authentication.getName()).isEqualTo("api-user");
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .contains("ROLE_INVENTORY_VIEWER");
    }

    @Test
    void af7_legacyPasswordAuthEndpointsAreRemoved() {
        assertThatThrownBy(() -> Class.forName("com.wornux.api.auth.SecurityController"))
                .isInstanceOf(ClassNotFoundException.class);
        assertThatThrownBy(() -> Class.forName("com.wornux.api.auth.LoginRequest"))
                .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void br05_systemAdministratorBootstrapUsesAdministratorRole() {
        AppUserService service = appUserService();
        Role administrator = new Role("SYSTEM_ADMINISTRATOR", "System Administrator", "Admin role");
        when(appUserRepository.findByOidcIssuerAndOidcSubject("http://localhost:7777/realms/wornux", "admin-subject"))
                .thenReturn(Optional.empty());
        when(appUserRepository.existsByUsernameIgnoreCase("admin@wornux.com")).thenReturn(false);
        when(appUserRepository.existsByEmailIgnoreCase("admin@wornux.com")).thenReturn(false);
        when(roleRepository.findByCode("SYSTEM_ADMINISTRATOR")).thenReturn(Optional.of(administrator));
        when(appUserRepository.save(org.mockito.ArgumentMatchers.any(AppUser.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AppUser user = service.provisionSystemAdministrator(new OidcUserProfile(
                "http://localhost:7777/realms/wornux",
                "admin-subject",
                "admin@wornux.com",
                "admin@wornux.com"));

        assertThat(user.getRoles()).containsExactly(administrator);
    }

    @Test
    void br06_mainLayoutSupportsOidcPrincipalWithoutUserDetailsCast() {
        var oidcUser = new DefaultOidcUser(
                List.of(),
                new OidcIdToken("token", Instant.now(), Instant.now().plusSeconds(60), Map.of(
                        "sub", "subject-1",
                        "iss", "https://issuer.test",
                        "preferred_username", "oidc-user",
                        "email", "oidc@example.com")),
                "preferred_username");
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(oidcUser, null));
        UiAccessService accessService = mock(UiAccessService.class);
        AuthenticationContext authenticationContext = mock(AuthenticationContext.class);

        MainLayout layout = new MainLayout(authenticationContext, accessService);

        assertThat(layout.getElement().getTextRecursively()).contains("oidc-user");
    }

    @Test
    void br08AndBr09_passwordSignupSurfacesAreRemoved() {
        assertThatThrownBy(() -> Class.forName("com.wornux.ui.views.SignupView"))
                .isInstanceOf(ClassNotFoundException.class);
        assertThatThrownBy(() -> Class.forName("com.wornux.user.SignupRequest"))
                .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void br10_logoutRedirectUsesCurrentBaseUrlLoginPath() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/wornux/security/SecurityConfig.java"));

        assertThat(source)
                .contains("oauth2LoginPage(\"/oauth2/authorization/keycloak\", \"{baseUrl}/login\")");
    }

    private AppUserService appUserService() {
        return new AppUserService(appUserRepository, roleRepository);
    }

    private AppUser user(String username, String email, String issuer, String subject) {
        AppUser user = new AppUser(username, email, issuer, subject);
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }

    private KeycloakAdminBootstrapProperties bootstrapProperties() {
        return new KeycloakAdminBootstrapProperties(
                true,
                "http://localhost:7777",
                "wornux",
                "master",
                "admin-cli",
                "admin",
                "admin-password",
                "admin@wornux.com",
                "admin@wornux.com",
                "user-password");
    }
}
