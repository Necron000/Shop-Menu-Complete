package com.arda.iyzico.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.arda.iyzico.project.exceptions.InsufficientStockException;
import com.arda.iyzico.project.models.Item;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ItemPurchaseTest {

    @Test
    void shouldDecreaseStockWhenPurchaseApproved() {
        Item item = Item.builder()
            .name("Laptop")
            .description("Gaming laptop")
            .price(new BigDecimal("22000.00"))
            .currency("TRY")
            .stock(3)
            .active(true)
            .build();

        item.sell(2);

        assertThat(item.getStock()).isEqualTo(1);
    }

    @Test
    void shouldRejectPurchaseWhenStockIsInsufficient() {
        Item item = Item.builder()
            .name("Keyboard")
            .description("Mechanical keyboard")
            .price(new BigDecimal("1200.00"))
            .currency("TRY")
            .stock(1)
            .active(true)
            .build();

        assertThatThrownBy(() -> item.sell(2))
            .isInstanceOf(InsufficientStockException.class);
    }
}
