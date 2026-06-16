package com.wornux.security;

import com.wornux.user.AppUser;
import com.wornux.user.AppUserRepository;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepository;

    public CustomUserDetailsService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
        AppUser appUser = appUserRepository
                .findByUsernameIgnoreCaseOrEmailIgnoreCase(usernameOrEmail, usernameOrEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid username/email or password."));

        List<GrantedAuthority> authorities = appUser.getRoles().stream()
                .filter(role -> role.isActive() && role.getCode() != null)
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getCode()))
                .map(GrantedAuthority.class::cast)
                .toList();

        return User.withUsername(appUser.getUsername())
                .password("{noop}oauth2")
                .authorities(authorities)
                .disabled(!appUser.isActive())
                .build();
    }
}
