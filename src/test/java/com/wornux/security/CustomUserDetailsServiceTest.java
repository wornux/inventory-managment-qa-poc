package com.wornux.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.wornux.user.AppUser;
import com.wornux.user.AppUserRepository;
import com.wornux.user.AppUserService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {
    @Mock AppUserRepository repository;
    @Mock AppUserService appUserService;

    @Test
    void loadsByUsernameOrEmailWithApplicationAuthoritiesAndStatus() {
        AppUser user = new AppUser("Alice", "alice@example.com", "issuer", "subject");
        when(repository.findByUsernameIgnoreCaseOrEmailIgnoreCase("alice@example.com", "alice@example.com"))
                .thenReturn(Optional.of(user));
        when(appUserService.authorities(user)).thenReturn(List.of(new SimpleGrantedAuthority("product:view")));

        var details = new CustomUserDetailsService(repository, appUserService).loadUserByUsername("alice@example.com");
        assertThat(details.getUsername()).isEqualTo("Alice");
        assertThat(details.getPassword()).isEqualTo("{noop}oauth2");
        assertThat(details.isEnabled()).isTrue();
        assertThat(details.getAuthorities()).extracting("authority").containsExactly("product:view");
    }

    @Test
    void missingUserIsReportedWithoutLeakingWhichIdentifierFailed() {
        when(repository.findByUsernameIgnoreCaseOrEmailIgnoreCase("nobody", "nobody")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> new CustomUserDetailsService(repository, appUserService).loadUserByUsername("nobody"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("Invalid username/email or password.");
    }

    @Test
    void inactiveUserIsDisabled() {
        AppUser user = new AppUser("Alice", "alice@example.com", "issuer", "subject");
        user.deactivate();
        when(repository.findByUsernameIgnoreCaseOrEmailIgnoreCase("Alice", "Alice")).thenReturn(Optional.of(user));
        when(appUserService.authorities(user)).thenReturn(List.of());
        assertThat(new CustomUserDetailsService(repository, appUserService).loadUserByUsername("Alice").isEnabled()).isFalse();
    }
}
