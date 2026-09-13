package org.kansei.wirehood.service;

import org.kansei.wirehood.messaging.RabbitMQConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes "send this email" events to mail.events - same publisher/sender split as shieldwall's
 * own MailEventPublisher, courier-one is the only thing that ever holds SMTP creds. Wirehood's
 * first-ever producer on this exchange - previously only shieldwall published to it.
 */
@Service
public class MailEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${wirehood.admin-email}")
    private String adminEmail;

    public MailEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    // No self-service disable exists (accounts are part of a shared archive) - this sends the
    // request to an admin inbox instead, see WirehoodUserService.requestDisable
    public void publishDisableRequest(UUID userId, String username) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("username", username);
        vars.put("userId", userId.toString());

        Map<String, Object> payload = new HashMap<>();
        payload.put("to", adminEmail);
        payload.put("template", "wirehood-disable-request");
        payload.put("vars", vars);

        rabbitTemplate.convertAndSend(RabbitMQConfig.MAIL_EXCHANGE, "wirehood.disable-request", payload);
    }
}
