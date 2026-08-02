package org.kansei.shieldwall.service;

import org.kansei.shieldwall.messaging.RabbitMQConfig;
import org.kansei.shieldwall.model.User;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Publishes "send this email" events to RabbitMQ - the courier-one service consumes them, renders the template, and delivers via SMTP.
 */
@Service
public class MailEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public MailEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishVerificationEmail(User user, String token) {
        String link = frontendUrl + "/verify-email?token=" + token;
        publish("email.verification", "email-verification", user.getEmail(), user.getUsername(), link);
    }

    public void publishPasswordResetEmail(User user, String token) {
        String link = frontendUrl + "/reset-password?token=" + token;
        publish("email.password-reset", "password-reset", user.getEmail(), user.getUsername(), link);
    }

    private void publish(String routingKey, String template, String to, String username, String link) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("username", username);
        vars.put("link", link);

        Map<String, Object> payload = new HashMap<>();
        payload.put("to", to);
        payload.put("template", template);
        payload.put("vars", vars);

        rabbitTemplate.convertAndSend(RabbitMQConfig.MAIL_EXCHANGE, routingKey, payload);
    }
}
