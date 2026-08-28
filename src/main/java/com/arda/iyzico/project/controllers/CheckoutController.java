package com.arda.iyzico.project.controllers;

import com.arda.iyzico.project.config.IyzicoProperties;
import com.arda.iyzico.project.dto.CheckoutInitResponse;
import com.arda.iyzico.project.dto.CheckoutRequest;
import com.arda.iyzico.project.models.PurchaseOrder;
import com.arda.iyzico.project.services.IyzicoCheckoutService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/checkout")
@RequiredArgsConstructor
public class CheckoutController {

    private final IyzicoCheckoutService checkoutService;
    private final IyzicoProperties properties;

    @PostMapping("/initialize")
    public CheckoutInitResponse initialize(@Valid @RequestBody CheckoutRequest request,
                                           HttpServletRequest httpRequest) {
        return checkoutService.initialize(request, clientIp(httpRequest));
    }

    private String clientIp(HttpServletRequest httpRequest) {
        String forwarded = httpRequest.getHeader("X-Forwarded-For");

        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }

        return httpRequest.getRemoteAddr();
    }

    @PostMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam("token") String token) {
        PurchaseOrder order = checkoutService.handleCallback(token);

        URI redirect = URI.create(
                properties.frontendResultUrl() + "/" + order.getId());

        return ResponseEntity.status(HttpStatus.FOUND).location(redirect).build();
    }
}