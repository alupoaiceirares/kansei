package org.kansei.fdr.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.util.Map;

@Component
public class AuditEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuditEventConsumer.class);

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public AuditEventConsumer(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RabbitMQConfig.AUDIT_QUEUE)
    public void onAuditEvent(AuditEvent event, @Header(name = AmqpHeaders.RECEIVED_USER_ID, required = false) String amqpUserId) {
        if (event.eventId() == null || event.service() == null || event.action() == null || event.occurredAt() == null) {
            // Thrown, not swallowed - the listener retry/DLQ setup (application.properties) is what actually handles this, a malformed message has no fix on redelivery so it dead-letters after retries exhaust
            throw new IllegalArgumentException("Audit event missing a required field: " + event);
        }

        // RabbitMQ itself already rejects a user-id property that doesn't match the connection's
        // authenticated user - this catches the remaining case, a service publishing under its own
        // real identity but claiming to be a different one in the payload's `service` field
        if (!event.service().equals(amqpUserId)) {
            throw new IllegalArgumentException("Audit event service=" + event.service() + " doesn't match AMQP user-id=" + amqpUserId);
        }

        String detailsJson = objectMapper.writeValueAsString(event.details() != null ? event.details() : Map.of());

        // ON CONFLICT DO NOTHING - redelivery of an already-stored eventId (broker retry, at-least-once delivery) is a no-op, not an error
        jdbcClient.sql("""
                INSERT INTO audit_log (event_id, service, action, actor_user_id, actor_username, target_type, target_id, details, trace_id, occurred_at)
                VALUES (:eventId, :service, :action, :actorUserId, :actorUsername, :targetType, :targetId, :details::jsonb, :traceId, :occurredAt)
                ON CONFLICT (event_id) DO NOTHING
                """)
                .param("eventId", event.eventId())
                .param("service", event.service())
                .param("action", event.action())
                .param("actorUserId", event.actorUserId())
                .param("actorUsername", event.actorUsername())
                .param("targetType", event.targetType())
                .param("targetId", event.targetId())
                .param("details", detailsJson)
                .param("traceId", event.traceId())
                // Postgres JDBC can't infer a SQL type for java.time.Instant directly - Timestamp is the type it knows how to bind
                .param("occurredAt", Timestamp.from(event.occurredAt()))
                .update();

        log.info("Stored audit event service={} action={} eventId={}", event.service(), event.action(), event.eventId());
    }
}
