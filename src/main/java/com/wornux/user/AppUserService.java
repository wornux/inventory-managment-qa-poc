package com.wornux.user;

import com.wornux.security.permission.AppPermission;
import java.util.List;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppUserService {

    public static final String DEFAULT_ROLE_CODE = "INVENTORY_VIEWER";
    public static final String SYSTEM_ADMINISTRATOR_ROLE_CODE = "SYSTEM_ADMINISTRATOR";

    private final AppUserRepository appUserRepository;
    private final RoleRepository roleRepository;

    public AppUserService(AppUserRepository appUserRepository, RoleRepository roleRepository) {
        this.appUserRepository = appUserRepository;
        this.roleRepository = roleRepository;
    }

    @Transactional
    public AppUser provisionOidcUser(OidcUserProfile profile) {
        return provision(profile, DEFAULT_ROLE_CODE);
    }

    @Transactional
    public AppUser provisionSystemAdministrator(OidcUserProfile profile) {
        OidcUserProfile normalized = profile.normalized();
        AppUser user = appUserRepository
                .findByOidcIssuerAndOidcSubject(normalized.issuer(), normalized.subject())
                .or(() -> appUserRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(
                        normalized.username(), normalized.email()))
                .orElseGet(() -> createUser(normalized, SYSTEM_ADMINISTRATOR_ROLE_CODE));

        validateUniqueUsername(normalized.username(), user.getId());
        validateUniqueEmail(normalized.email(), user.getId());
        user.updateIdentity(normalized.username(), normalized.email(), normalized.issuer(), normalized.subject());
        user.setActive(true);
        Role administrator = requireRole(SYSTEM_ADMINISTRATOR_ROLE_CODE);

        if (user.getRoles().stream().noneMatch(role -> SYSTEM_ADMINISTRATOR_ROLE_CODE.equals(role.getCode()))) {
            user.addRole(administrator);
        }

        return appUserRepository.save(user);
    }

    @Transactional(readOnly = true)
    public List<GrantedAuthority> authorities(AppUser user) {
        return user.getRoles().stream()
                .filter(Role::isActive)
                .flatMap(role -> role.getPermissions().stream())
                .map(AppPermission::code)
                .distinct()
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
    }

    private AppUser provision(OidcUserProfile profile, String roleCodeForNewUser) {
        OidcUserProfile normalized = profile.normalized();
        AppUser user = appUserRepository
                .findByOidcIssuerAndOidcSubject(normalized.issuer(), normalized.subject())
                .orElseGet(() -> createUser(normalized, roleCodeForNewUser));

        if (!user.isActive()) {
            throw new DisabledException("Account is inactive.");
        }

        validateUniqueUsername(normalized.username(), user.getId());
        validateUniqueEmail(normalized.email(), user.getId());
        user.updateIdentity(normalized.username(), normalized.email(), normalized.issuer(), normalized.subject());

        return appUserRepository.save(user);
    }

    private AppUser createUser(OidcUserProfile profile, String roleCode) {
        validateUniqueUsername(profile.username(), null);
        validateUniqueEmail(profile.email(), null);
        AppUser user = new AppUser(profile.username(), profile.email(), profile.issuer(), profile.subject());
        user.addRole(requireRole(roleCode));

        return user;
    }

    private Role requireRole(String code) {
        return roleRepository
                .findByCode(code)
                .filter(Role::isActive)
                .orElseThrow(() -> new IllegalStateException("Role " + code + " is not configured."));
    }

    private void validateUniqueUsername(String username, Long id) {
        boolean exists = id == null
                ? appUserRepository.existsByUsernameIgnoreCase(username)
                : appUserRepository.existsByUsernameIgnoreCaseAndIdNot(username, id);

        if (exists) {
            throw new OidcProvisioningException("Username already exists for another local user.");
        }
    }

    private void validateUniqueEmail(String email, Long id) {
        boolean exists = id == null
                ? appUserRepository.existsByEmailIgnoreCase(email)
                : appUserRepository.existsByEmailIgnoreCaseAndIdNot(email, id);

        if (exists) {
            throw new OidcProvisioningException("Email already exists for another local user.");
        }
    }
}
