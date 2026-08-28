package com.arda.iyzico.project.dto;

import com.arda.iyzico.project.models.OrderItem;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OrderItemResponse {
    private final Long itemId;
    private final String itemName;
    private final Integer quantity;
    private final BigDecimal unitPrice;
    private final BigDecimal lineTotal;

    public static OrderItemResponse from(OrderItem orderItem) {
        return OrderItemResponse.builder()
            .itemId(orderItem.getItemId())
            .itemName(orderItem.getItemName())
            .quantity(orderItem.getQuantity())
            .unitPrice(orderItem.getUnitPrice())
            .lineTotal(orderItem.getLineTotal())
            .build();
    }
}
