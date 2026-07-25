package com.wornux.security;

import com.wornux.user.AppUser;
import com.wornux.user.AppUserRepository;
import com.wornux.user.AppUserService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepository;
    private final AppUserService appUserService;

    public CustomUserDetailsService(AppUserRepository appUserRepository, AppUserService appUserService) {
        this.appUserRepository = appUserRepository;
        this.appUserService = appUserService;
    }

    @Override
    public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
        AppUser appUser = appUserRepository
                .findByUsernameIgnoreCaseOrEmailIgnoreCase(usernameOrEmail, usernameOrEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid username/email or password."));

        return User.withUsername(appUser.getUsername())
                .password("{noop}oauth2")
                .authorities(appUserService.authorities(appUser))
                .disabled(!appUser.isActive())
                .build();
    }
}
