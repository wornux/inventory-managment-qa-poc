package com.wornux.user;

import jakarta.validation.Valid;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class AppUserService {

    public static final String DEFAULT_ROLE_CODE = "INVENTORY_VIEWER";

    private final AppUserRepository appUserRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public AppUserService(
            AppUserRepository appUserRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AppUser signup(@Valid SignupRequest request) {
        String username = trim(request.getUsername());
        String email = trim(request.getEmail()).toLowerCase(Locale.ROOT);

        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new SignupException("Passwords do not match.");
        }
        if (appUserRepository.existsByUsernameIgnoreCase(username)) {
            throw new SignupException("Username already taken.");
        }
        if (appUserRepository.existsByEmailIgnoreCase(email)) {
            throw new SignupException("Email already registered.");
        }

        Role defaultRole = roleRepository.findByCode(DEFAULT_ROLE_CODE)
                .orElseThrow(() -> new IllegalStateException("Default role INVENTORY_VIEWER is not configured."));

        AppUser user = new AppUser(username, email, passwordEncoder.encode(request.getPassword()));
        user.addRole(defaultRole);
        return appUserRepository.save(user);
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
