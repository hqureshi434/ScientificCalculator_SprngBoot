package com.example.scicalculator.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure unit test (no Spring) for token issue/validate.
 *
 * <p>The secret and expiration are constructor parameters, so a test can mint a service with a
 * past expiration and get an already-expired token deterministically — no sleeping. HS256 needs a
 * key of at least 256 bits, so the test secrets are 64 ASCII chars (512 bits).
 */
class JwtServiceTest {

    private static final String SECRET =
            "test-secret-test-secret-test-secret-test-secret-test-secret-0123";
    private static final long ONE_HOUR_MS = 3_600_000L;

    @Test
    void generatedTokenRoundTripsUsername() {
        JwtService jwt = new JwtService(SECRET, ONE_HOUR_MS);

        String token = jwt.generateToken("alice");

        assertThat(jwt.extractUsername(token)).isEqualTo("alice");
    }

    @Test
    void expiredTokenIsRejected() {
        // Negative validity → the token's expiration is in the past the moment it's minted.
        JwtService jwt = new JwtService(SECRET, -1_000L);

        String token = jwt.generateToken("alice");

        assertThatThrownBy(() -> jwt.extractUsername(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void tokenSignedWithDifferentSecretIsRejected() {
        JwtService issuer = new JwtService(SECRET, ONE_HOUR_MS);
        JwtService other =
                new JwtService("different-secret-different-secret-different-secret-diff-0123", ONE_HOUR_MS);

        String token = issuer.generateToken("alice");

        // A token this service didn't sign must fail signature verification.
        assertThatThrownBy(() -> other.extractUsername(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void malformedTokenIsRejected() {
        JwtService jwt = new JwtService(SECRET, ONE_HOUR_MS);

        assertThatThrownBy(() -> jwt.extractUsername("not.a.jwt"))
                .isInstanceOf(JwtException.class);
    }
}
