package com.example.scicalculator.configuration;

import com.example.scicalculator.audit.AuditUserFilter;
import com.example.scicalculator.security.JwtAuthenticationFilter;
import com.example.scicalculator.security.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless, JWT-based security. Replaces the dev-only HTTP-Basic config.
 *
 * <p>Two filter chains, profile-scoped, so the permissive H2-console access can never leak into a
 * production run:
 * <ul>
 *   <li>{@code @Profile("dev")} — additionally permits {@code /h2-console/**} and relaxes
 *       frame options so the console renders. Active only when the "dev" profile is on.</li>
 *   <li>{@code @Profile("!dev")} — no console, no frame relaxation. Everything except the auth
 *       endpoints requires a valid JWT. This is what runs when no profile (or any non-dev profile)
 *       is active, so a default run is never left without a chain.</li>
 * </ul>
 *
 * <p>Both chains are stateless (no HTTP session — the JWT is re-validated on every request), disable
 * CSRF (there are no browser-session cookies to protect; auth rides the Authorization header), and
 * insert {@link JwtAuthenticationFilter} before {@link UsernamePasswordAuthenticationFilter} so a
 * Bearer token is turned into an {@code Authentication} before authorization runs.
 *
 * <p>The filter is constructed here (not a {@code @Component}) to keep it out of Boot's automatic
 * servlet-filter registration; it runs only where it's placed in these chains.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    public SecurityConfig(JwtService jwtService, UserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    private JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtService, userDetailsService);
    }

    /**
     * Exposes the {@link AuthenticationManager} so the login endpoint can authenticate
     * username/password (delegating to the {@code UserDetailsService} + {@code PasswordEncoder}).
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
            throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    @Profile("dev")
    public SecurityFilterChain devFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/h2-console/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new AuditUserFilter(), JwtAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @Profile("!dev")
    public SecurityFilterChain appFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new AuditUserFilter(), JwtAuthenticationFilter.class);
        return http.build();
    }
}
