package com.wornux.user;

import jakarta.validation.Valid;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class UserService {

    private static final String ADMINISTRATOR = "ROLE_SYSTEM_ADMINISTRATOR";

    private final AppUserRepository appUserRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            AppUserRepository appUserRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<AppUser> search(UserFilter filter) {
        requireRead();
        UserFilter safeFilter = filter == null ? new UserFilter("", null) : filter;
        return appUserRepository.search(normalizeSearch(safeFilter.text()), safeFilter.active());
    }

    @Transactional(readOnly = true)
    public AppUser get(Long id) {
        requireRead();
        return appUserRepository.findWithRolesById(id)
                .orElseThrow(() -> new UserException("User was not found."));
    }

    @Transactional(readOnly = true)
    public List<Role> activeRoles() {
        requireRead();
        return roleRepository.findByActiveTrueOrderByNameAsc();
    }

    @Transactional
    public AppUser create(@Valid UserRequest request) {
        requireManage();
        validateCreatePassword(request);
        validateUniqueUsername(request.getUsername(), null);
        validateUniqueEmail(request.getEmail(), null);
        Set<Role> roles = requireActiveRoles(request.getRoleIds());

        AppUser user = new AppUser(
                normalizeUsername(request.getUsername()),
                normalizeEmail(request.getEmail()),
                passwordEncoder.encode(request.getPassword()));
        user.setActive(request.isActive());
        roles.forEach(user::addRole);
        return appUserRepository.save(user);
    }

    @Transactional
    public AppUser update(Long id, @Valid UserRequest request) {
        requireManage();
        AppUser user = appUserRepository.findWithRolesById(id)
                .orElseThrow(() -> new UserException("User was not found."));
        if (!Objects.equals(user.getVersion(), request.getVersion())) {
            throw new UserException("User was updated by another administrator. Refresh the form and try again.");
        }
        validateUniqueUsername(request.getUsername(), id);
        validateUniqueEmail(request.getEmail(), id);
        user.update(
                normalizeUsername(request.getUsername()),
                normalizeEmail(request.getEmail()),
                request.isActive(),
                requireActiveRoles(request.getRoleIds()));
        return appUserRepository.save(user);
    }

    @Transactional
    public void deactivate(Long id) {
        requireManage();
        AppUser user = appUserRepository.findWithRolesById(id)
                .orElseThrow(() -> new UserException("User was not found."));
        if (isCurrentUser(user)) {
            throw new UserException("You cannot deactivate your own account.");
        }
        user.deactivate();
        appUserRepository.save(user);
    }

    public boolean canManageUsers() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return hasAuthority(authentication, ADMINISTRATOR);
    }

    private void requireRead() {
        if (!canManageUsers()) {
            throw new AccessDeniedException("USER:READ permission is required.");
        }
    }

    private void requireManage() {
        if (!canManageUsers()) {
            throw new AccessDeniedException("USER:CREATE/UPDATE/DELETE/ASSIGN permission is required.");
        }
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(grantedAuthority -> authority.equals(grantedAuthority.getAuthority()));
    }

    private void validateCreatePassword(UserRequest request) {
        String password = request.getPassword() == null ? "" : request.getPassword();
        if (password.isBlank()) {
            throw new UserException("Password is required.");
        }
        if (password.length() < 8) {
            throw new UserException("Password must be at least 8 characters.");
        }
        if (!password.equals(request.getConfirmPassword())) {
            throw new UserException("Passwords do not match.");
        }
    }

    private void validateUniqueUsername(String username, Long id) {
        boolean exists = id == null
                ? appUserRepository.existsByUsernameIgnoreCase(normalizeUsername(username))
                : appUserRepository.existsByUsernameIgnoreCaseAndIdNot(normalizeUsername(username), id);
        if (exists) {
            throw new UserException("Username already exists. Please choose a different one.");
        }
    }

    private void validateUniqueEmail(String email, Long id) {
        boolean exists = id == null
                ? appUserRepository.existsByEmailIgnoreCase(normalizeEmail(email))
                : appUserRepository.existsByEmailIgnoreCaseAndIdNot(normalizeEmail(email), id);
        if (exists) {
            throw new UserException("Email already registered. Please use a different one.");
        }
    }

    private Set<Role> requireActiveRoles(Set<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            throw new UserException("At least one role must be selected.");
        }
        List<Role> roles = roleRepository.findAllById(roleIds).stream()
                .filter(Role::isActive)
                .toList();
        if (roles.size() != roleIds.size()) {
            throw new UserException("At least one role must be selected.");
        }
        return new LinkedHashSet<>(roles);
    }

    private boolean isCurrentUser(AppUser user) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return false;
        }
        String current = authentication.getName();
        return user.getUsername().equalsIgnoreCase(current) || user.getEmail().equalsIgnoreCase(current);
    }

    private String normalizeSearch(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeUsername(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeEmail(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
