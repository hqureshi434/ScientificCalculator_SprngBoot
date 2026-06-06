package com.example.scicalculator.web.dto;

import jakarta.validation.constraints.NotBlank;

/** Registration payload. Both fields are required; validated by {@code @Valid} in the controller. */
public record RegisterRequest(
        @NotBlank String username,
        @NotBlank String password) {
}
