package com.arda.iyzico.project.services;

import com.arda.iyzico.project.models.PaymentStatus;
import com.arda.iyzico.project.models.PurchaseOrder;
import com.arda.iyzico.project.repositories.PurchaseOrderRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Closes out checkouts that were started but never paid. */
@Service
public class AbandonedCheckoutSweeper {

    private static final Logger log = LoggerFactory.getLogger(AbandonedCheckoutSweeper.class);

    private static final String REASON =
            "Checkout abandoned - no payment was completed in time.";

    private final PurchaseOrderRepository orders;
    private final Duration abandonAfter;

    AbandonedCheckoutSweeper(PurchaseOrderRepository orders,
                             @Value("${app.checkout.abandon-after}") Duration abandonAfter) {
        this.orders = orders;
        this.abandonAfter = abandonAfter;
    }

    @Scheduled(
            initialDelayString = "${app.checkout.sweep-initial-delay}",
            fixedDelayString = "${app.checkout.sweep-interval}")
    @Transactional
    public void cancelAbandonedCheckouts() {
        Instant cutoff = Instant.now().minus(abandonAfter);

        List<PurchaseOrder> abandoned =
                orders.findByStatusAndCreatedAtBefore(PaymentStatus.AWAITING_PAYMENT, cutoff);

        if (abandoned.isEmpty()) {
            return;
        }

        abandoned.forEach(order -> order.markCancelled(REASON));

        log.info("Cancelled {} checkout(s) abandoned before {}", abandoned.size(), cutoff);
    }
}
