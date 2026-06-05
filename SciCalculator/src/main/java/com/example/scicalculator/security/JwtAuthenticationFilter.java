package com.example.scicalculator.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Authenticates each request from a {@code Authorization: Bearer <jwt>} header.
 *
 * <p>{@link OncePerRequestFilter} guarantees it runs exactly once per request. The logic is
 * intentionally permissive: with no header or a bad/expired token it leaves the SecurityContext
 * empty and lets the chain continue, so the authorization rules (not this filter) produce the 401.
 * It only ever <em>adds</em> an authentication; it never rejects.
 *
 * <p>Not a {@code @Component}: a {@code Filter} bean would be auto-registered into the main servlet
 * chain by Spring Boot and run for every request including the H2 console, separately from the
 * security chain. Instead {@code SecurityConfig} constructs it explicitly and inserts it into the
 * filter chain via {@code addFilterBefore}, so it runs exactly where intended.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, UserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(PREFIX.length());
        try {
            String username = jwtService.extractUsername(token);
            // Only set if we have a username and nothing has authenticated this request yet.
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (JwtException | UsernameNotFoundException ex) {
            // Invalid/expired token, or the user no longer exists: stay unauthenticated and let the
            // authorization layer answer with 401. Never abort the chain from here.
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
