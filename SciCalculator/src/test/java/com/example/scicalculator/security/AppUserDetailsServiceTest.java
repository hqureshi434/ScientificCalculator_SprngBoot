package com.example.scicalculator.security;

import com.example.scicalculator.domain.Role;
import com.example.scicalculator.domain.User;
import com.example.scicalculator.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Pure unit test (Mockito, no Spring context) for the UserDetailsService adapter.
 *
 * <p>The service is glue: it looks a {@link User} up by username and translates it into Spring
 * Security's {@link UserDetails}. The two things worth pinning down are that (1) the stored
 * password hash and username come through unchanged and the {@link Role} is mapped to a
 * {@code ROLE_*} authority, and (2) a missing user becomes a {@link UsernameNotFoundException}
 * rather than a null or empty Optional leaking upward.
 */
@ExtendWith(MockitoExtension.class)
class AppUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AppUserDetailsService service;

    @Test
    void loadsUserAndMapsRoleToAuthority() {
        User user = new User("alice", "hashed-pw", Role.ADMIN);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("alice");

        assertThat(details.getUsername()).isEqualTo("alice");
        assertThat(details.getPassword()).isEqualTo("hashed-pw");
        assertThat(details.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void unknownUsernameThrows() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
