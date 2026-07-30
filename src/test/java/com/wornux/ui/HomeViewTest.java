package com.wornux.ui;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.wornux.catalog.DashboardService;
import com.wornux.security.authorization.AuthorizationService;
import com.wornux.security.permission.AppPermission;
import com.wornux.ui.views.HomeView;
import org.junit.jupiter.api.Test;

class HomeViewTest {

    @Test
    void constructor_withoutReportPermission_doesNotReadDashboardData() {
        var dashboardService = mock(DashboardService.class);
        var authorizationService = mock(AuthorizationService.class);

        new HomeView(dashboardService, authorizationService);

        verify(authorizationService).can(AppPermission.REPORT_VIEW);
        verifyNoInteractions(dashboardService);
    }
}
