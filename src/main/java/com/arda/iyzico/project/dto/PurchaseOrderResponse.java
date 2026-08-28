package com.arda.iyzico.project.dto;

import com.arda.iyzico.project.models.PaymentStatus;
import com.arda.iyzico.project.models.PurchaseOrder;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PurchaseOrderResponse {
    private final Long id;
    private final String buyerEmail;
    private final List<OrderItemResponse> items;
    private final BigDecimal amount;
    private final String currency;
    private final PaymentStatus status;
    private final String checkoutToken;
    private final String errorMessage;
    private final Instant createdAt;

    public static PurchaseOrderResponse from(PurchaseOrder purchaseOrder) {
        return PurchaseOrderResponse.builder()
            .id(purchaseOrder.getId())
            .buyerEmail(purchaseOrder.getBuyerEmail())
            .items(purchaseOrder.getItems().stream().map(OrderItemResponse::from).toList())
            .amount(purchaseOrder.getAmount())
            .currency(purchaseOrder.getCurrency())
            .status(purchaseOrder.getStatus())
            .checkoutToken(purchaseOrder.getCheckoutToken())
            .errorMessage(purchaseOrder.getErrorMessage())
            .createdAt(purchaseOrder.getCreatedAt())
            .build();
    }
}
