package com.example.scicalculator.security;

import com.example.scicalculator.domain.User;
import com.example.scicalculator.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Adapts the domain {@link User} to Spring Security's {@link UserDetails}.
 *
 * <p>Spring Security never sees our entity directly — it works in terms of {@code UserDetails}.
 * This service is the single translation point: load a {@link User} by username, hand over the
 * stored password <em>hash</em> (never plaintext; the encoder compares against it), and map the
 * {@link com.example.scicalculator.domain.Role} to a {@code ROLE_*} authority that the security
 * rules and {@code @PreAuthorize} checks understand.
 *
 * <p>Both the login flow (authentication manager) and the {@code JwtAuthenticationFilter} call this
 * to resolve a username into an authenticated principal.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public AppUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPasswordHash())
                .authorities(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
                .build();
    }
}
