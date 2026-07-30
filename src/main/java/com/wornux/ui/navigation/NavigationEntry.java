package com.wornux.ui.navigation;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.router.Route;
import com.wornux.security.permission.AppPermission;
import java.util.Objects;

public record NavigationEntry(
        String section, String label, Class<? extends Component> target, String iconPath, AppPermission permission) {

    public NavigationEntry {
        label = Objects.requireNonNull(label, "label");
        target = Objects.requireNonNull(target, "target");
        iconPath = Objects.requireNonNull(iconPath, "iconPath");
        permission = Objects.requireNonNull(permission, "permission");
    }

    public String path() {
        return Objects.requireNonNull(target.getAnnotation(Route.class), "target route")
                .value();
    }
}
