package com.example.scicalculator.service;

import com.example.scicalculator.domain.Calculation;
import com.example.scicalculator.domain.CalculationStep;
import com.example.scicalculator.domain.Operation;
import com.example.scicalculator.domain.Role;
import com.example.scicalculator.domain.User;
import com.example.scicalculator.exception.CalculationNotFoundException;
import com.example.scicalculator.repository.CalculationRepository;
import com.example.scicalculator.repository.CalculationStepRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for the transactional CalculationService.
 *
 * <p>Deliberately NOT annotated {@code @Transactional}: a test-managed transaction would wrap
 * (and ultimately roll back) everything, masking whether the service's own {@code @Transactional}
 * boundary actually commits or rolls back. Here the service method owns its transaction, seed data
 * is committed in a separate programmatic transaction, and assertions read back independently.
 */
@SpringBootTest
class CalculationServiceTest {

    @Autowired
    private CalculationService calculationService;

    @Autowired
    private CalculationRepository calculationRepository;

    @Autowired
    private CalculationStepRepository stepRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private TransactionTemplate txTemplate;

    /** Unique usernames per seed — the class shares one in-memory DB and username is UNIQUE. */
    private static final AtomicInteger USER_SEQ = new AtomicInteger();

    @BeforeEach
    void setUp() {
        txTemplate = new TransactionTemplate(transactionManager);
    }

    /** Holds a seeded calculation's id alongside its owner's username, since loads are owner-scoped. */
    private record Seed(Long calculationId, String ownerUsername) {
    }

    /** Seeds a User and an empty Calculation in a committed transaction. */
    private Seed seedEmptyCalculation() {
        return seedCalculationOwnedBy("user-" + USER_SEQ.incrementAndGet());
    }

    /** Seeds a User with the exact given username and an empty Calculation they own. */
    private Seed seedCalculationOwnedBy(String username) {
        return txTemplate.execute(status -> {
            User owner = new User(username, "hash", Role.USER);
            entityManager.persist(owner);
            Calculation calculation = new Calculation(owner);
            entityManager.persist(calculation);
            return new Seed(calculation.getId(), username);
        });
    }

    /** Seeds just a User row with the given username (no calculation). */
    private void seedUser(String username) {
        txTemplate.execute(status -> {
            entityManager.persist(new User(username, "hash", Role.USER));
            return null;
        });
    }

    @Test
    void appendingValidStepPersistsStepAndUpdatesCurrentValue() {
        Seed seed = seedEmptyCalculation();
        Long calculationId = seed.calculationId();

        CalculationStep step = calculationService.appendStep(
                calculationId, seed.ownerUsername(), Operation.ADD, new BigDecimal("5"));

        // The returned step is the first one and carries the new running value.
        assertThat(step.getSequenceNumber()).isEqualTo(1);
        assertThat(step.getResultAfter()).isEqualByComparingTo("5");

        // The parent's currentValue was updated in the same transaction.
        Calculation reloaded = calculationRepository.findById(calculationId).orElseThrow();
        assertThat(reloaded.getCurrentValue()).isEqualByComparingTo("5");

        // Exactly one step row exists for this calculation.
        List<CalculationStep> steps = stepRepository.findByCalculationOrderBySequenceNumberAsc(reloaded);
        assertThat(steps).hasSize(1);
    }

