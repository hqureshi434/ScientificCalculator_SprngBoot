package com.example.scicalculator.exception;

/**
 * Thrown when a calculation cannot be served to the caller — either it does not exist or it is owned
 * by someone else. The same exception covers both cases so a caller cannot probe for the existence of
 * other users' calculations; {@code ApiExceptionHandler} maps it to HTTP 404.
 */
public class CalculationNotFoundException extends RuntimeException {
    public CalculationNotFoundException(Long calculationId) {
        super("Calculation not found: " + calculationId);
    }
}
