package com.wornux.usecases.uc002_user_login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wornux.user.AppUserRepository;
import com.wornux.user.AppUserService;
import com.wornux.user.SignupRequest;
import com.wornux.usecases.PostgresContainerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Import(PostgresContainerConfig.class)
class UC002UserLoginTest {

    private MockMvc mockMvc;

    private final WebApplicationContext context;
    private final AppUserService appUserService;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    UC002UserLoginTest(
            WebApplicationContext context,
            AppUserService appUserService,
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder) {
        this.context = context;
        this.appUserService = appUserService;
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @BeforeEach
    void cleanDatabase() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        appUserRepository.deleteAll();
    }

    @Test
    void mainFlow_authenticatesExistingActiveUserAndRedirectsHome() throws Exception {
        signup("loginuser", "loginuser@example.com", "validPass123");

        mockMvc.perform(formLogin("/login").user("loginuser").password("validPass123"))
                .andExpect(authenticated().withUsername("loginuser"))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void af1_invalidCredentialsShowGenericError() throws Exception {
        signup("knownuser", "known@example.com", "validPass123");

        mockMvc.perform(formLogin("/login").user("knownuser").password("wrongPass123"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void af2_inactiveAccountShowsInactiveMessage() throws Exception {
        signup("inactiveuser", "inactive@example.com", "validPass123");
        var user = appUserRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase("inactiveuser", "inactiveuser")
                .orElseThrow();
        user.setActive(false);
        appUserRepository.saveAndFlush(user);

        mockMvc.perform(formLogin("/login").user("inactiveuser").password("validPass123"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?inactive"));
    }

    @Test
    void af3_missingRequiredFieldsStayOnLoginFormWithRequiredInputs() throws Exception {
        mockMvc.perform(formLogin("/login").user("").password(""))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void af4_sessionTimeoutRequiresLoginAgain() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void br01_loginAcceptsEmailAddress() throws Exception {
        signup("emailuser", "emailuser@example.com", "validPass123");

        mockMvc.perform(formLogin("/login").user("emailuser@example.com").password("validPass123"))
                .andExpect(authenticated().withUsername("emailuser"))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void br02_passwordValidationUsesBcryptHashing() throws Exception {
        signup("bcryptuser", "bcrypt@example.com", "validPass123");
        var user = appUserRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase("bcryptuser", "bcryptuser")
                .orElseThrow();

        assertThat(user.getPasswordHash()).startsWith("$2");
        assertThat(passwordEncoder.matches("validPass123", user.getPasswordHash())).isTrue();

        mockMvc.perform(formLogin("/login").user("bcryptuser").password("validPass123"))
                .andExpect(authenticated().withUsername("bcryptuser"));
    }

    @Test
    void br03_inactiveUserAccountsAreRejected() throws Exception {
        af2_inactiveAccountShowsInactiveMessage();
    }

    @Test
    void br04_genericInvalidCredentialErrorDoesNotRevealWhichFieldFailed() throws Exception {
        mockMvc.perform(formLogin("/login").user("missing-user").password("validPass123"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));

        mockMvc.perform(formLogin("/login").user("missing-user").password("wrongPass123"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void br05_successfulLoginCreatesAuthenticatedSession() throws Exception {
        signup("sessionuser", "session@example.com", "validPass123");

        mockMvc.perform(formLogin("/login").user("sessionuser").password("validPass123"))
                .andExpect(authenticated().withUsername("sessionuser"));
    }

    @Test
    void br06_logoutInvalidatesTheSession() throws Exception {
        signup("logoutuser", "logout@example.com", "validPass123");
        var login = mockMvc.perform(formLogin("/login").user("logoutuser").password("validPass123"))
                .andExpect(authenticated().withUsername("logoutuser"))
                .andReturn();

        mockMvc.perform(post("/logout")
                        .session((org.springframework.mock.web.MockHttpSession) login.getRequest().getSession(false))
                        .with(csrf()))
                .andExpect(unauthenticated());
    }

    private void signup(String username, String email, String password) {
        var request = new SignupRequest();
        request.setUsername(username);
        request.setEmail(email);
        request.setPassword(password);
        request.setConfirmPassword(password);
        appUserService.signup(request);
    }
}
