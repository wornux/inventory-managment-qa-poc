package com.wornux.ui;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.wornux.catalog.DashboardService;
import com.wornux.security.permission.AppPermission;
import com.wornux.ui.security.UiAccessService;
import com.wornux.ui.views.HomeView;
import org.junit.jupiter.api.Test;

class HomeViewTest {

    @Test
    void constructor_withoutReportPermission_doesNotReadDashboardData() {
        var dashboardService = mock(DashboardService.class);
        var accessService = mock(UiAccessService.class);

        new HomeView(dashboardService, accessService);

        verify(accessService).canRead(AppPermission.REPORT_VIEW);
        verifyNoInteractions(dashboardService);
    }
}
