package com.example.scicalculator.service;

import com.example.scicalculator.domain.Calculation;
import com.example.scicalculator.domain.CalculationStep;
import com.example.scicalculator.domain.Operation;
import com.example.scicalculator.domain.Role;
import com.example.scicalculator.domain.User;
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

    /** Seeds a User and an empty Calculation in a committed transaction; returns the calculation id. */
    private Long seedEmptyCalculation() {
        return txTemplate.execute(status -> {
            User owner = new User("user-" + USER_SEQ.incrementAndGet(), "hash", Role.USER);
            entityManager.persist(owner);
            Calculation calculation = new Calculation(owner);
            entityManager.persist(calculation);
            return calculation.getId();
        });
    }

    @Test
    void appendingValidStepPersistsStepAndUpdatesCurrentValue() {
        Long calculationId = seedEmptyCalculation();

        CalculationStep step = calculationService.appendStep(calculationId, Operation.ADD, new BigDecimal("5"));

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
        Long calculationId = seedEmptyCalculation();

        // One valid step first, so there is committed state for the failed attempt to threaten.
        calculationService.appendStep(calculationId, Operation.ADD, new BigDecimal("5"));

        Calculation beforeFailure = calculationRepository.findById(calculationId).orElseThrow();
        BigDecimal valueBefore = beforeFailure.getCurrentValue();
        Instant updatedAtBefore = beforeFailure.getUpdatedAt();
        int stepCountBefore =
                stepRepository.findByCalculationOrderBySequenceNumberAsc(beforeFailure).size();

        // Divide by zero: the engine throws, and the exception must propagate OUT of the
        // @Transactional method so Spring rolls the whole step back.
        assertThatThrownBy(() ->
                calculationService.appendStep(calculationId, Operation.DIVIDE, BigDecimal.ZERO))
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
        Long calculationId = seedEmptyCalculation();

        calculationService.appendStep(calculationId, Operation.ADD, new BigDecimal("5"));
        CalculationStep second =
                calculationService.appendStep(calculationId, Operation.MULTIPLY, new BigDecimal("2"));

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
                calculationService.appendStep(unknownId, Operation.ADD, new BigDecimal("1")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
