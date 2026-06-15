package com.wornux.usecases.uc001_user_signup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wornux.user.AppUserRepository;
import com.wornux.user.AppUserService;
import com.wornux.user.SignupException;
import com.wornux.user.SignupRequest;
import com.wornux.usecases.PostgresContainerConfig;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(PostgresContainerConfig.class)
@Transactional
class UC001UserSignupTest {

    @Autowired
    private AppUserService appUserService;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void mainFlow_createsUserWithHashedPasswordAndDefaultViewerRole() {
        appUserService.signup(request("newuser", "newuser@example.com", "validPass123", "validPass123"));

        var user = appUserRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase("newuser", "newuser")
                .orElseThrow();

        assertThat(user.getEmail()).isEqualTo("newuser@example.com");
        assertThat(user.getPasswordHash()).isNotEqualTo("validPass123");
        assertThat(passwordEncoder.matches("validPass123", user.getPasswordHash())).isTrue();
        assertThat(user.getRoles()).extracting("code").containsExactly("INVENTORY_VIEWER");
    }

    @Test
    void af1_duplicateUsernameShowsUsernameTaken() {
        appUserService.signup(request("sameuser", "one@example.com", "validPass123", "validPass123"));

        assertThatThrownBy(() -> appUserService.signup(
                request("sameuser", "two@example.com", "validPass123", "validPass123")))
                .isInstanceOf(SignupException.class)
                .hasMessage("Username already taken.");
    }

    @Test
    void af2_duplicateEmailShowsEmailRegistered() {
        appUserService.signup(request("userone", "same@example.com", "validPass123", "validPass123"));

        assertThatThrownBy(() -> appUserService.signup(
                request("usertwo", "same@example.com", "validPass123", "validPass123")))
                .isInstanceOf(SignupException.class)
                .hasMessage("Email already registered.");
    }

    @Test
    void af3_invalidEmailFormatIsRejected() {
        assertThatThrownBy(() -> appUserService.signup(
                request("bademail", "not-an-email", "validPass123", "validPass123")))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("Invalid email address.");
    }

    @Test
    void af4_weakPasswordIsRejected() {
        assertThatThrownBy(() -> appUserService.signup(request("weak", "weak@example.com", "short", "short")))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("Password must be at least 8 characters.");
    }

    @Test
    void af5_passwordMismatchIsRejected() {
        assertThatThrownBy(() -> appUserService.signup(
                request("mismatch", "mismatch@example.com", "validPass123", "validPass124")))
                .isInstanceOf(SignupException.class)
                .hasMessage("Passwords do not match.");
    }

    @Test
    void af6_missingRequiredFieldsAreRejected() {
        assertThatThrownBy(() -> appUserService.signup(request("", "", "", "")))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("Username is required.")
                .hasMessageContaining("Email is required.")
                .hasMessageContaining("Password is required.");
    }

    @Test
    void br01_usernameMustBeUniqueAndNotBlank() {
        af1_duplicateUsernameShowsUsernameTaken();
        assertThatThrownBy(() -> appUserService.signup(
                request(" ", "blank-user@example.com", "validPass123", "validPass123")))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void br02_emailMustBeUniqueNotBlankAndValid() {
        af2_duplicateEmailShowsEmailRegistered();
        af3_invalidEmailFormatIsRejected();
    }

    @Test
    void br03_passwordMustBeAtLeast8Characters() {
        af4_weakPasswordIsRejected();
    }

    @Test
    void br04_passwordAndConfirmationMustMatchExactly() {
        af5_passwordMismatchIsRejected();
    }

    @Test
    void br05_passwordsAreStoredAsBcryptHashes() {
        appUserService.signup(request("hashcheck", "hash@example.com", "validPass123", "validPass123"));

        var user = appUserRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase("hashcheck", "hashcheck")
                .orElseThrow();

        assertThat(user.getPasswordHash()).startsWith("$2");
        assertThat(passwordEncoder.matches("validPass123", user.getPasswordHash())).isTrue();
    }

    @Test
    void br06_newUsersAreAssignedInventoryViewerRole() {
        appUserService.signup(request("viewer", "viewer@example.com", "validPass123", "validPass123"));

        var user = appUserRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase("viewer", "viewer")
                .orElseThrow();

        assertThat(user.getRoles()).extracting("code").containsExactly("INVENTORY_VIEWER");
    }

    private SignupRequest request(String username, String email, String password, String confirmPassword) {
        var request = new SignupRequest();
        request.setUsername(username);
        request.setEmail(email);
        request.setPassword(password);
        request.setConfirmPassword(confirmPassword);
        return request;
    }
}
