package com.wornux.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.spring.security.AuthenticationContext;
import com.wornux.security.authorization.AuthorizationService;
import com.wornux.security.permission.AppPermission;
import com.wornux.ui.components.DrawerRailToggle;
import com.wornux.ui.views.ForbiddenView;
import com.wornux.ui.views.HomeView;
import com.wornux.ui.views.NoAccessView;
import com.wornux.ui.views.ProductsView;
import com.wornux.ui.views.RolesView;
import com.wornux.user.Role;
import com.wornux.user.RoleService;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

class UiBehaviorTest {
    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void drawerRailToggleExposesItsCustomElementContract() {
        var toggle = new DrawerRailToggle();

        assertThat(toggle.getElement().getTag()).isEqualTo("drawer-rail-toggle");
    }

    @Test
    void layoutShowsOnlyAllowedNavigationAndAuthenticatedUsername() {
        var access = mock(AuthorizationService.class);
        when(access.can(AppPermission.PRODUCT_VIEW)).thenReturn(true);
        when(access.can(AppPermission.REPORT_VIEW)).thenReturn(true);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(
                        User.withUsername("alice").password("x").roles("USER").build(), "x", List.of()));
        var layout = new MainLayout(mock(AuthenticationContext.class), access);
        var text = descendants(layout)
                .map(Component::getElement)
                .map(e -> e.getText())
                .toList();

