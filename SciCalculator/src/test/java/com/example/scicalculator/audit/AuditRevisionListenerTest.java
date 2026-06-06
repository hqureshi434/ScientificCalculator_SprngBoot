package com.example.scicalculator.audit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AuditRevisionListener}. Plain JUnit 5 + AssertJ — no Spring, no database.
 *
 * <p>The listener reads the current user from {@link AuditUserContext} (a {@code ThreadLocal}) and
 * stamps it onto the revision entity, falling back to "system" when no user is bound. Each test
 * clears the holder afterward so a bound user can't leak into the next test on the runner thread.
 */
class AuditRevisionListenerTest {

    private final AuditRevisionListener listener = new AuditRevisionListener();

    @AfterEach
    void clearContext() {
        AuditUserContext.clear();
    }

    @Test
    void stampsBoundUsername() {
        AuditUserContext.set("alice");
        AuditRevisionEntity revision = new AuditRevisionEntity();

        listener.newRevision(revision);

        assertThat(revision.getUsername()).isEqualTo("alice");
    }

    @Test
    void fallsBackToSystemWhenNoUserBound() {
        AuditRevisionEntity revision = new AuditRevisionEntity();

        listener.newRevision(revision);

        assertThat(revision.getUsername()).isEqualTo("system");
    }

    @Test
    void fallsBackToSystemWhenBoundUserIsBlank() {
        AuditUserContext.set("  ");
        AuditRevisionEntity revision = new AuditRevisionEntity();

        listener.newRevision(revision);

        assertThat(revision.getUsername()).isEqualTo("system");
    }
}
