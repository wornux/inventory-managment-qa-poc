package com.wornux.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.spring.security.AuthenticationContext;
import com.wornux.security.authorization.AuthorizationService;
import com.wornux.security.permission.AppPermission;
import com.wornux.ui.components.DrawerRailToggle;
import com.wornux.ui.security.UiAccessService;
import com.wornux.ui.views.ForbiddenView;
import com.wornux.ui.views.ProductsView;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
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
    void accessServiceDelegatesTheExactPermission() {
        var authorization = mock(AuthorizationService.class);
        when(authorization.can(AppPermission.PRODUCT_VIEW)).thenReturn(true);

        assertThat(new UiAccessService(authorization).canRead(AppPermission.PRODUCT_VIEW))
                .isTrue();
        verify(authorization).can(AppPermission.PRODUCT_VIEW);
    }

    @Test
    void drawerRailToggleExposesItsCustomElementContract() {
        var toggle = new DrawerRailToggle();

        assertThat(toggle.getElement().getTag()).isEqualTo("drawer-rail-toggle");
    }

    @Test
    void layoutShowsOnlyAllowedNavigationAndAuthenticatedUsername() {
        var access = mock(UiAccessService.class);
        when(access.canRead(AppPermission.PRODUCT_VIEW)).thenReturn(true);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(
                        User.withUsername("alice").password("x").roles("USER").build(), "x", List.of()));
        var layout = new MainLayout(mock(AuthenticationContext.class), access);
        var text = descendants(layout)
                .map(Component::getElement)
                .map(e -> e.getText())
                .toList();

        assertThat(text).contains("Products", "alice", "Sign out");
        assertThat(text).doesNotContain("Categories", "Users", "No modules available");
    }

    @Test
    void layoutShowsEmptyStateAndFallbackUsernameWhenNothingIsAllowed() {
        var layout = new MainLayout(mock(AuthenticationContext.class), mock(UiAccessService.class));

        assertThat(descendants(layout)
                        .filter(Span.class::isInstance)
                        .map(c -> c.getElement().getText()))
                .contains("No modules available", "User");
    }

    @Test
    void layoutCanShowAdministrationWithoutInventory() {
        var access = mock(UiAccessService.class);
        when(access.canRead(AppPermission.USER_VIEW)).thenReturn(true);
        var text = textOf(new MainLayout(mock(AuthenticationContext.class), access));

        assertThat(text).contains("Users").doesNotContain("Products", "No modules available");
    }

    @Test
    void layoutUsesAuthenticationNameAndFallsBackWhenItIsBlank() {
        var authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(new Object());
        when(authentication.getName()).thenReturn("bob");
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThat(textOf(new MainLayout(mock(AuthenticationContext.class), mock(UiAccessService.class))))
                .contains("bob");
        when(authentication.getName()).thenReturn(" ");

        assertThat(textOf(new MainLayout(mock(AuthenticationContext.class), mock(UiAccessService.class))))
                .contains("User");
        when(authentication.getName()).thenReturn(null);

        assertThat(textOf(new MainLayout(mock(AuthenticationContext.class), mock(UiAccessService.class))))
                .contains("User");
    }

    @Test
    void layoutUsesPreferredOidcUsernameWithIdentityNameAsFallback() {
        var authentication = mock(Authentication.class);
        var principal = mock(OidcUser.class);
        when(authentication.getPrincipal()).thenReturn(principal);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        when(principal.getClaimAsString("preferred_username")).thenReturn("alice");

        assertThat(textOf(new MainLayout(mock(AuthenticationContext.class), mock(UiAccessService.class))))
                .contains("alice");
        when(principal.getClaimAsString("preferred_username")).thenReturn(null);
        when(principal.getName()).thenReturn("subject");

        assertThat(textOf(new MainLayout(mock(AuthenticationContext.class), mock(UiAccessService.class))))
                .contains("subject");
        when(principal.getClaimAsString("preferred_username")).thenReturn(" ");

        assertThat(textOf(new MainLayout(mock(AuthenticationContext.class), mock(UiAccessService.class))))
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

        var layout = new MainLayout(mock(AuthenticationContext.class), mock(UiAccessService.class));
        var profile = descendants(layout).filter(Details.class::isInstance).map(Details.class::cast).findFirst().orElseThrow();

        assertThat(textOf(profile)).contains("Alice Example", "alice@example.com", "Sign out");
        assertThat(profile.isOpened()).isFalse();
        assertThat(descendants(profile).filter(Button.class::isInstance).map(Button.class::cast).map(Button::getText))
                .containsExactly("Sign out");
    }

    @Test
    void logoutButtonInvokesAuthenticationContext() {
        var authentication = mock(AuthenticationContext.class);
        var layout = new MainLayout(authentication, mock(UiAccessService.class));
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
    void protectedRouteReroutesWithoutPermissionAndAllowsWithPermission() {
        var access = mock(UiAccessService.class);
        var layout = new MainLayout(mock(AuthenticationContext.class), access);
        var denied = mock(BeforeEnterEvent.class);
        doReturn(ProductsView.class).when(denied).getNavigationTarget();

        layout.beforeEnter(denied);

        verify(denied).rerouteTo(ForbiddenView.class);

        when(access.canRead(AppPermission.PRODUCT_VIEW)).thenReturn(true);
        var allowed = mock(BeforeEnterEvent.class);
        doReturn(ProductsView.class).when(allowed).getNavigationTarget();

        layout.beforeEnter(allowed);

        verify(allowed, never()).rerouteTo(ForbiddenView.class);

        var publicRoute = mock(BeforeEnterEvent.class);
        doReturn(ForbiddenView.class).when(publicRoute).getNavigationTarget();

        layout.beforeEnter(publicRoute);

        verify(publicRoute, never()).rerouteTo(ForbiddenView.class);
    }

    private static Stream<Component> descendants(Component root) {
        return Stream.concat(Stream.of(root), root.getChildren().flatMap(UiBehaviorTest::descendants));
    }

    private static List<String> textOf(Component root) {
        return descendants(root).map(c -> c.getElement().getText()).toList();
    }
}
