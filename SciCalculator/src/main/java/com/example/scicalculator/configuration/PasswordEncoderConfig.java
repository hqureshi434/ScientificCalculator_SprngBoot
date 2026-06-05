package com.example.scicalculator.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Supplies the application-wide {@link PasswordEncoder}.
 *
 * <p>Kept separate from {@code SecurityConfig} on purpose: the encoder is needed regardless of the
 * active profile (registration hashes, login verifies), whereas the security filter chains are
 * profile-scoped (dev vs. non-dev). Defined once here, both chains and the auth services share it.
 *
 * <p>BCrypt salts each hash and is deliberately slow, so plaintext passwords are never stored and
 * never compared directly — registration calls {@code encode(raw)}, login calls
 * {@code matches(raw, storedHash)}.
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
