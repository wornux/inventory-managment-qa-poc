package com.wornux.specdriven.usecases.uc014_main_layout_navigation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.vaadin.flow.dom.Element;
import com.vaadin.flow.spring.security.AuthenticationContext;
import com.wornux.security.permission.AppPermission;
import com.wornux.ui.MainLayout;
import com.wornux.ui.security.UiAccessService;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UC014MainLayoutNavigation {

    @Mock
    private AuthenticationContext authenticationContext;

    @Mock
    private UiAccessService accessService;

    @Test
    void mainFlow_rendersLitRailToggleAndAllCustomNavigationIcons() {
        when(accessService.canRead(any(AppPermission.class))).thenReturn(true);

        var layout = new MainLayout(authenticationContext, accessService);
        List<Element> descendants = descendants(layout.getElement())
                .filter(element -> !element.isTextNode())
                .toList();

        assertThat(descendants).extracting(Element::getTag).contains("drawer-rail-toggle");
        assertThat(descendants.stream()
                .filter(element -> element.getClassList().contains("main-layout-brand-mark"))
                .map(element -> element.getAttribute("src")))
                .containsExactly("/icons/app.svg");
        assertThat(descendants.stream()
                .filter(element -> element.getClassList().contains("main-layout-custom-icon"))
                .map(element -> element.getAttribute("src")))
                .containsExactlyInAnyOrder(
                        "/icons/overview.svg",
                        "/icons/package.svg",
                        "/icons/categories.svg",
                        "/icons/suppliers.svg",
                        "/icons/stock-movement.svg",
                        "/icons/users.svg",
                        "/icons/roles.svg");
    }

    private Stream<Element> descendants(Element parent) {
        return parent.getChildren()
                .flatMap(child -> Stream.concat(Stream.of(child), descendants(child)));
    }
}
