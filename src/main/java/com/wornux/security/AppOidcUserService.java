package com.wornux.security;

import com.wornux.user.AppUser;
import com.wornux.user.AppUserService;
import com.wornux.user.OidcProvisioningException;
import com.wornux.user.OidcUserProfile;
import java.util.LinkedHashSet;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

@Service
public class AppOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private static final String USERNAME_CLAIM = "preferred_username";

    private final OidcUserService delegate = new OidcUserService();
    private final AppUserService appUserService;

    public AppOidcUserService(AppUserService appUserService) {
        this.appUserService = appUserService;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = delegate.loadUser(userRequest);
        try {
            AppUser appUser = appUserService.provisionOidcUser(profile(oidcUser));
            var authorities = new LinkedHashSet<GrantedAuthority>(oidcUser.getAuthorities());
            authorities.addAll(appUserService.authorities(appUser));
            return new DefaultOidcUser(authorities, oidcUser.getIdToken(), oidcUser.getUserInfo(), USERNAME_CLAIM);
        } catch (AuthenticationException | OidcProvisioningException exception) {
            throw oauthFailure(exception.getMessage(), exception);
        }
    }

    private OidcUserProfile profile(OidcUser oidcUser) {
        return new OidcUserProfile(
                oidcUser.getIssuer() == null ? null : oidcUser.getIssuer().toString(),
                oidcUser.getSubject(),
                oidcUser.getClaimAsString(USERNAME_CLAIM),
                oidcUser.getEmail());
    }

    private OAuth2AuthenticationException oauthFailure(String message, RuntimeException cause) {
        return new OAuth2AuthenticationException(
                new OAuth2Error("oidc_provisioning_failed", message, null),
                message,
                cause);
    }
}
