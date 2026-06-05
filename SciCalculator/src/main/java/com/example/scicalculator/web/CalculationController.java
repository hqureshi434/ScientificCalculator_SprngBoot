package com.example.scicalculator.web;

import com.example.scicalculator.domain.Calculation;
import com.example.scicalculator.domain.CalculationStep;
import com.example.scicalculator.service.CalculationService;
import com.example.scicalculator.web.dto.AppendStepRequest;
import com.example.scicalculator.web.dto.CalculationDetailResponse;
import com.example.scicalculator.web.dto.CalculationResponse;
import com.example.scicalculator.web.dto.CreateCalculationRequest;
import com.example.scicalculator.web.dto.HistoryEntryResponse;
import com.example.scicalculator.web.dto.StepResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Calculation endpoints. Every operation is scoped to the authenticated caller: the owner username
 * always comes from {@link Authentication#getName()}, never from a request field or the entity. The
 * service enforces ownership (unknown-or-not-yours → {@code CalculationNotFoundException}, mapped to
 * 404 by {@link ApiExceptionHandler}); the controller only reads non-lazy columns when mapping to DTOs,
 * since the entities are detached by the time they return (open-in-view is false).
 */
@RestController
@RequestMapping("/api/calculations")
public class CalculationController {

    private final CalculationService calculationService;

    public CalculationController(CalculationService calculationService) {
        this.calculationService = calculationService;
    }

    @PostMapping
    public ResponseEntity<CalculationResponse> create(
            @Valid @RequestBody CreateCalculationRequest request,
            Authentication authentication) {
        String username = authentication.getName();
        Calculation calc = calculationService.createCalculation(username, request.name());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CalculationResponse.from(calc, username));
    }

    @GetMapping("/{id}")
    public CalculationDetailResponse get(
            @PathVariable Long id, Authentication authentication) {
        String username = authentication.getName();
        Calculation calc = calculationService.getCalculation(id, username);
        List<CalculationStep> steps = calculationService.getSteps(id, username);
        return new CalculationDetailResponse(
                CalculationResponse.from(calc, username),
                steps.stream().map(StepResponse::from).toList());
    }

    @PostMapping("/{id}/steps")
    public ResponseEntity<StepResponse> appendStep(
            @PathVariable Long id,
            @Valid @RequestBody AppendStepRequest request,
            Authentication authentication) {
        String username = authentication.getName();
        CalculationStep step = calculationService.appendStep(
                id, username, request.operation(), request.operand());
        return ResponseEntity.status(HttpStatus.CREATED).body(StepResponse.from(step));
    }

    @GetMapping("/{id}/history")
    public List<HistoryEntryResponse> history(
            @PathVariable Long id, Authentication authentication) {
        return calculationService.getHistory(id, authentication.getName())
                .stream().map(HistoryEntryResponse::from).toList();
    }
}
