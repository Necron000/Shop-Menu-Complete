package com.arda.iyzico.project.dto;

public record CheckoutInitResponse(
        Long orderId,
        String checkoutFormContent,
        String token,
        String status,
        String errorMessage
) {}