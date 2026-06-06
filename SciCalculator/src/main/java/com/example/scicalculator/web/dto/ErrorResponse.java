package com.example.scicalculator.web.dto;

/** Minimal error body returned by {@code ApiExceptionHandler}. */
public record ErrorResponse(String message) {
}
