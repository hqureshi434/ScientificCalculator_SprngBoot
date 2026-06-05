package com.example.scicalculator.web;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Minimal protected "whoami" endpoint.
 *
 * <p>Not in the original endpoint list — added as a concrete protected target so the JWT flow can be
 * proven end-to-end (401 without a token, 200 with one) before the calculation endpoints exist. It
 * also demonstrates the payoff of the filter: by the time this method runs, the
 * {@link Authentication} has been resolved from the Bearer token and the controller executes "as
 * that user." Returns the authenticated username.
 */
@RestController
public class MeController {

    @GetMapping("/api/me")
    public Map<String, String> me(Authentication authentication) {
        return Map.of("username", authentication.getName());
    }
}
