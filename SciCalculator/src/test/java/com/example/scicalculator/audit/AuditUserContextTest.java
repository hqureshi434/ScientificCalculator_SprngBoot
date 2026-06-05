package com.example.scicalculator.audit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link AuditUserContext} thread-local holder. Plain JUnit 5 + AssertJ — no
 * Spring context, no database.
 *
 * <p>The holder backs a {@code ThreadLocal}, so each test clears it afterward to stop a value set on
 * the shared runner thread from leaking into the next test.
 */
class
AuditUserContextTest {

    @AfterEach
    void clearContext() {
        AuditUserContext.clear();
    }

    @Test
    void getReturnsValueAfterSet() {
        AuditUserContext.set("alice");

        assertThat(AuditUserContext.get()).isEqualTo("alice");
    }

    @Test
    void getReturnsNullAfterClear() {
        AuditUserContext.set("alice");

        AuditUserContext.clear();

        assertThat(AuditUserContext.get()).isNull();
    }

    @Test
    void valueIsNotVisibleToAnotherThread() throws InterruptedException {
        AuditUserContext.set("alice");

        // Seed with a non-null sentinel so a passing assertion proves the other thread actually ran
        // and read null — not that the lambda was skipped and the reference left untouched.
        AtomicReference<String> seenByOtherThread = new AtomicReference<>("sentinel");
        Thread other = new Thread(() -> seenByOtherThread.set(AuditUserContext.get()));
        other.start();
        other.join();

        // The other thread has its own ThreadLocal slot, so it never sees this thread's value...
        assertThat(seenByOtherThread.get()).isNull();
        // ...and this thread still holds "alice".
        assertThat(AuditUserContext.get()).isEqualTo("alice");
    }
}
