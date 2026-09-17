package org.kansei.wirehood.messaging;

import io.micrometer.tracing.Tracer;
import org.kansei.wirehood.client.ShieldwallUserClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes to audit.events - fdr is the sole consumer. The actual send is blocking (pooled AMQP
 * connection), offloaded to boundedElastic like every other blocking call in this service. Never
 * fails the caller: a failed publish (broker down, nack, return) appends to a local fallback file
 * and logs a WARN instead, chained after the real action already succeeded.
 */
@Component
public class AuditPublisher {

    private static final Logger log = LoggerFactory.getLogger(AuditPublisher.class);
    private static final String SERVICE_NAME = "wirehood";

    private final RabbitTemplate rabbitTemplate;
    private final ShieldwallUserClient shieldwallUserClient;
    private final Tracer tracer;
    private final ObjectMapper objectMapper;
    private final String rabbitmqUsername;
    private final Path fallbackFile;

    public AuditPublisher(RabbitTemplate rabbitTemplate, ShieldwallUserClient shieldwallUserClient, Tracer tracer,
                           ObjectMapper objectMapper,
                           @Value("${spring.rabbitmq.username}") String rabbitmqUsername,
                           @Value("${audit.fallback-file}") String fallbackFilePath) {
        this.rabbitTemplate = rabbitTemplate;
        this.shieldwallUserClient = shieldwallUserClient;
        this.tracer = tracer;
        this.objectMapper = objectMapper;
        this.rabbitmqUsername = rabbitmqUsername;
        this.fallbackFile = Path.of(fallbackFilePath);
        rabbitTemplate.setConfirmCallback(this::onConfirm);
        rabbitTemplate.setReturnsCallback(this::onReturn);
    }

    // Convenience for the admin-gated actions below - the caller only has the actor's id (from
    // X-User-Id), this resolves a display username first via the same cached client every other
    // wirehood-side username lookup uses
    public Mono<Void> publishWithResolvedUsername(String action, UUID actorUserId, String targetType, String targetId, Map<String, Object> details) {
        return shieldwallUserClient.resolveUsernames(List.of(actorUserId))
                .flatMap(usernames -> publish(action, actorUserId, usernames.get(actorUserId), targetType, targetId, details));
    }

    public Mono<Void> publish(String action, UUID actorUserId, String actorUsername, String targetType, String targetId, Map<String, Object> details) {
        return Mono.fromRunnable(() -> publishBlocking(action, actorUserId, actorUsername, targetType, targetId, details))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(ex -> {
                    log.warn("Unexpected failure publishing audit event action={}", action, ex);
                    return Mono.empty();
                })
                .then();
    }

    private void publishBlocking(String action, UUID actorUserId, String actorUsername, String targetType, String targetId, Map<String, Object> details) {
        AuditEvent event = new AuditEvent(UUID.randomUUID(), SERVICE_NAME, action, actorUserId, actorUsername,
                targetType, targetId, details == null ? Map.of() : details, currentTraceId(), Instant.now());
        try {
            String json = objectMapper.writeValueAsString(event);
            Message message = rabbitTemplate.getMessageConverter().toMessage(event, new MessageProperties());
            // Must equal this connection's authenticated username - RabbitMQ itself rejects a
            // mismatch, and fdr's consumer double-checks it against the payload's service field
            message.getMessageProperties().setUserId(rabbitmqUsername);
            rabbitTemplate.send(RabbitMQConfig.AUDIT_EXCHANGE, routingKey(action), message, new CorrelationData(json));
        } catch (Exception ex) {
            log.warn("Failed to publish audit event action={}, writing to fallback file", action, ex);
            appendFallback(event);
        }
    }

    // Async nack - the send() call itself succeeded, but the broker rejected the message afterward
    private void onConfirm(CorrelationData correlationData, boolean ack, String cause) {
        if (!ack && correlationData != null) {
            log.warn("Audit event nacked by broker: {}", cause);
            appendFallbackJson(correlationData.getId());
        }
    }

    // Mandatory + unroutable - no queue/binding exists for the routing key used
    private void onReturn(ReturnedMessage returned) {
        log.warn("Audit event returned undeliverable, exchange={}, routingKey={}", returned.getExchange(), returned.getRoutingKey());
        appendFallbackJson(new String(returned.getMessage().getBody(), StandardCharsets.UTF_8));
    }

    private void appendFallback(AuditEvent event) {
        try {
            appendFallbackJson(objectMapper.writeValueAsString(event));
        } catch (Exception ex) {
            log.warn("Failed to serialize audit event for fallback file", ex);
        }
    }

    private void appendFallbackJson(String json) {
        try {
            Files.createDirectories(fallbackFile.getParent());
            Files.writeString(fallbackFile, json + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            log.warn("Failed to write audit fallback file", ex);
        }
    }

    private String currentTraceId() {
        var span = tracer.currentSpan();
        return span == null ? null : span.context().traceId();
    }

    private String routingKey(String action) {
        return SERVICE_NAME + "." + action.toLowerCase(Locale.ROOT);
    }
}
