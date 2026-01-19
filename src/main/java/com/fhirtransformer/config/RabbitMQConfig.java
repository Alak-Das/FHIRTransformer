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

    // --- Retry Queues for HL7→FHIR (Exponential Backoff: 5s, 15s, 45s) ---

    @Bean
    Queue hl7RetryQueue1() {
        return QueueBuilder.durable("hl7-messages-retry-1")
                .withArgument("x-message-ttl", 5000) // 5 seconds delay
                .withArgument("x-dead-letter-exchange", exchangeName)
                .withArgument("x-dead-letter-routing-key", routingKey)
                .build();
    }

    @Bean
    Binding hl7RetryBinding1() {
        return BindingBuilder.bind(hl7RetryQueue1())
                .to(exchange())
                .with("hl7.retry.1")
                .noargs();
    }

    @Bean
    Queue hl7RetryQueue2() {
        return QueueBuilder.durable("hl7-messages-retry-2")
                .withArgument("x-message-ttl", 15000) // 15 seconds delay
                .withArgument("x-dead-letter-exchange", exchangeName)
                .withArgument("x-dead-letter-routing-key", routingKey)
                .build();
    }

    @Bean
    Binding hl7RetryBinding2() {
        return BindingBuilder.bind(hl7RetryQueue2())
                .to(exchange())
                .with("hl7.retry.2")
                .noargs();
    }

    @Bean
    Queue hl7RetryQueue3() {
        return QueueBuilder.durable("hl7-messages-retry-3")
                .withArgument("x-message-ttl", 45000) // 45 seconds delay
                .withArgument("x-dead-letter-exchange", dlxName) // Final retry → DLQ
                .withArgument("x-dead-letter-routing-key", dlRoutingKey)
                .build();
    }

    @Bean
    Binding hl7RetryBinding3() {
        return BindingBuilder.bind(hl7RetryQueue3())
                .to(exchange())
                .with("hl7.retry.3")
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

    // --- Retry Queues for FHIR→HL7 (Exponential Backoff: 5s, 15s, 45s) ---

    @Bean
    Queue fhirRetryQueue1() {
        return QueueBuilder.durable("fhir-to-v2-retry-1")
                .withArgument("x-message-ttl", 5000) // 5 seconds delay
                .withArgument("x-dead-letter-exchange", fhirExchangeName)
                .withArgument("x-dead-letter-routing-key", fhirRoutingKey)
                .build();
    }

    @Bean
    Binding fhirRetryBinding1() {
        return BindingBuilder.bind(fhirRetryQueue1())
                .to(fhirExchange())
                .with("fhir.retry.1")
                .noargs();
    }

    @Bean
    Queue fhirRetryQueue2() {
        return QueueBuilder.durable("fhir-to-v2-retry-2")
                .withArgument("x-message-ttl", 15000) // 15 seconds delay
                .withArgument("x-dead-letter-exchange", fhirExchangeName)
                .withArgument("x-dead-letter-routing-key", fhirRoutingKey)
                .build();
    }

    @Bean
    Binding fhirRetryBinding2() {
        return BindingBuilder.bind(fhirRetryQueue2())
                .to(fhirExchange())
                .with("fhir.retry.2")
                .noargs();
    }

    @Bean
    Queue fhirRetryQueue3() {
        return QueueBuilder.durable("fhir-to-v2-retry-3")
                .withArgument("x-message-ttl", 45000) // 45 seconds delay
                .withArgument("x-dead-letter-exchange", fhirDlxName) // Final retry → DLQ
                .withArgument("x-dead-letter-routing-key", fhirDlRoutingKey)
                .build();
    }

    @Bean
    Binding fhirRetryBinding3() {
        return BindingBuilder.bind(fhirRetryQueue3())
                .to(fhirExchange())
                .with("fhir.retry.3")
                .noargs();
    }
}
