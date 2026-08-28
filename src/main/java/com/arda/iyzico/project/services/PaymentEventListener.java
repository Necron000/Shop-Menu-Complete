package com.arda.iyzico.project.services;

import com.arda.iyzico.project.config.RabbitMqConfig;
import com.arda.iyzico.project.dto.CheckoutMessage;
import com.arda.iyzico.project.models.PurchaseOrder;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private final RabbitTemplate rabbitTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        PurchaseOrder order = event.order();

        List<CheckoutMessage.Line> lines = event.items().stream()
                .map(item -> CheckoutMessage.Line.builder()
                        .itemId(item.getItemId())
                        .quantity(item.getQuantity())
                        .build())
                .toList();

        CheckoutMessage message = CheckoutMessage.builder()
                .orderId(order.getId())
                .buyerEmail(order.getBuyerEmail())
                .lines(lines)
                .amount(order.getAmount())
                .currency(order.getCurrency())
                .build();

        rabbitTemplate.convertAndSend(RabbitMqConfig.FULFILMENT_QUEUE, message);
    }
}