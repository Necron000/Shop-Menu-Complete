package com.arda.iyzico.project.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    public static final String FULFILMENT_QUEUE = "order.fulfilment.queue";

    public static final String FULFILMENT_DLX = "order.fulfilment.dlx";
    public static final String FULFILMENT_DLQ = "order.fulfilment.dlq";

    @Bean
    public Queue fulfilmentQueue() {
        return QueueBuilder.durable(FULFILMENT_QUEUE)
                .deadLetterExchange(FULFILMENT_DLX)
                .deadLetterRoutingKey(FULFILMENT_DLQ)
                .build();
    }

    @Bean
    public DirectExchange fulfilmentDeadLetterExchange() {
        return new DirectExchange(FULFILMENT_DLX);
    }

    @Bean
    public Queue fulfilmentDeadLetterQueue() {
        return QueueBuilder.durable(FULFILMENT_DLQ).build();
    }

    @Bean
    public Binding fulfilmentDeadLetterBinding() {
        return BindingBuilder.bind(fulfilmentDeadLetterQueue())
                .to(fulfilmentDeadLetterExchange())
                .with(FULFILMENT_DLQ);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}
