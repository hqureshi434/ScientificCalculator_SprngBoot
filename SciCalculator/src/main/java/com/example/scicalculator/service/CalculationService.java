package com.example.scicalculator.service;

import com.example.scicalculator.audit.AuditRevisionEntity;
import com.example.scicalculator.domain.Calculation;
import com.example.scicalculator.domain.CalculationStep;
import com.example.scicalculator.domain.Operation;
import com.example.scicalculator.domain.User;
import com.example.scicalculator.exception.CalculationNotFoundException;
import com.example.scicalculator.repository.CalculationRepository;
import com.example.scicalculator.repository.CalculationStepRepository;
import com.example.scicalculator.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.query.AuditEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Applies calculation steps atomically.
 *
 * <p>{@link #appendStep} does two writes inside one transaction — insert a new
 * {@link CalculationStep} and update the parent {@link Calculation}'s currentValue — so they
 * commit together or not at all. The compute happens BEFORE either write: if
 * {@link CalculationEngine#apply} throws (e.g. divide-by-zero), the exception propagates out of
 * this {@code @Transactional} method, Spring rolls the transaction back, and neither write lands.
 * The exception is intentionally NOT caught here — catching it would let the method return
 * normally and Spring would commit instead of rolling back.
 */
@Service
public class CalculationService {

    private final CalculationEngine engine;
    private final CalculationRepository calculationRepository;
    private final CalculationStepRepository stepRepository;
    private final UserRepository userRepository;

    // Field injection is the standard JPA mechanism for the EntityManager (it is a container-managed,
    // transaction-scoped proxy), and is the intended exception to this class's constructor injection.
    @PersistenceContext
    private EntityManager entityManager;

    public CalculationService(CalculationEngine engine,
                              CalculationRepository calculationRepository,
                              CalculationStepRepository stepRepository,
                              UserRepository userRepository) {
        this.engine = engine;
        this.calculationRepository = calculationRepository;
        this.stepRepository = stepRepository;
        this.userRepository = userRepository;
    }

    /**
     * Owner-scoped load: the single 404 boundary. An unknown id and a calculation owned by
     * someone else both yield the same {@link CalculationNotFoundException}, so callers cannot probe
     * for the existence of other users' calculations.
     */
    private Calculation requireOwned(Long calculationId, String ownerUsername) {
        return calculationRepository
                .findByIdAndOwnerUsername(calculationId, ownerUsername)
                .orElseThrow(() -> new CalculationNotFoundException(calculationId));
    }

    @Transactional
    public Calculation createCalculation(String ownerUsername, String name) {
        // Missing user here is a should-never-happen bug (the request was authenticated), not a
        // client 404 — hence IllegalStateException rather than IllegalArgumentException.
        User owner = userRepository.findByUsername(ownerUsername)
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated user not found: " + ownerUsername));
        Calculation calculation = (name == null || name.isBlank())
                ? new Calculation(owner)
                : new Calculation(owner, name);
        return calculationRepository.save(calculation);
    }

    @Transactional
    public CalculationStep appendStep(Long calculationId, String ownerUsername,
                                      Operation operation, BigDecimal operand) {
        Calculation calculation = requireOwned(calculationId, ownerUsername);

        int nextSequenceNumber = stepRepository
                .findTopByCalculationOrderBySequenceNumberDesc(calculation)
                .map(last -> last.getSequenceNumber() + 1)
                .orElse(1);

        // Compute first: if this throws, no write has happened yet and the transaction rolls back.
        BigDecimal newValue = engine.apply(calculation.getCurrentValue(), operation, operand);

        CalculationStep step = new CalculationStep(calculation, nextSequenceNumber, operation, operand, newValue);
        stepRepository.save(step);

        // Dirty-checking: mutating the managed entity makes Hibernate issue an UPDATE at flush,
        // which fires @PreUpdate to stamp updatedAt. No explicit save needed.
        calculation.setCurrentValue(newValue);

        return step;
    }

    @Transactional(readOnly = true)
    public Calculation getCalculation(Long calculationId, String ownerUsername) {
        return requireOwned(calculationId, ownerUsername);
    }

    @Transactional(readOnly = true)
    public List<CalculationStep> getSteps(Long calculationId, String ownerUsername) {
        Calculation calculation = requireOwned(calculationId, ownerUsername);
        return stepRepository.findByCalculationOrderBySequenceNumberAsc(calculation);
    }

    /**
     * Envers revision history for one calculation's steps, oldest first. Each entry pairs the audited
     * step state with the revision that produced it — including {@code username}, which the audit
     * revision listener stamps from the request-bound {@code AuditUserContext} (falling back to
     * {@code "system"} when no user is bound, e.g. outside a web request).
     */
    @Transactional(readOnly = true)
    public List<StepRevision> getHistory(Long calculationId, String ownerUsername) {
        requireOwned(calculationId, ownerUsername); // ownership gate -> CalculationNotFoundException

        @SuppressWarnings("unchecked")
        List<Object[]> rows = AuditReaderFactory.get(entityManager)
                .createQuery()
                .forRevisionsOfEntity(CalculationStep.class, false, false)
                .add(AuditEntity.relatedId("calculation").eq(calculationId))
                .addOrder(AuditEntity.revisionNumber().asc())
                .getResultList();

        List<StepRevision> history = new ArrayList<>();
        for (Object[] row : rows) {
            CalculationStep step = (CalculationStep) row[0];
            AuditRevisionEntity rev = (AuditRevisionEntity) row[1];
            history.add(new StepRevision(
                    rev.getId(), rev.getUsername(), rev.getRevisionDate().toInstant(),
                    step.getSequenceNumber(), step.getOperation(),
                    step.getOperand(), step.getResultAfter()));
        }
        return history;
    }
}
