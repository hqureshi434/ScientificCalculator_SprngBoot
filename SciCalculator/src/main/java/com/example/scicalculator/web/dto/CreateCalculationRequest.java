package com.example.scicalculator.web.dto;

/** Create-calculation payload. {@code name} is optional — blank/null means an unnamed calculation. */
public record CreateCalculationRequest(String name) {
}
