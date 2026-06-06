package com.example.scicalculator.web.dto;

import com.example.scicalculator.domain.CalculationStep;
import com.example.scicalculator.domain.Operation;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Step view. Maps only the step's own columns — {@code getCalculation()} is a LAZY association on a
 * detached entity and is deliberately not touched.
 */
public record StepResponse(
        Long id, int sequenceNumber, Operation operation,
        BigDecimal operand, BigDecimal resultAfter, Instant createdAt) {

    public static StepResponse from(CalculationStep s) {
        return new StepResponse(s.getId(), s.getSequenceNumber(), s.getOperation(),
                s.getOperand(), s.getResultAfter(), s.getCreatedAt());
    }
}
