package com.wornux.security;

import com.wornux.user.AppUser;
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

    public AppJwtAuthenticationConverter(AppUserService appUserService) {
        this.appUserService = appUserService;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        AppUser appUser = appUserService.provisionOidcUser(new OidcUserProfile(
                jwt.getIssuer() == null ? null : jwt.getIssuer().toString(),
                jwt.getSubject(),
                jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("email")));
        return new JwtAuthenticationToken(jwt, appUserService.authorities(appUser), appUser.getUsername());
    }
}
