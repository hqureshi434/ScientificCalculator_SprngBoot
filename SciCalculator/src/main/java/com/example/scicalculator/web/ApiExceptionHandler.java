package com.example.scicalculator.web;

import com.example.scicalculator.exception.CalculationNotFoundException;
import com.example.scicalculator.web.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates the two service-layer exceptions into HTTP status codes.
 *
 * <ul>
 *   <li>{@link CalculationNotFoundException} → 404: the service throws this for both unknown and
 *       not-owned-by-caller calculations (the single 404 boundary in {@code requireOwned}), so a
 *       caller can't distinguish "doesn't exist" from "isn't yours."</li>
 *   <li>{@link ArithmeticException} → 400: an invalid operation (e.g. divide-by-zero) from the
 *       calculation engine; the transaction has already rolled back by the time we get here.</li>
 * </ul>
 *
 * <p>Deliberately NOT handled here: {@code IllegalStateException} (the should-never-happen
 * missing-authenticated-user case) keeps falling through to Spring's default 500. Bean-validation
 * failures ({@code @Valid}) and unreadable bodies already produce 400 via Spring's own handling and
 * are not customized.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(CalculationNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(CalculationNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(ArithmeticException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(ArithmeticException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(ex.getMessage()));
    }
}
