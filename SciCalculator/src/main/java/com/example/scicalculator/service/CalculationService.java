package com.example.scicalculator.service;

import com.example.scicalculator.domain.Calculation;
import com.example.scicalculator.domain.CalculationStep;
import com.example.scicalculator.domain.Operation;
import com.example.scicalculator.repository.CalculationRepository;
import com.example.scicalculator.repository.CalculationStepRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

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

    public CalculationService(CalculationEngine engine,
                              CalculationRepository calculationRepository,
                              CalculationStepRepository stepRepository) {
        this.engine = engine;
        this.calculationRepository = calculationRepository;
        this.stepRepository = stepRepository;
    }

    @Transactional
    public CalculationStep appendStep(Long calculationId, Operation operation, BigDecimal operand) {
        Calculation calculation = calculationRepository.findById(calculationId)
                .orElseThrow(() -> new IllegalArgumentException("Calculation not found: " + calculationId));

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
}
