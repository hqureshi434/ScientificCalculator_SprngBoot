package com.example.scicalculator.web.dto;

import com.example.scicalculator.domain.Calculation;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Calculation view. The {@code owner} username is passed in (from the authenticated principal) rather
 * than read from the entity: {@code Calculation.owner} is a LAZY association and the entity is detached
 * by the time the controller maps it (open-in-view is false), so {@code getOwner()} would throw.
 */
public record CalculationResponse(
        Long id, String name, String owner,
        BigDecimal currentValue, Instant createdAt, Instant updatedAt) {

    public static CalculationResponse from(Calculation c, String owner) {
        return new CalculationResponse(c.getId(), c.getName(), owner,
                c.getCurrentValue(), c.getCreatedAt(), c.getUpdatedAt());
    }
}
