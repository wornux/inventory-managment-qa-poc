package com.wornux.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.wornux.security.authorization.AuthorizationService;
import com.wornux.security.permission.AppPermission;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CurrentUserControllerTest {

    @Mock
    private AuthorizationService authorizationService;

    @Test
    void returnsStableSortedPermissionCodes() {
        when(authorizationService.effectivePermissions())
                .thenReturn(Set.of(AppPermission.PRODUCT_UPDATE, AppPermission.PRODUCT_VIEW));

        var response = new CurrentUserController(authorizationService).permissions().getBody();

        assertThat(response.data().permissions()).containsExactly("product:update", "product:view");
    }
}
