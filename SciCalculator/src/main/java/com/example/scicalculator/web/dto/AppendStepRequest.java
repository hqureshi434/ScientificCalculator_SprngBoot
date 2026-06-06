package com.example.scicalculator.web.dto;

import com.example.scicalculator.domain.Operation;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Append-step payload. Both fields are required; validated by {@code @Valid} in the controller. */
public record AppendStepRequest(
        @NotNull Operation operation,
        @NotNull BigDecimal operand) {
}
