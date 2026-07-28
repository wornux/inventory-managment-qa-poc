package com.wornux.security;

import com.wornux.security.authorization.AuthorizationService;
import com.wornux.security.authorization.UserAccessSnapshot;
import com.wornux.user.AppUserService;
import com.wornux.user.OidcUserProfile;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class AppJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final AppUserService appUserService;
    private final AuthorizationService authorizationService;

    public AppJwtAuthenticationConverter(AppUserService appUserService, AuthorizationService authorizationService) {
        this.appUserService = appUserService;
        this.authorizationService = authorizationService;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        var profile = new OidcUserProfile(
                jwt.getIssuer() == null ? null : jwt.getIssuer().toString(),
                jwt.getSubject(),
                jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("email"));
        UserAccessSnapshot access = authorizationService
                .cached(profile.username())
                .orElseGet(() -> authorizationService.cache(appUserService.provisionOidcUser(profile)));

        return new JwtAuthenticationToken(jwt, access.authorities(), access.username());
    }
}
