package com.example.scicalculator.web.dto;

/** Login response: the signed JWT the client puts in the {@code Authorization: Bearer} header. */
public record TokenResponse(String token) {
}
