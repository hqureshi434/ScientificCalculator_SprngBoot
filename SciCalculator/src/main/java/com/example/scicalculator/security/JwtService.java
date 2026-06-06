package com.example.scicalculator.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Issues and validates signed JWTs (HS256).
 *
 * <p>Uses the jjwt 0.13.0 API, which differs from 0.11.x examples found online:
 * <ul>
 *   <li>building: {@code Jwts.builder().subject(...).signWith(key).compact()}
 *       (not {@code setSubject} / {@code signWith(key, SignatureAlgorithm.HS256)}),</li>
 *   <li>parsing: {@code Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload()}
 *       (not {@code parserBuilder().setSigningKey(...).parseClaimsJws(...)}).</li>
 * </ul>
 *
 * <p>Parsing verifies the HMAC signature (proves we issued the token and it wasn't tampered with)
 * and the expiration; any failure throws an {@link io.jsonwebtoken.JwtException} subtype, which
 * callers treat as "not authenticated."
 *
 * <p>The secret and expiration are constructor parameters: {@code @Value}-injected from
 * {@code application.yaml} in the app, but also directly constructable in unit tests.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(@Value("${jwt.secret}") String secret,
                      @Value("${jwt.expiration-ms}") long expirationMs) {
        // HS256 requires a key of at least 256 bits; the configured secret must be >= 32 bytes.
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    /** Issues a token whose subject is the username, valid from now for {@code expirationMs}. */
    public String generateToken(String username) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expirationMs))
                .signWith(key)
                .compact();
    }

    /**
     * Verifies the signature and expiration, then returns the subject (username).
     *
     * @throws io.jsonwebtoken.JwtException if the token is malformed, tampered, signed with a
     *                                      different key, or expired.
     */
    public String extractUsername(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }
}
