package com.example.scicalculator.web;

import com.example.scicalculator.domain.Role;
import com.example.scicalculator.domain.User;
import com.example.scicalculator.repository.UserRepository;
import com.example.scicalculator.security.JwtService;
import com.example.scicalculator.web.dto.LoginRequest;
import com.example.scicalculator.web.dto.RegisterRequest;
import com.example.scicalculator.web.dto.TokenResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints — single-purpose by design.
 *
 * <ul>
 *   <li>{@code POST /api/auth/register} — hash the password (BCrypt) and store the user; returns
 *       201 with no body, or 409 if the username is taken. No token is issued here.</li>
 *   <li>{@code POST /api/auth/login} — verify credentials via the {@link AuthenticationManager}
 *       (which delegates to the UserDetailsService + PasswordEncoder); on success return a signed
 *       JWT, on failure 401.</li>
 * </ul>
 *
 * <p>Both paths are permitted without authentication by {@code SecurityConfig} ({@code /api/auth/**}).
 * Plaintext passwords are never stored or compared — only the BCrypt hash is.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthController(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          AuthenticationManager authenticationManager,
                          JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request) {
        if (userRepository.findByUsername(request.username()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        User user = new User(request.username(),
                passwordEncoder.encode(request.password()),
                Role.USER);
        userRepository.save(user);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        } catch (AuthenticationException ex) {
            // Bad password or unknown user — don't distinguish (avoids user enumeration).
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String token = jwtService.generateToken(request.username());
        return ResponseEntity.ok(new TokenResponse(token));
    }
}
