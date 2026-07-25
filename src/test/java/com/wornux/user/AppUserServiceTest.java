package com.wornux.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wornux.security.permission.AppPermission;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.DisabledException;

@ExtendWith(MockitoExtension.class)
class AppUserServiceTest {

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private RoleRepository roleRepository;

    @Test
    void authorities_includesPermissionsFromActiveRoles() {
        Role role = new Role("CATALOG_EDITOR", "Catalog Editor", null, false);
        role.update(role.getName(), role.getDescription(), true, Set.of(AppPermission.PRODUCT_UPDATE));
        AppUser user = new AppUser("editor", "editor@example.com", "issuer", "subject");
        user.addRole(role);

        var authorities = new AppUserService(appUserRepository, roleRepository).authorities(user);

        assertThat(authorities)
                .extracting(authority -> authority.getAuthority())
                .containsExactly("product:update");
    }

    @Test
    void authorities_ignoresInactiveRolesAndDeduplicatesPermissions() {
        Role active = UserDomainTest.role("A", true, AppPermission.PRODUCT_VIEW);
        Role duplicate = UserDomainTest.role("B", true, AppPermission.PRODUCT_VIEW);
        Role inactive = UserDomainTest.role("C", false, AppPermission.PRODUCT_DELETE);
        AppUser user = new AppUser("u", "e", "i", "s");
        Set.of(active, duplicate, inactive).forEach(user::addRole);

        assertThat(new AppUserService(appUserRepository, roleRepository).authorities(user))
                .extracting(a -> a.getAuthority())
                .containsExactly("product:view");
    }

