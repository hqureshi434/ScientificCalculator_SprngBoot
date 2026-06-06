package com.example.scicalculator.audit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AuditUserFilter}. Plain JUnit 5 + AssertJ — no Spring context, no DB.
 *
 * <p>The filter's job: copy the authenticated username from {@link SecurityContextHolder} into
 * {@link AuditUserContext} for the duration of the request, then always clear it. The key
 * observation point is what the holder contains DURING the chain (not just after), so each test
 * uses a custom {@link FilterChain} that captures {@code AuditUserContext.get()} into an
 * {@link AtomicReference} mid-chain. {@code @AfterEach} clears both contexts so nothing leaks.
 */
class AuditUserFilterTest {

    private final AuditUserFilter filter = new AuditUserFilter();

    @AfterEach
    void clearContexts() {
        SecurityContextHolder.clearContext();
        AuditUserContext.clear();
    }

    @Test
    void authenticatedUserBoundDuringChainAndClearedAfter() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice", null, List.of()));

        AtomicReference<String> capturedDuringChain = new AtomicReference<>("sentinel");
        FilterChain chain = (req, res) -> capturedDuringChain.set(AuditUserContext.get());

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertThat(capturedDuringChain.get()).isEqualTo("alice");
        assertThat(AuditUserContext.get()).isNull();
    }

    @Test
    void unauthenticatedRequestBindsNothingAndStillClears() throws Exception {
        SecurityContextHolder.clearContext();

        AtomicReference<String> capturedDuringChain = new AtomicReference<>("sentinel");
        FilterChain chain = (req, res) -> capturedDuringChain.set(AuditUserContext.get());

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertThat(capturedDuringChain.get()).isNull();
        assertThat(AuditUserContext.get()).isNull();
    }

    @Test
    void anonymousTokenIsTreatedAsNoUser() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser",
                        List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        AtomicReference<String> capturedDuringChain = new AtomicReference<>("sentinel");
        FilterChain chain = (req, res) -> capturedDuringChain.set(AuditUserContext.get());

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertThat(capturedDuringChain.get()).isNull();   // the literal "anonymousUser" must NOT be bound
        assertThat(AuditUserContext.get()).isNull();
    }

    @Test
    void holderClearedEvenWhenChainThrows() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice", null, List.of()));

        FilterChain throwingChain = (req, res) -> {
            throw new ServletException("boom");
        };

        assertThatThrownBy(() ->
                filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), throwingChain))
                .isInstanceOf(ServletException.class);

        // The finally block must have cleared the holder despite the exception.
        assertThat(AuditUserContext.get()).isNull();
    }
}
