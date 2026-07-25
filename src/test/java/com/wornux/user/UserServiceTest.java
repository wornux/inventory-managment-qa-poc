package com.wornux.user;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.wornux.security.authorization.AuthorizationService;
import com.wornux.security.permission.AppPermission;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private RoleRepository roleRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void create_rejectsRoleWithPermissionsActorDoesNotHave() {
        authenticateActor(AppPermission.USER_CREATE, AppPermission.USER_ASSIGN);
        Role elevatedRole = role(AppPermission.PRODUCT_DELETE);
        when(roleRepository.findAllById(Set.of(7L))).thenReturn(java.util.List.of(elevatedRole));

        var request = new UserRequest();
        request.setUsername("new-user");
        request.setEmail("new-user@example.com");
        request.setRoleIds(Set.of(7L));
        var service = new UserService(
                appUserRepository,
                roleRepository,
                new AuthorizationService(appUserRepository));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(UserException.class)
                .hasMessage("You cannot assign a role containing permissions that you do not have.");
    }

    private void authenticateActor(AppPermission... permissions) {
        Role role = role(permissions);
        AppUser actor = new AppUser("admin", "admin@example.com", "issuer", "subject");
        actor.addRole(role);
        when(appUserRepository.findForAuthorization("admin")).thenReturn(java.util.Optional.of(actor));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", "password", java.util.List.of()));
    }

    private Role role(AppPermission... permissions) {
        Role role = new Role("TEST", "Test", null, false);
        role.update(role.getName(), role.getDescription(), true, Set.of(permissions));
        return role;
    }
}
