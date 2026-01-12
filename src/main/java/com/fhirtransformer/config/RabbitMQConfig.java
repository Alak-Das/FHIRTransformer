package com.fhirtransformer.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${app.rabbitmq.queue}")
    private String queueName;

    @Value("${app.rabbitmq.output-queue}")
    private String outputQueueName;

    @Value("${app.rabbitmq.exchange}")
    private String exchangeName;

    @Value("${app.rabbitmq.routingkey}")
    private String routingKey;

    @Bean
    Queue queue() {
        return QueueBuilder.durable(queueName).build();
    }

    @Bean
    Queue outputQueue() {
        return QueueBuilder.durable(outputQueueName).build();
    }

    @Bean
    Exchange exchange() {
        return ExchangeBuilder.topicExchange(exchangeName).durable(true).build();
    }

    @Bean
    Binding binding(Queue queue, Exchange exchange) {
        return BindingBuilder.bind(queue)
                .to(exchange)
                .with(routingKey)
                .noargs();
    }
}
