package com.arda.iyzico.project.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.arda.iyzico.project.models.PaymentStatus;
import com.arda.iyzico.project.models.PurchaseOrder;
import com.arda.iyzico.project.repositories.PurchaseOrderRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AbandonedCheckoutSweeperTest {

    private PurchaseOrderRepository orders;
    private AbandonedCheckoutSweeper sweeper;

    @BeforeEach
    void setUp() {
        orders = mock(PurchaseOrderRepository.class);
        sweeper = new AbandonedCheckoutSweeper(orders, Duration.ofHours(24));
    }

    @Test
    void cancelsOrdersLeftAwaitingPayment() {
        PurchaseOrder stale = awaitingOrder();
        when(orders.findByStatusAndCreatedAtBefore(eq(PaymentStatus.AWAITING_PAYMENT), any()))
            .thenReturn(List.of(stale));

        sweeper.cancelAbandonedCheckouts();

        assertThat(stale.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(stale.getErrorMessage()).contains("abandoned");
    }

    @Test
    void looksBackExactlyTheConfiguredWindow() {
        when(orders.findByStatusAndCreatedAtBefore(any(), any())).thenReturn(List.of());

        Instant before = Instant.now();
        sweeper.cancelAbandonedCheckouts();
        Instant after = Instant.now();

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        org.mockito.Mockito.verify(orders)
            .findByStatusAndCreatedAtBefore(eq(PaymentStatus.AWAITING_PAYMENT), cutoff.capture());

        assertThat(cutoff.getValue())
            .isBetween(before.minus(Duration.ofHours(24)), after.minus(Duration.ofHours(24)));
    }

    @Test
    void onlyEverAsksForAwaitingPaymentOrders() {
        when(orders.findByStatusAndCreatedAtBefore(any(), any())).thenReturn(List.of());

        sweeper.cancelAbandonedCheckouts();

        org.mockito.Mockito.verify(orders)
            .findByStatusAndCreatedAtBefore(eq(PaymentStatus.AWAITING_PAYMENT), any());
        org.mockito.Mockito.verifyNoMoreInteractions(orders);
    }

    @Test
    void doesNothingWhenThereIsNothingToSweep() {
        when(orders.findByStatusAndCreatedAtBefore(any(), any())).thenReturn(List.of());

        sweeper.cancelAbandonedCheckouts();

        org.mockito.Mockito.verify(orders, org.mockito.Mockito.never()).saveAll(any());
    }

    private static PurchaseOrder awaitingOrder() {
        PurchaseOrder order = PurchaseOrder.builder()
            .id(1L)
            .buyerEmail("ada@example.com")
            .amount(new BigDecimal("100.00"))
            .currency("TRY")
            .status(PaymentStatus.PENDING)
            .build();

        order.markAwaitingPayment("conv-1", "token-1");
        return order;
    }
}
