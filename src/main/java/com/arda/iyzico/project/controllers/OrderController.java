package com.arda.iyzico.project.controllers;

import com.arda.iyzico.project.dto.PurchaseOrderResponse;
import com.arda.iyzico.project.models.PurchaseOrder;
import com.arda.iyzico.project.repositories.PurchaseOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OrderController {

    private final PurchaseOrderRepository purchaseOrderRepository;

    @GetMapping("/orders/{id}")
    public PurchaseOrderResponse getOrder(@PathVariable Long id, Authentication authentication) {
        PurchaseOrder order = purchaseOrderRepository.findWithItemsById(id)
            .orElseThrow(() -> new IllegalArgumentException("Purchase order not found."));

        if (!canRead(order, authentication)) {
            // Same message as a missing order, so ids can't be probed for existence.
            throw new IllegalArgumentException("Purchase order not found.");
        }

        return PurchaseOrderResponse.from(order);
    }

    private boolean canRead(PurchaseOrder order, Authentication authentication) {
        if (order.getBuyerEmail().equals(authentication.getName())) {
            return true;
        }

        return authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch("ROLE_ADMIN"::equals);
    }
}
