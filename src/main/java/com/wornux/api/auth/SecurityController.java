package com.wornux.api.auth;

import com.wornux.api.AbstractRestController;
import com.wornux.api.ApiResponse;
import com.wornux.api.security.JwtService;
import com.wornux.user.AppUser;
import com.wornux.user.AppUserService;
import com.wornux.user.SignupRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "JWT authentication and account registration")
public class SecurityController extends AbstractRestController {

    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AppUserService appUserService;

    public SecurityController(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AppUserService appUserService) {
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.appUserService = appUserService;
    }

    @PostMapping("/login")
    @Operation(summary = "Log in", description = "Authenticates an active user and returns a JWT bearer token.")
    ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(request.usernameOrEmail());
        if (!userDetails.isEnabled()) {
            throw new DisabledException("Account is inactive.");
        }
        if (!passwordEncoder.matches(request.password(), userDetails.getPassword())) {
            throw new BadCredentialsException("Invalid username/email or password.");
        }
        LoginResponse response = new LoginResponse(
                "Bearer",
                jwtService.generate(userDetails),
                userDetails.getUsername(),
                userDetails.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .toList());
        return ok("Login successful.", response);
    }

    @PostMapping("/signup")
    @Operation(summary = "Sign up", description = "Creates a new application account.")
    ResponseEntity<ApiResponse<SignupResponse>> signup(@Valid @RequestBody SignupRequest request) {
        AppUser user = appUserService.signup(request);
        SignupResponse response = new SignupResponse(user.getId(), user.getUsername(), user.getEmail());
        return created("Account created.", URI.create("/api/auth/signup/" + user.getId()), response);
    }
}
