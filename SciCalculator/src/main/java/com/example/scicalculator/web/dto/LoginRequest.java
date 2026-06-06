package com.example.scicalculator.web.dto;

import jakarta.validation.constraints.NotBlank;

/** Login payload. Both fields are required; validated by {@code @Valid} in the controller. */
public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password) {
}
