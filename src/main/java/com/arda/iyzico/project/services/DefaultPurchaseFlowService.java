package com.arda.iyzico.project.services;

import com.arda.iyzico.project.dto.PurchaseOrderResponse;
import com.arda.iyzico.project.repositories.PurchaseOrderRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DefaultPurchaseFlowService implements PurchaseFlowService {

    private final PurchaseOrderRepository purchaseOrderRepository;

    @Override
    @Transactional(readOnly = true)
    public List<PurchaseOrderResponse> getOrders() {
        return purchaseOrderRepository.findByBuyerEmailOrderByCreatedAtDesc(currentUserEmail()).stream()
            .map(PurchaseOrderResponse::from)
            .toList();
    }

    private String currentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user.");
        }
        return authentication.getName();
    }
}