    @Test
    void divideByZeroRollsBackLeavingNoOrphanStepOrChangedValue() {
        Seed seed = seedEmptyCalculation();
        Long calculationId = seed.calculationId();

        // One valid step first, so there is committed state for the failed attempt to threaten.
        calculationService.appendStep(calculationId, seed.ownerUsername(), Operation.ADD, new BigDecimal("5"));

        Calculation beforeFailure = calculationRepository.findById(calculationId).orElseThrow();
        BigDecimal valueBefore = beforeFailure.getCurrentValue();
        Instant updatedAtBefore = beforeFailure.getUpdatedAt();
        int stepCountBefore =
                stepRepository.findByCalculationOrderBySequenceNumberAsc(beforeFailure).size();

        // Divide by zero: the engine throws, and the exception must propagate OUT of the
        // @Transactional method so Spring rolls the whole step back.
        assertThatThrownBy(() ->
                calculationService.appendStep(calculationId, seed.ownerUsername(), Operation.DIVIDE, BigDecimal.ZERO))
                .isInstanceOf(ArithmeticException.class);

        // Fresh read: the failed attempt left no trace.
        Calculation afterFailure = calculationRepository.findById(calculationId).orElseThrow();
        assertThat(afterFailure.getCurrentValue()).isEqualByComparingTo(valueBefore);   // unchanged
        assertThat(afterFailure.getUpdatedAt()).isEqualTo(updatedAtBefore);             // @PreUpdate never fired

        List<CalculationStep> stepsAfter =
                stepRepository.findByCalculationOrderBySequenceNumberAsc(afterFailure);
        assertThat(stepsAfter).hasSize(stepCountBefore);  // no orphan step
        assertThat(stepsAfter).hasSize(1);
    }

    @Test
    void secondStepIncrementsSequenceNumberAndChainsFromCurrentValue() {
        Seed seed = seedEmptyCalculation();
        Long calculationId = seed.calculationId();

        calculationService.appendStep(calculationId, seed.ownerUsername(), Operation.ADD, new BigDecimal("5"));
        CalculationStep second =
                calculationService.appendStep(calculationId, seed.ownerUsername(), Operation.MULTIPLY, new BigDecimal("2"));

        // Second step is sequence 2 and chains off the first step's result (5 * 2 = 10).
        assertThat(second.getSequenceNumber()).isEqualTo(2);
        assertThat(second.getResultAfter()).isEqualByComparingTo("10");

        Calculation reloaded = calculationRepository.findById(calculationId).orElseThrow();
        assertThat(reloaded.getCurrentValue()).isEqualByComparingTo("10");
        assertThat(stepRepository.findByCalculationOrderBySequenceNumberAsc(reloaded)).hasSize(2);
    }

    @Test
    void appendingToUnknownCalculationThrows() {
        long unknownId = 999_999L;

        assertThatThrownBy(() ->
                calculationService.appendStep(unknownId, "user-nobody", Operation.ADD, new BigDecimal("1")))
                .isInstanceOf(CalculationNotFoundException.class);
    }

    @Test
    void createCalculationPersistsCalculationOwnedByGivenUser() {
        String username = "calcuser-create-" + USER_SEQ.incrementAndGet();
        seedUser(username);

        Calculation created = calculationService.createCalculation(username, null);

        // currentValue is seeded in the constructor, so it is readable on the returned entity.
        assertThat(created.getCurrentValue()).isEqualByComparingTo(BigDecimal.ZERO);
        // Prove ownership via the owner-scoped finder rather than navigating the LAZY owner
        // association on a detached entity (open-in-view is off — that would throw).
        assertThat(calculationRepository.findByIdAndOwnerUsername(created.getId(), username)).isPresent();
    }

    @Test
    void getCalculationReturnsCalculationForItsOwner() {
        Seed seed = seedCalculationOwnedBy("calcuser-get-" + USER_SEQ.incrementAndGet());

        Calculation found = calculationService.getCalculation(seed.calculationId(), seed.ownerUsername());

        assertThat(found.getId()).isEqualTo(seed.calculationId());
        // Ownership is confirmed via the finder (LAZY owner can't be navigated on the detached entity).
        assertThat(calculationRepository.findByIdAndOwnerUsername(seed.calculationId(), seed.ownerUsername()))
                .isPresent();
    }

