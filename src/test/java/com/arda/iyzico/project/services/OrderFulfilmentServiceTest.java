package com.arda.iyzico.project.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.arda.iyzico.project.dto.CheckoutMessage;
import com.arda.iyzico.project.exceptions.InsufficientStockException;
import com.arda.iyzico.project.models.Item;
import com.arda.iyzico.project.repositories.ItemRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrderFulfilmentServiceTest {

    private ItemRepository itemRepository;
    private OrderFulfilmentService service;

    @BeforeEach
    void setUp() {
        itemRepository = mock(ItemRepository.class);
        service = new OrderFulfilmentService(itemRepository);
    }

    @Test
    void decrementsStockForEveryLine() {
        Item laptop = stubItem(1L, "Laptop", 5);
        Item mouse  = stubItem(2L, "Mouse", 10);

        service.fulfil(message(line(1L, 2), line(2L, 3)));

        assertThat(laptop.getStock()).isEqualTo(3);
        assertThat(mouse.getStock()).isEqualTo(7);
    }

    @Test
    void takesTheFullQuantityForARepeatedItemInASingleLine() {
        Item laptop = stubItem(1L, "Laptop", 10);

        service.fulfil(message(line(1L, 4)));

        assertThat(laptop.getStock()).isEqualTo(6);
    }

    @Test
    void failsTheWholeMessageWhenOneLineOutrunsStock() {
        Item laptop = stubItem(1L, "Laptop", 5);
        stubItem(2L, "Mouse", 1);

        assertThatThrownBy(() -> service.fulfil(message(line(1L, 2), line(2L, 3))))
            .isInstanceOf(InsufficientStockException.class);

        // Rollback is the transaction's job; what matters is that the exception escapes.
        assertThat(laptop.getStock()).isEqualTo(3);
    }

    @Test
    void failsWhenAnItemHasDisappearedSinceCheckout() {
        when(itemRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.fulfil(message(line(9L, 1))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Item not found: 9");
    }

    @Test
    void rejectsANonPositiveQuantityRatherThanAddingStockBack() {
        Item laptop = stubItem(1L, "Laptop", 5);

        assertThatThrownBy(() -> service.fulfil(message(line(1L, 0))))
            .isInstanceOf(IllegalArgumentException.class);

        assertThat(laptop.getStock()).isEqualTo(5);
    }

    private Item stubItem(Long id, String name, int stock) {
        Item item = Item.builder()
            .id(id)
            .name(name)
            .description(name)
            .price(new BigDecimal("100.00"))
            .currency("TRY")
            .stock(stock)
            .active(true)
            .build();

        when(itemRepository.findById(id)).thenReturn(Optional.of(item));
        return item;
    }

    private static CheckoutMessage.Line line(Long itemId, int quantity) {
        return CheckoutMessage.Line.builder().itemId(itemId).quantity(quantity).build();
    }

    private static CheckoutMessage message(CheckoutMessage.Line... lines) {
        return CheckoutMessage.builder()
            .orderId(42L)
            .buyerEmail("ada@example.com")
            .lines(List.of(lines))
            .amount(new BigDecimal("100.00"))
            .currency("TRY")
            .checkoutToken("token-123")
            .build();
    }
}
