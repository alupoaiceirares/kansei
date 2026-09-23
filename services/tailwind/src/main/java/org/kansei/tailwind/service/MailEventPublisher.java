package org.kansei.tailwind.service;

import org.kansei.tailwind.messaging.RabbitMQConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Publishes "send this email" events to mail.events, courier-one renders and sends them and is the only
 * holder of SMTP credentials.
 */
@Service
public class MailEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final String adminEmail;

    public MailEventPublisher(RabbitTemplate rabbitTemplate, @Value("${tailwind.admin-email}") String adminEmail) {
        this.rabbitTemplate = rabbitTemplate;
        this.adminEmail = adminEmail;
    }

    public void publishDisableRequest(UUID userId, String username) {
        Map<String, Object> payload = Map.of(
                "to", adminEmail,
                "template", "tailwind-disable-request",
                "vars", Map.of("username", username, "userId", userId.toString()));
        rabbitTemplate.convertAndSend(RabbitMQConfig.MAIL_EXCHANGE, "tailwind.disable-request", payload);
    }
}