    @Test
    void getStepsReturnsOwnersStepsInSequenceOrder() {
        Seed seed = seedCalculationOwnedBy("calcuser-steps-" + USER_SEQ.incrementAndGet());
        calculationService.appendStep(seed.calculationId(), seed.ownerUsername(), Operation.ADD, new BigDecimal("5"));
        calculationService.appendStep(seed.calculationId(), seed.ownerUsername(), Operation.MULTIPLY, new BigDecimal("2"));

        List<CalculationStep> steps = calculationService.getSteps(seed.calculationId(), seed.ownerUsername());

        assertThat(steps).hasSize(2);
        assertThat(steps.get(0).getSequenceNumber()).isEqualTo(1);
        assertThat(steps.get(1).getSequenceNumber()).isEqualTo(2);
        assertThat(steps.get(1).getResultAfter()).isEqualByComparingTo("10");
    }

    @Test
    void crossOwnerAccessIsRejectedForEveryOwnerScopedMethod() {
        // Calculation belongs to A; B must not be able to read or mutate it.
        Seed owned = seedCalculationOwnedBy("calcuser-A-" + USER_SEQ.incrementAndGet());
        String otherUser = "calcuser-B-" + USER_SEQ.incrementAndGet();
        seedUser(otherUser);

        assertThatThrownBy(() ->
                calculationService.appendStep(owned.calculationId(), otherUser, Operation.ADD, new BigDecimal("1")))
                .isInstanceOf(CalculationNotFoundException.class);

        assertThatThrownBy(() ->
                calculationService.getCalculation(owned.calculationId(), otherUser))
                .isInstanceOf(CalculationNotFoundException.class);

        assertThatThrownBy(() ->
                calculationService.getSteps(owned.calculationId(), otherUser))
                .isInstanceOf(CalculationNotFoundException.class);
    }

    @Test
    void createCalculationForUnknownUserThrowsIllegalState() {
        String username = "calcuser-ghost-" + USER_SEQ.incrementAndGet();  // never persisted

        assertThatThrownBy(() ->
                calculationService.createCalculation(username, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void getHistoryReturnsOneEntryPerStepInRevisionOrder() {
        Seed seed = seedCalculationOwnedBy("calcuser-hist-" + USER_SEQ.incrementAndGet());
        calculationService.appendStep(seed.calculationId(), seed.ownerUsername(), Operation.ADD, new BigDecimal("5"));
        calculationService.appendStep(seed.calculationId(), seed.ownerUsername(), Operation.MULTIPLY, new BigDecimal("2"));

        List<StepRevision> history = calculationService.getHistory(seed.calculationId(), seed.ownerUsername());

        assertThat(history).hasSize(2);

        StepRevision first = history.get(0);
        assertThat(first.sequenceNumber()).isEqualTo(1);
        assertThat(first.operation()).isEqualTo(Operation.ADD);
        assertThat(first.operand()).isEqualByComparingTo("5");
        assertThat(first.resultAfter()).isEqualByComparingTo("5");
        // No request filter runs in a service test, so AuditUserContext is empty and the listener
        // stamps the "system" fallback — asserting it confirms the fallback path.
        assertThat(first.username()).isEqualTo("system");

        StepRevision second = history.get(1);
        assertThat(second.sequenceNumber()).isEqualTo(2);
        assertThat(second.operation()).isEqualTo(Operation.MULTIPLY);
        assertThat(second.operand()).isEqualByComparingTo("2");
        assertThat(second.resultAfter()).isEqualByComparingTo("10");
        assertThat(second.username()).isEqualTo("system");

        // Ordered oldest-first by revision number, which matches sequence order here.
        assertThat(first.revision()).isLessThan(second.revision());
    }

    @Test
    void crossOwnerGetHistoryThrowsCalculationNotFound() {
        Seed owned = seedCalculationOwnedBy("calcuser-histA-" + USER_SEQ.incrementAndGet());
        calculationService.appendStep(owned.calculationId(), owned.ownerUsername(), Operation.ADD, new BigDecimal("5"));
        String otherUser = "calcuser-histB-" + USER_SEQ.incrementAndGet();
        seedUser(otherUser);

        assertThatThrownBy(() ->
                calculationService.getHistory(owned.calculationId(), otherUser))
                .isInstanceOf(CalculationNotFoundException.class);
    }
}
