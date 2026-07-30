package com.wornux.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.SvgIcon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Layout;
import com.vaadin.flow.spring.security.AuthenticationContext;
import com.wornux.security.authorization.AuthorizationService;
import com.wornux.ui.components.DrawerRailToggle;
import com.wornux.ui.navigation.NavigationEntry;
import com.wornux.ui.navigation.NavigationRegistry;
import com.wornux.ui.views.ForbiddenView;
import com.wornux.ui.views.NoAccessView;
import jakarta.annotation.security.PermitAll;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

@Layout
@PermitAll
public class MainLayout extends AppLayout implements BeforeEnterObserver, HasSize {

    private final transient AuthenticationContext authenticationContext;
    private final AuthorizationService authorizationService;

    public MainLayout(AuthenticationContext authenticationContext, AuthorizationService authorizationService) {
        this.authenticationContext = authenticationContext;
        this.authorizationService = authorizationService;

        setId("main-layout");
        addClassName("main-layout");
        setHeightFull();
        setPrimarySection(Section.DRAWER);
        addToNavbar(createMobileDrawerToggle("main-layout-toggle", "Open navigation"));
        addToDrawer(createDrawerHeader(), new Scroller(createNavigation()), createProfileDrawer());
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        Class<? extends Component> target = event.getNavigationTarget();
        if (target == NoAccessView.class || target == ForbiddenView.class) {
            return;
        }

        if (authorizationService.effectivePermissions().isEmpty()) {
            event.rerouteTo(NoAccessView.class);
            return;
        }

        NavigationRegistry.findByTarget(target)
                .ifPresentOrElse(
                        entry -> {
                            if (!authorizationService.can(entry.permission())) {
                                event.rerouteTo(ForbiddenView.class);
                            }
                        },
                        () -> event.rerouteTo(ForbiddenView.class));
    }

    private Component createDrawerHeader() {
        var identity = getIdentity();

        var railToggle = new DrawerRailToggle();
        railToggle.addClassName("main-layout-rail-toggle");

        var mobileToggle = createMobileDrawerToggle("main-layout-drawer-close", "Close navigation");
        var controls = new Div(railToggle, mobileToggle);
        controls.addClassName("main-layout-brand-controls");

        var header = new HorizontalLayout(identity, controls);
        header.addClassName("main-layout-brand");
        header.setAlignItems(FlexComponent.Alignment.CENTER);

        return header;
    }

    private static @NonNull HorizontalLayout getIdentity() {
        var mark = new SvgIcon("/icons/app.svg");
        mark.addClassName("main-layout-brand-mark");

        var name = new Span("Wornux");
        name.addClassName("main-layout-brand-name");

        var label = new Span("Inventory Ops");
        label.addClassName("main-layout-brand-label");

        var copy = new VerticalLayout(name, label);
        copy.addClassName("main-layout-brand-copy");
        copy.setPadding(false);
        copy.setSpacing(false);

        var identity = new HorizontalLayout(mark, copy);
        identity.addClassName("main-layout-brand-identity");
        identity.setAlignItems(FlexComponent.Alignment.CENTER);
        return identity;
    }

    private DrawerToggle createMobileDrawerToggle(String className, String ariaLabel) {
        var toggle = new DrawerToggle();
        toggle.setId(className);
        toggle.addThemeVariants(ButtonVariant.TERTIARY);
        toggle.addClassName(className);
        toggle.setAriaLabel(ariaLabel);

        return toggle;
    }

    private Component createNavigation() {
        var wrapper = new VerticalLayout();
        wrapper.addClassNames("main-layout-nav", "main-layout-icon-carousel");
        wrapper.setPadding(false);
        wrapper.setSpacing(false);

        Map<String, SideNav> sections = new LinkedHashMap<>();
        NavigationRegistry.entries().stream()
                .filter(entry -> authorizationService.can(entry.permission()))
                .forEach(entry ->
                        sections.computeIfAbsent(entry.section(), this::section).addItem(navItem(entry)));

        if (sections.isEmpty()) {
            var empty = new Span("No modules available");
            empty.addClassName("main-layout-empty-nav");
            wrapper.add(empty);
        } else {
            sections.values().forEach(wrapper::add);
        }

        return wrapper;
    }

    private SideNav section(String label) {
        var nav = label == null ? new SideNav() : new SideNav(label);
        if (label != null) {
            nav.addClassName("main-layout-nav-section");
        }

        return nav;
    }

    private SideNavItem navItem(NavigationEntry entry) {
        var item = new SideNavItem(entry.label(), entry.path());
        String id = entry.section() == null
                ? "nav-overview"
                : "nav-" + entry.label().toLowerCase().replace(' ', '-');
        item.setId(id);
        item.setPrefixComponent(svgIcon(entry.iconPath()));
        item.setMatchNested(true);

        return item;
    }

    private SvgIcon svgIcon(String path) {
        var icon = new SvgIcon(path);
        icon.addClassName("main-layout-custom-icon");

        return icon;
    }

    private Component createProfileDrawer() {
        var profile = currentUserProfile();

        var summary = getSummary(profile);

        var logout = new Button("Sign out", VaadinIcon.SIGN_OUT.create(), event -> authenticationContext.logout());
        logout.setId("sign-out");
        logout.addThemeVariants(ButtonVariant.TERTIARY);
        logout.addClassName("profile-drawer-card__logout");

        var details = new Details(summary, logout);
        details.setId("profile-drawer");
        details.addClassName("profile-drawer-card");

        return details;
    }

    private static @NonNull Div getSummary(UserProfile profile) {
        var avatar = new Avatar(profile.name());
        avatar.addClassName("profile-drawer-card__avatar");

        var name = new Span(profile.name());
        name.addClassName("profile-drawer-card__name");

        var identity = new Div(name);
        identity.addClassName("profile-drawer-card__identity");
        if (!profile.email().isBlank()) {
            var email = new Span(profile.email());
            email.addClassName("profile-drawer-card__email");
            identity.add(email);
        }

        var chevron = new SvgIcon("/icons/chevron.svg");
        chevron.addClassName("profile-drawer-card__chevron");

        var summary = new Div(avatar, identity, chevron);
        summary.addClassName("profile-drawer-card__summary");
        return summary;
    }

    private UserProfile currentUserProfile() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null) {
            return new UserProfile("User", "");
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof OidcUser oidcUser) {
            String username =
                    firstNonBlank(oidcUser.getClaimAsString("preferred_username"), oidcUser.getName(), "User");
            String displayName = firstNonBlank(oidcUser.getClaimAsString("name"), username);

            return new UserProfile(displayName, firstNonBlank(oidcUser.getEmail(), ""));
        }

        if (principal instanceof UserDetails userDetails) {
            return new UserProfile(firstNonBlank(userDetails.getUsername(), "User"), "");
        }

        return new UserProfile(firstNonBlank(authentication.getName(), "User"), "");
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }

        return "";
    }

    private record UserProfile(String name, String email) {}
}
