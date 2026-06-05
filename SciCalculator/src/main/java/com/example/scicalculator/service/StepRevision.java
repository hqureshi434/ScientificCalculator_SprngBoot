package com.example.scicalculator.service;

import com.example.scicalculator.domain.Operation;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Service-layer carrier for one Envers revision of a {@link com.example.scicalculator.domain.CalculationStep}.
 * Every field is a fully-resolved scalar so nothing lazy or detached escapes the read transaction.
 */
public record StepRevision(
        int revision, String username, Instant timestamp,
        int sequenceNumber, Operation operation,
        BigDecimal operand, BigDecimal resultAfter) {
}
