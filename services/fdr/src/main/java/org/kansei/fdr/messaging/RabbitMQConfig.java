package org.kansei.fdr.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * audit.events - every producing service declares this same exchange too, so whichever service
 * starts first doesn't fail, same reasoning as shieldwall's mail.events. fdr is the only consumer,
 * bound with # so it gets every routing key regardless of service/action.
 * A message that exhausts the listener's in-process retry (see application.properties) gets
 * rejected, not requeued - the queue's own dead-letter args then route it to the dlx/dlq instead
 * of looping forever.
 */
@Configuration
public class RabbitMQConfig {

    public static final String AUDIT_EXCHANGE = "audit.events";
    public static final String AUDIT_QUEUE = "fdr.audit";
    public static final String DEAD_LETTER_EXCHANGE = "fdr.audit.dlx";
    public static final String DEAD_LETTER_QUEUE = "fdr.audit.dlq";

    @Bean
    public TopicExchange auditExchange() {
        return new TopicExchange(AUDIT_EXCHANGE);
    }

    @Bean
    public FanoutExchange deadLetterExchange() {
        return new FanoutExchange(DEAD_LETTER_EXCHANGE);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue, FanoutExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange);
    }

    @Bean
    public Queue auditQueue() {
        return QueueBuilder.durable(AUDIT_QUEUE)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .build();
    }

    @Bean
    public Binding auditBinding(Queue auditQueue, TopicExchange auditExchange) {
        return BindingBuilder.bind(auditQueue).to(auditExchange).with("#");
    }

    @Bean
    public JacksonJsonMessageConverter jacksonJsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
