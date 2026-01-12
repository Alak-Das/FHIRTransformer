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

    @Value("${app.rabbitmq.dlq}")
    private String dlqName;

    @Value("${app.rabbitmq.dlx}")
    private String dlxName;

    @Value("${app.rabbitmq.dl-routingkey}")
    private String dlRoutingKey;

    @Value("${app.rabbitmq.exchange}")
    private String exchangeName;

    @Value("${app.rabbitmq.routingkey}")
    private String routingKey;

    @Bean
    Queue queue() {
        return QueueBuilder.durable(queueName)
                .withArgument("x-dead-letter-exchange", dlxName)
                .withArgument("x-dead-letter-routing-key", dlRoutingKey)
                .build();
    }

    // --- HL7 to FHIR Flow ---

    @Bean
    Queue outputQueue() {
        return QueueBuilder.durable(outputQueueName).build();
    }

    @Bean
    Queue deadLetterQueue() {
        return QueueBuilder.durable(dlqName).build();
    }

    @Bean
    Exchange deadLetterExchange() {
        return ExchangeBuilder.directExchange(dlxName).durable(true).build();
    }

    @Bean
    Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue())
                .to(deadLetterExchange())
                .with(dlRoutingKey)
                .noargs();
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

    // --- FHIR to HL7 Flow ---

    @Value("${app.rabbitmq.fhir.queue}")
    private String fhirQueueName;

    @Value("${app.rabbitmq.v2.output-queue}")
    private String v2OutputQueueName;

    @Value("${app.rabbitmq.fhir.exchange}")
    private String fhirExchangeName;

    @Value("${app.rabbitmq.fhir.routingkey}")
    private String fhirRoutingKey;

    @Value("${app.rabbitmq.fhir.dlq}")
    private String fhirDlqName;

    @Value("${app.rabbitmq.fhir.dlx}")
    private String fhirDlxName;

    @Value("${app.rabbitmq.fhir.dl-routingkey}")
    private String fhirDlRoutingKey;

    @Bean
    Queue fhirQueue() {
        return QueueBuilder.durable(fhirQueueName)
                .withArgument("x-dead-letter-exchange", fhirDlxName)
                .withArgument("x-dead-letter-routing-key", fhirDlRoutingKey)
                .build();
    }

    @Bean
    Queue v2OutputQueue() {
        return QueueBuilder.durable(v2OutputQueueName).build();
    }

    @Bean
    Exchange fhirExchange() {
        return ExchangeBuilder.topicExchange(fhirExchangeName).durable(true).build();
    }

    @Bean
    Binding fhirBinding() {
        return BindingBuilder.bind(fhirQueue())
                .to(fhirExchange())
                .with(fhirRoutingKey)
                .noargs();
    }

    @Bean
    Queue fhirDlq() {
        return QueueBuilder.durable(fhirDlqName).build();
    }

    @Bean
    Exchange fhirDlx() {
        return ExchangeBuilder.directExchange(fhirDlxName).durable(true).build();
    }

    @Bean
    Binding fhirDlqBinding() {
        return BindingBuilder.bind(fhirDlq())
                .to(fhirDlx())
                .with(fhirDlRoutingKey)
                .noargs();
    }
}
