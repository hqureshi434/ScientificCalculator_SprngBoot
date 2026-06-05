package com.example.scicalculator.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Confirms profile scoping of the two security chains.
 *
 * <p>Under the "dev" profile the context must build the console-permitting {@code devFilterChain}
 * and NOT the {@code appFilterChain}. That the context starts at all proves the dev chain is a valid
 * {@code SecurityFilterChain}. The non-dev chain is covered by the rest of the suite, which runs
 * with no active profile (so {@code @Profile("!dev")} applies) and boots successfully — together the
 * two prove a run is never left without exactly one valid chain.
 */
@SpringBootTest
@ActiveProfiles("dev")
class SecurityConfigProfileTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void devProfileBuildsOnlyTheConsolePermittingChain() {
        assertThat(context.containsBean("devFilterChain")).isTrue();
        assertThat(context.containsBean("appFilterChain")).isFalse();
    }
}
