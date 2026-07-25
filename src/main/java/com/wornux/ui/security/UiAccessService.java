package com.wornux.ui.security;

import com.wornux.security.authorization.AuthorizationService;
import com.wornux.security.permission.AppPermission;
import org.springframework.stereotype.Service;

@Service
public class UiAccessService {

    private final AuthorizationService authorizationService;

    public UiAccessService(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    public boolean canRead(AppPermission permission) {
        return authorizationService.can(permission);
    }
}