    @Test
    void provisionOidcUser_createsNormalizedUserWithDefaultRole() {
        Role role = UserDomainTest.role(AppUserService.DEFAULT_ROLE_CODE, true, AppPermission.PRODUCT_VIEW);
        when(appUserRepository.findByOidcIssuerAndOidcSubject("issuer", "subject"))
                .thenReturn(Optional.empty());
        when(roleRepository.findByCode(AppUserService.DEFAULT_ROLE_CODE)).thenReturn(Optional.of(role));
        when(appUserRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        AppUser user = service().provisionOidcUser(new OidcUserProfile(" issuer ", " subject ", " user ", " U@E.COM "));

        assertThat(user.getUsername()).isEqualTo("user");
        assertThat(user.getEmail()).isEqualTo("u@e.com");
        assertThat(user.getRoles()).containsExactly(role);
    }

    @Test
    void provisionOidcUser_updatesExistingButRejectsInactiveAndConflicts() throws Exception {
        AppUser user = new AppUser("old", "old@e.com", "issuer", "subject");
        set(user, "id", 1L);
        when(appUserRepository.findByOidcIssuerAndOidcSubject("issuer", "subject"))
                .thenReturn(Optional.of(user));
        when(appUserRepository.save(user)).thenReturn(user);

        assertThat(service()
                        .provisionOidcUser(new OidcUserProfile("issuer", "subject", " new ", "N@E.COM"))
                        .getEmail())
                .isEqualTo("n@e.com");

        user.deactivate();

        assertThatThrownBy(
                        () -> service().provisionOidcUser(new OidcUserProfile("issuer", "subject", "new", "n@e.com")))
                .isInstanceOf(DisabledException.class);
        user.setActive(true);

        when(appUserRepository.existsByUsernameIgnoreCaseAndIdNot("new", 1L)).thenReturn(true);

        assertThatThrownBy(
                        () -> service().provisionOidcUser(new OidcUserProfile("issuer", "subject", "new", "n@e.com")))
                .isInstanceOf(OidcProvisioningException.class)
                .hasMessageContaining("Username");
        when(appUserRepository.existsByUsernameIgnoreCaseAndIdNot("new", 1L)).thenReturn(false);
        when(appUserRepository.existsByEmailIgnoreCaseAndIdNot("n@e.com", 1L)).thenReturn(true);

        assertThatThrownBy(
                        () -> service().provisionOidcUser(new OidcUserProfile("issuer", "subject", "new", "n@e.com")))
                .isInstanceOf(OidcProvisioningException.class)
                .hasMessageContaining("Email");
    }

    @Test
    void provisionNewUser_rejectsDuplicateIdentityAndMissingRole() {
        var profile = new OidcUserProfile("i", "s", "u", "e@x.com");
        when(appUserRepository.findByOidcIssuerAndOidcSubject("i", "s")).thenReturn(Optional.empty());
        when(appUserRepository.existsByUsernameIgnoreCase("u")).thenReturn(true);

        assertThatThrownBy(() -> service().provisionOidcUser(profile)).isInstanceOf(OidcProvisioningException.class);

        when(appUserRepository.existsByUsernameIgnoreCase("u")).thenReturn(false);
        when(appUserRepository.existsByEmailIgnoreCase("e@x.com")).thenReturn(true);

        assertThatThrownBy(() -> service().provisionOidcUser(profile)).isInstanceOf(OidcProvisioningException.class);

        when(appUserRepository.existsByEmailIgnoreCase("e@x.com")).thenReturn(false);
        when(roleRepository.findByCode(AppUserService.DEFAULT_ROLE_CODE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().provisionOidcUser(profile)).isInstanceOf(IllegalStateException.class);

        when(roleRepository.findByCode(AppUserService.DEFAULT_ROLE_CODE))
                .thenReturn(Optional.of(UserDomainTest.role("X", false, AppPermission.PRODUCT_VIEW)));

        assertThatThrownBy(() -> service().provisionOidcUser(profile)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void systemAdministrator_linksFallbackUserReactivatesAndDoesNotDuplicateRole() {
        Role admin = UserDomainTest.role(AppUserService.SYSTEM_ADMINISTRATOR_ROLE_CODE, true, AppPermission.USER_VIEW);
        AppUser user = new AppUser("old", "old@x.com", null, null);
        user.deactivate();
        user.addRole(UserDomainTest.role("OTHER", true, AppPermission.PRODUCT_VIEW));
        when(appUserRepository.findByOidcIssuerAndOidcSubject("i", "s")).thenReturn(Optional.empty());
        when(appUserRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase("u", "e@x.com"))
                .thenReturn(Optional.of(user));
        when(roleRepository.findByCode(AppUserService.SYSTEM_ADMINISTRATOR_ROLE_CODE))
                .thenReturn(Optional.of(admin));
        when(appUserRepository.save(user)).thenReturn(user);

        assertThat(service()
                        .provisionSystemAdministrator(new OidcUserProfile("i", "s", "u", "e@x.com"))
                        .getRoles())
                .contains(admin);
        assertThat(user.isActive()).isTrue();

        service().provisionSystemAdministrator(new OidcUserProfile("i", "s", "u", "e@x.com"));

        assertThat(user.getRoles()).contains(admin);
    }

    @Test
    void systemAdministrator_createsUserWhenNoIdentityMatches() {
        Role admin = UserDomainTest.role(AppUserService.SYSTEM_ADMINISTRATOR_ROLE_CODE, true, AppPermission.USER_VIEW);
        when(appUserRepository.findByOidcIssuerAndOidcSubject("i", "s")).thenReturn(Optional.empty());
        when(appUserRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase("u", "e@x.com"))
                .thenReturn(Optional.empty());
        when(roleRepository.findByCode(AppUserService.SYSTEM_ADMINISTRATOR_ROLE_CODE))
                .thenReturn(Optional.of(admin));
        when(appUserRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertThat(service()
                        .provisionSystemAdministrator(new OidcUserProfile("i", "s", "u", "e@x.com"))
                        .getRoles())
                .containsExactly(admin);
    }

    @Test
    void createLocalUser_normalizesAndRequiresAllRolesActive() {
        UserRequest request = new UserRequest();
        request.setUsername(null);
        request.setEmail(" E@X.COM ");
        request.setActive(false);
        request.setRoleIds(Set.of(1L));
        Role role = UserDomainTest.role("R", true, AppPermission.USER_VIEW);
        when(roleRepository.findAllById(Set.of(1L))).thenReturn(List.of(role));
        when(appUserRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        AppUser user = service().createLocalUser(request);

        assertThat(user.getUsername()).isEmpty();
        assertThat(user.getEmail()).isEqualTo("e@x.com");
        assertThat(user.isActive()).isFalse();

        request.setRoleIds(null);

        assertThatThrownBy(() -> service().createLocalUser(request)).isInstanceOf(UserException.class);

        request.setRoleIds(Set.of());

        assertThatThrownBy(() -> service().createLocalUser(request)).isInstanceOf(UserException.class);

        request.setRoleIds(Set.of(1L));
        when(roleRepository.findAllById(Set.of(1L))).thenReturn(List.of());

        assertThatThrownBy(() -> service().createLocalUser(request)).isInstanceOf(UserException.class);

        request.setEmail(null);
        when(roleRepository.findAllById(Set.of(1L))).thenReturn(List.of(role));
        request.setUsername(" x ");

        assertThat(service().createLocalUser(request).getEmail()).isEmpty();

        UserRequest nullRoles = mock(UserRequest.class);
        when(nullRoles.getRoleIds()).thenReturn(null);

        assertThatThrownBy(() -> service().createLocalUser(nullRoles)).isInstanceOf(UserException.class);
    }

    private AppUserService service() {
        return new AppUserService(appUserRepository, roleRepository);
    }

    private static void set(Object target, String field, Object value) throws Exception {
        var declared = target.getClass().getDeclaredField(field);
        declared.setAccessible(true);

        declared.set(target, value);
    }
}
