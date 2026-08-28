package com.arda.iyzico.project.dto;

public record AuthResponse(
        String token,
        String email,
        String role
) {}