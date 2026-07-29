package com.wornux.ui.navigation;

import static org.assertj.core.api.Assertions.assertThat;

import com.wornux.security.permission.AppPermission;
import com.wornux.ui.views.HomeView;
import com.wornux.ui.views.NoAccessView;
import com.wornux.ui.views.RolesView;
import org.junit.jupiter.api.Test;

class NavigationRegistryTest {

    @Test
    void registryIsOrderedUniqueAndProvidesRouteAuthorizationMetadata() {
        var entries = NavigationRegistry.entries();

        assertThat(entries).extracting(NavigationEntry::order).isSorted();
        assertThat(entries).extracting(NavigationEntry::target).doesNotHaveDuplicates();
        assertThat(entries).extracting(NavigationEntry::path).doesNotHaveDuplicates();
        assertThat(NavigationRegistry.findByTarget(HomeView.class))
                .get()
                .extracting(NavigationEntry::permission)
                .isEqualTo(AppPermission.REPORT_VIEW);
        assertThat(NavigationRegistry.findByTarget(RolesView.class))
                .get()
                .extracting(NavigationEntry::permission)
                .isEqualTo(AppPermission.ROLE_VIEW);
        assertThat(NavigationRegistry.findByTarget(NoAccessView.class)).isEmpty();
    }
}
