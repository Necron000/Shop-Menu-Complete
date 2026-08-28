package com.arda.iyzico.project.services;

import com.arda.iyzico.project.config.RabbitMqConfig;
import com.arda.iyzico.project.dto.CheckoutMessage;
import com.arda.iyzico.project.models.Item;
import com.arda.iyzico.project.repositories.ItemRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderFulfilmentService {

    private static final Logger log = LoggerFactory.getLogger(OrderFulfilmentService.class);

    private final ItemRepository itemRepository;

    @RabbitListener(queues = RabbitMqConfig.FULFILMENT_QUEUE)
    @Transactional
    public void fulfil(CheckoutMessage message) {
        log.info("Fulfilling order {} ({} lines)",
                message.getOrderId(), message.getLines().size());

        for (CheckoutMessage.Line line : message.getLines()) {
            Item item = itemRepository.findById(line.getItemId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Item not found: " + line.getItemId()));

            item.sell(line.getQuantity());

            log.info("Stock for item {} reduced by {}, now {}",
                    item.getId(), line.getQuantity(), item.getStock());
        }
    }
}
