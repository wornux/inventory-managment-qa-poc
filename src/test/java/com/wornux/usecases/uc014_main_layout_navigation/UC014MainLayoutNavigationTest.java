package com.wornux.usecases.uc014_main_layout_navigation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Main;
import com.vaadin.flow.router.Layout;
import com.vaadin.flow.spring.security.AuthenticationContext;
import com.wornux.ui.MainLayout;
import com.wornux.ui.security.UiAccessService;
import com.wornux.ui.views.CategoriesView;
import com.wornux.ui.views.ForbiddenView;
import com.wornux.ui.views.HomeView;
import com.wornux.ui.views.LoginView;
import com.wornux.ui.views.PermissionsView;
import com.wornux.ui.views.ProductsView;
import com.wornux.ui.views.RolesView;
import com.wornux.ui.views.SignupView;
import com.wornux.ui.views.StockMovementsView;
import com.wornux.ui.views.SuppliersView;
import com.wornux.ui.views.UsersView;
import com.wornux.usecases.PostgresContainerConfig;
import jakarta.annotation.security.PermitAll;
import java.lang.reflect.Field;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;

@SpringBootTest
@Import(PostgresContainerConfig.class)
class UC014MainLayoutNavigationTest {

    private final UiAccessService accessService;

    @Autowired
    UC014MainLayoutNavigationTest(UiAccessService accessService) {
        this.accessService = accessService;
    }

    @Test
    @WithMockUser(username = "admin", roles = "SYSTEM_ADMINISTRATOR")
    void mainFlow_protectedViewsUseMainLayoutWithPermissionAwareDrawerAndPolishedHome() {
        MainLayout layout = new MainLayout(authenticationContext("admin", "SYSTEM_ADMINISTRATOR"), accessService);
        HomeView home = new HomeView();

        assertThat(MainLayout.class).isAssignableTo(AppLayout.class);
        assertThat(MainLayout.class).hasAnnotation(Layout.class);
        assertThat(MainLayout.class).hasAnnotation(PermitAll.class);
        assertThat(home).isInstanceOf(Main.class);
        assertThat(home.getChildren()).noneMatch(Anchor.class::isInstance);
        assertThat(componentText(layout))
                .contains("Wornux")
                .contains("Overview")
                .contains("Products")
                .contains("Categories")
                .contains("Suppliers")
                .contains("Stock Movements")
                .contains("Users")
                .contains("Roles")
                .contains("Permissions");
        assertThat(componentText(home))
                .contains("Inventory workspace")
                .contains("Pending")
                .contains("Dashboard metrics are pending");
    }

    @Test
    @WithAnonymousUser
    void af1_unauthenticatedUsersHaveNoDrawerPermissions() {
        assertThat(accessService.canRead("PRODUCT")).isFalse();
        assertThat(accessService.canRead("USER")).isFalse();
    }

    @Test
    @WithMockUser(username = "viewer", roles = "INVENTORY_VIEWER")
    void af2AndAf3_userWithoutAdminPermissionCannotSeeOrOpenAdminResources() throws Exception {
        MainLayout layout = new MainLayout(authenticationContext("viewer", "INVENTORY_VIEWER"), accessService);

        assertThat(accessService.canRead("PRODUCT")).isTrue();
        assertThat(accessService.canRead("USER")).isFalse();
        assertThat(routeResources()).containsEntry(UsersView.class, "USER");
        assertThat(componentText(layout))
                .contains("Products")
                .doesNotContain("Users")
                .doesNotContain("Roles")
                .doesNotContain("Permissions");
    }

    @Test
    @WithMockUser(username = "limited")
    void af4_authenticatedUserWithNoBusinessPermissionsSeesLimitedNavigation() {
        MainLayout layout = new MainLayout(authenticationContext("limited"), accessService);

        assertThat(accessService.hasAnyBusinessModule()).isFalse();
        assertThat(componentText(layout))
                .contains("Overview")
                .contains("No modules available")
                .doesNotContain("Products")
                .doesNotContain("Users");
    }

    @Test
    void af5_mainLayoutUsesAppLayoutDrawerForResponsiveNavigation() {
        assertThat(MainLayout.class).isAssignableTo(AppLayout.class);
    }

    @Test
    void br01_mainLayoutExtendsAppLayout() {
        assertThat(MainLayout.class.getSuperclass()).isEqualTo(AppLayout.class);
    }

    @Test
    void br02_publicAuthViewsOptOutOfMainLayout() {
        assertThat(LoginView.class.getAnnotation(com.vaadin.flow.router.Route.class).autoLayout()).isFalse();
        assertThat(SignupView.class.getAnnotation(com.vaadin.flow.router.Route.class).autoLayout()).isFalse();
    }

    @Test
    @WithMockUser(username = "manager", roles = "INVENTORY_MANAGER")
    void br04_navigationPermissionsFollowReadResourceRules() {
        assertThat(accessService.canRead("PRODUCT")).isTrue();
        assertThat(accessService.canRead("CATEGORY")).isTrue();
        assertThat(accessService.canRead("SUPPLIER")).isTrue();
        assertThat(accessService.canRead("STOCK_MOVEMENT")).isTrue();
        assertThat(accessService.canRead("USER")).isFalse();
        assertThat(accessService.canRead("ROLE")).isFalse();
        assertThat(accessService.canRead("PERMISSION")).isFalse();
    }

    @Test
    void br05_directRouteAccessHasResourceMapping() throws Exception {
        assertThat(routeResources())
                .containsEntry(ProductsView.class, "PRODUCT")
                .containsEntry(CategoriesView.class, "CATEGORY")
                .containsEntry(SuppliersView.class, "SUPPLIER")
                .containsEntry(StockMovementsView.class, "STOCK_MOVEMENT")
                .containsEntry(UsersView.class, "USER")
                .containsEntry(RolesView.class, "ROLE")
                .containsEntry(PermissionsView.class, "PERMISSION");
    }

    @Test
    void br06_forbiddenAccessHasUserFacingRoute() {
        assertThat(ForbiddenView.class.getAnnotation(com.vaadin.flow.router.Route.class).value()).isEqualTo("forbidden");
        assertThat(componentText(new ForbiddenView())).contains("Access forbidden");
    }

    @Test
    void br07_homeViewIsNotRawAnchorList() {
        HomeView home = new HomeView();

        assertThat(home.getChildren()).noneMatch(Anchor.class::isInstance);
        assertThat(componentText(home)).contains("Inventory workspace", "Pending");
    }

    @Test
    void br08_stylesUseAuraTokensInsteadOfLumoTokens() throws Exception {
        String styles = java.nio.file.Files.readString(
                java.nio.file.Path.of("src/main/resources/META-INF/resources/styles.css"));

        assertThat(styles).contains("--aura-accent-color-light");
        assertThat(styles).doesNotContain("--lumo-");
    }

    @SuppressWarnings("unchecked")
    private static java.util.Map<Class<? extends Component>, String> routeResources() throws Exception {
        Field field = MainLayout.class.getDeclaredField("ROUTE_RESOURCES");
        field.setAccessible(true);
        return (java.util.Map<Class<? extends Component>, String>) field.get(null);
    }

    private static AuthenticationContext authenticationContext(String username, String... roles) {
        AuthenticationContext context = mock(AuthenticationContext.class);
        UserDetails user = User.withUsername(username).password("password").roles(roles).build();
        when(context.getAuthenticatedUser(UserDetails.class)).thenReturn(Optional.of(user));
        return context;
    }

    private static String componentText(Component component) {
        return component.getElement().getTextRecursively();
    }
}
