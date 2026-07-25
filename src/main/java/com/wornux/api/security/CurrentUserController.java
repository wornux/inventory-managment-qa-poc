package com.wornux.api.security;

import com.wornux.api.AbstractRestController;
import com.wornux.api.ApiResponse;
import com.wornux.api.OpenApiConfig;
import com.wornux.security.authorization.AuthorizationService;
import com.wornux.security.permission.AppPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Current user", description = "Capabilities of the authenticated application user")
@SecurityRequirement(name = OpenApiConfig.OAUTH2_SCHEME)
public class CurrentUserController extends AbstractRestController {

    private final AuthorizationService authorizationService;

    public CurrentUserController(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    @GetMapping("/api/me/permissions")
    @Operation(summary = "List current user permissions")
    ResponseEntity<ApiResponse<PermissionsResponseDto>> permissions() {
        List<String> permissions = authorizationService.effectivePermissions().stream()
                .map(AppPermission::code)
                .sorted()
                .toList();

        return ok("Permissions retrieved.", new PermissionsResponseDto(permissions));
    }

    public record PermissionsResponseDto(List<String> permissions) {}
}
