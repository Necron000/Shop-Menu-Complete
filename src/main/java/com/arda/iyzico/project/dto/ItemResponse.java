package com.arda.iyzico.project.dto;

import com.arda.iyzico.project.models.Item;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ItemResponse {
    private final Long id;
    private final String name;
    private final String description;
    private final BigDecimal price;
    private final String currency;
    private final Integer stock;
    private final Boolean active;

    public static ItemResponse from(Item item) {
        return ItemResponse.builder()
            .id(item.getId())
            .name(item.getName())
            .description(item.getDescription())
            .price(item.getPrice())
            .currency(item.getCurrency())
            .stock(item.getStock())
            .active(item.getActive())
            .build();
    }
}