        assertThat(text).contains("Overview", "Products", "alice", "Sign out");
        assertThat(text).doesNotContain("Categories", "Users", "No modules available");
    }

    @Test
    void layoutShowsOnlyOverviewWithoutAnEmptyStateForReportOnlyAccess() {
        var access = mock(AuthorizationService.class);
        when(access.can(AppPermission.REPORT_VIEW)).thenReturn(true);

        assertThat(textOf(new MainLayout(mock(AuthenticationContext.class), access)))
                .contains("Overview")
                .doesNotContain("Products", "Users", "No modules available");
    }

    @Test
    void layoutShowsEmptyStateAndFallbackUsernameWhenNothingIsAllowed() {
        var layout = new MainLayout(mock(AuthenticationContext.class), mock(AuthorizationService.class));

        assertThat(descendants(layout)
                        .filter(Span.class::isInstance)
                        .map(c -> c.getElement().getText()))
                .contains("No modules available", "User");
    }

    @Test
    void layoutCanShowAdministrationWithoutInventory() {
        var access = mock(AuthorizationService.class);
        when(access.can(AppPermission.USER_VIEW)).thenReturn(true);
        var text = textOf(new MainLayout(mock(AuthenticationContext.class), access));

        assertThat(text).contains("Users").doesNotContain("Products", "No modules available");
    }

    @Test
    void layoutUsesAuthenticationNameAndFallsBackWhenItIsBlank() {
        var authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(new Object());
        when(authentication.getName()).thenReturn("bob");
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThat(textOf(new MainLayout(mock(AuthenticationContext.class), mock(AuthorizationService.class))))
                .contains("bob");
        when(authentication.getName()).thenReturn(" ");

        assertThat(textOf(new MainLayout(mock(AuthenticationContext.class), mock(AuthorizationService.class))))
                .contains("User");
        when(authentication.getName()).thenReturn(null);

        assertThat(textOf(new MainLayout(mock(AuthenticationContext.class), mock(AuthorizationService.class))))
                .contains("User");
    }

    @Test
    void layoutUsesPreferredOidcUsernameWithIdentityNameAsFallback() {
        var authentication = mock(Authentication.class);
        var principal = mock(OidcUser.class);
        when(authentication.getPrincipal()).thenReturn(principal);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        when(principal.getClaimAsString("preferred_username")).thenReturn("alice");

        assertThat(textOf(new MainLayout(mock(AuthenticationContext.class), mock(AuthorizationService.class))))
                .contains("alice");
        when(principal.getClaimAsString("preferred_username")).thenReturn(null);
        when(principal.getName()).thenReturn("subject");

        assertThat(textOf(new MainLayout(mock(AuthenticationContext.class), mock(AuthorizationService.class))))
                .contains("subject");
        when(principal.getClaimAsString("preferred_username")).thenReturn(" ");

        assertThat(textOf(new MainLayout(mock(AuthenticationContext.class), mock(AuthorizationService.class))))
                .contains("subject");
    }

    @Test
    void profileDrawerShowsOidcIdentityAndOnlyLogoutOption() {
        var authentication = mock(Authentication.class);
        var principal = mock(OidcUser.class);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(principal.getClaimAsString("preferred_username")).thenReturn("alice");
        when(principal.getClaimAsString("name")).thenReturn("Alice Example");
        when(principal.getEmail()).thenReturn("alice@example.com");
        SecurityContextHolder.getContext().setAuthentication(authentication);

        var layout = new MainLayout(mock(AuthenticationContext.class), mock(AuthorizationService.class));
        var profile = descendants(layout)
                .filter(Details.class::isInstance)
                .map(Details.class::cast)
                .findFirst()
                .orElseThrow();

        assertThat(textOf(profile)).contains("Alice Example", "alice@example.com", "Sign out");
        assertThat(profile.isOpened()).isFalse();
        assertThat(descendants(profile)
                        .filter(Button.class::isInstance)
                        .map(Button.class::cast)
                        .map(Button::getText))
                .containsExactly("Sign out");
    }

    @Test
    void logoutButtonInvokesAuthenticationContext() {
        var authentication = mock(AuthenticationContext.class);
        var layout = new MainLayout(authentication, mock(AuthorizationService.class));
        descendants(layout)
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(button -> "Sign out".equals(button.getText()))
                .findFirst()
                .orElseThrow()
                .click();

        verify(authentication).logout();
    }

    @Test
    void rolesViewShowsMemberActionsWithUpdateAndAssignmentPermissions() {
        var roleService = mock(RoleService.class);
        var role = new Role("TEST", "Test role", null);
        when(roleService.assignablePermissions()).thenReturn(List.of());
        when(roleService.search(any())).thenReturn(List.of(role));
        when(roleService.userCounts(any())).thenReturn(new HashMap<>());
        when(roleService.members(null)).thenReturn(List.of());
        when(roleService.canAssignRole(role)).thenReturn(true);
        var view = new RolesView(roleService);
        var tabs = descendants(view)
                .filter(Tabs.class::isInstance)
                .map(Tabs.class::cast)
                .findFirst()
                .orElseThrow();

        tabs.setSelectedIndex(2);

        assertThat(descendants(view)
                        .filter(Button.class::isInstance)
                        .map(Button.class::cast)
                        .map(Button::getText))
                .contains("Add member");
    }

    @Test
    void rolesViewShowsEditForPriorityOneHundredWithRoleUpdatePermission() {
        var roleService = mock(RoleService.class);
        var role = new Role("SYSTEM", "System role", null);
        role.update(role.getName(), role.getDescription(), 100, true, Set.of(AppPermission.ROLE_UPDATE));
        when(roleService.assignablePermissions()).thenReturn(List.of());
        when(roleService.search(any())).thenReturn(List.of(role));
        when(roleService.userCounts(any())).thenReturn(new HashMap<>());
        when(roleService.canUpdateRole(role)).thenReturn(true);

        var view = new RolesView(roleService);

        assertThat(descendants(view)
                        .filter(Button.class::isInstance)
                        .map(Button.class::cast)
                        .map(Button::getText))
                .contains("Edit role");
    }

    @Test
    void noAccessViewExplainsTheRecoveryPath() {
        assertThat(textOf(new NoAccessView()))
                .contains(
                        "$ accessctl status --current-user",
                        "No access assigned",
                        "Contact an administrator and ask them to assign the role you need.");
    }

    @ParameterizedTest
    @MethodSource("protectedRoutes")
    void registeredRoutesRequireTheirExactPermission(Class<? extends Component> target, AppPermission permission) {
        var access = mock(AuthorizationService.class);
        when(access.effectivePermissions()).thenReturn(Set.of(permission));
        var layout = new MainLayout(mock(AuthenticationContext.class), access);
        var denied = mock(BeforeEnterEvent.class);
        doReturn(target).when(denied).getNavigationTarget();

        layout.beforeEnter(denied);

        verify(denied).rerouteTo(ForbiddenView.class);
        when(access.can(permission)).thenReturn(true);
        var allowed = mock(BeforeEnterEvent.class);
        doReturn(target).when(allowed).getNavigationTarget();

        layout.beforeEnter(allowed);

        verify(allowed, never()).rerouteTo(ForbiddenView.class);
    }

    @Test
    void publicAndUnknownRoutesAreHandledWithoutRedirectLoops() {
        var access = mock(AuthorizationService.class);
        when(access.effectivePermissions()).thenReturn(Set.of(AppPermission.REPORT_VIEW));
        var layout = new MainLayout(mock(AuthenticationContext.class), access);
        var publicRoute = mock(BeforeEnterEvent.class);
        doReturn(ForbiddenView.class).when(publicRoute).getNavigationTarget();

        layout.beforeEnter(publicRoute);

        verify(publicRoute, never()).rerouteTo(ForbiddenView.class);
        var unknownRoute = mock(BeforeEnterEvent.class);
        doReturn(Div.class).when(unknownRoute).getNavigationTarget();

        layout.beforeEnter(unknownRoute);

        verify(unknownRoute).rerouteTo(ForbiddenView.class);
    }

    @Test
    void usersWithoutPermissionsRerouteToNoAccessWithoutCreatingARedirectLoop() {
        var access = mock(AuthorizationService.class);
        when(access.effectivePermissions()).thenReturn(Set.of());
        var layout = new MainLayout(mock(AuthenticationContext.class), access);
        var denied = mock(BeforeEnterEvent.class);
        doReturn(HomeView.class).when(denied).getNavigationTarget();

        layout.beforeEnter(denied);

        verify(denied).rerouteTo(NoAccessView.class);

        var noAccess = mock(BeforeEnterEvent.class);
        doReturn(NoAccessView.class).when(noAccess).getNavigationTarget();

        layout.beforeEnter(noAccess);

        verify(noAccess, never()).rerouteTo(NoAccessView.class);
    }

    private static Stream<Arguments> protectedRoutes() {
        return Stream.of(
                arguments(HomeView.class, AppPermission.REPORT_VIEW),
                arguments(ProductsView.class, AppPermission.PRODUCT_VIEW));
    }

    private static Stream<Component> descendants(Component root) {
        return Stream.concat(Stream.of(root), root.getChildren().flatMap(UiBehaviorTest::descendants));
    }

    private static List<String> textOf(Component root) {
        return descendants(root).map(c -> c.getElement().getText()).toList();
    }
}
