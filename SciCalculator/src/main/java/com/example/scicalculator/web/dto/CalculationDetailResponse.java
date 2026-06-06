package com.example.scicalculator.web.dto;

import java.util.List;

/** Calculation plus its ordered steps — the body of {@code GET /api/calculations/{id}}. */
public record CalculationDetailResponse(
        CalculationResponse calculation, List<StepResponse> steps) {
}
