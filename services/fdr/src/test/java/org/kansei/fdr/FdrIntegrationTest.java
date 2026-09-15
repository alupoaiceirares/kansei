package org.kansei.fdr;

import org.junit.jupiter.api.Test;
import org.kansei.fdr.messaging.AuditEvent;
import org.kansei.fdr.messaging.RabbitMQConfig;
import org.kansei.fdr.scheduler.AuditRetentionScheduler;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.TEN_SECONDS;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class FdrIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");
    @Container
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3-management-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", rabbitmq::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitmq::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitmq::getAdminPassword);
        // Fast retry for the DLQ test below - production uses 5 attempts with real backoff (application.properties), this just needs the same reject-on-exhaustion path, quickly
        registry.add("spring.rabbitmq.listener.simple.retry.max-attempts", () -> "2");
        registry.add("spring.rabbitmq.listener.simple.retry.initial-interval", () -> "50");
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private JdbcClient jdbcClient;
    @Autowired
    private AuditRetentionScheduler retentionScheduler;

    private AuditEvent event(UUID eventId) {
        return new AuditEvent(eventId, "shieldwall", "LOGIN", UUID.randomUUID(), "someuser",
                null, null, Map.of("k", "v"), "trace-1", Instant.now());
    }

    private long countRows(UUID eventId) {
        return jdbcClient.sql("SELECT count(*) FROM audit_log WHERE event_id = :id")
                .param("id", eventId).query(Long.class).single();
    }

    @Test
    void validEvent_getsStored() {
        UUID eventId = UUID.randomUUID();
        rabbitTemplate.convertAndSend(RabbitMQConfig.AUDIT_EXCHANGE, "shieldwall.login", event(eventId));

        await().atMost(TEN_SECONDS).untilAsserted(() -> assertThat(countRows(eventId)).isEqualTo(1));
    }

    @Test
    void duplicateEventId_isNoOp() {
        UUID eventId = UUID.randomUUID();
        rabbitTemplate.convertAndSend(RabbitMQConfig.AUDIT_EXCHANGE, "shieldwall.login", event(eventId));
        await().atMost(TEN_SECONDS).untilAsserted(() -> assertThat(countRows(eventId)).isEqualTo(1));

        rabbitTemplate.convertAndSend(RabbitMQConfig.AUDIT_EXCHANGE, "shieldwall.login", event(eventId));
        await().pollDelay(TEN_SECONDS.dividedBy(5)).atMost(TEN_SECONDS)
                .untilAsserted(() -> assertThat(countRows(eventId)).isEqualTo(1));
    }

    @Test
    void malformedEvent_deadLetters() {
        AuditEvent malformed = new AuditEvent(null, "shieldwall", "LOGIN", null, null, null, null, Map.of(), null, Instant.now());
        rabbitTemplate.convertAndSend(RabbitMQConfig.AUDIT_EXCHANGE, "shieldwall.login", malformed);

        var dlqMessage = rabbitTemplate.receive(RabbitMQConfig.DEAD_LETTER_QUEUE, 10_000);
        assertThat(dlqMessage).isNotNull();
    }

    @Test
    void retentionScheduler_deletesOnlyRowsOlderThanRetention() {
        UUID oldId = UUID.randomUUID();
        UUID recentId = UUID.randomUUID();
        insertRow(oldId, Instant.now().minus(400, ChronoUnit.DAYS));
        insertRow(recentId, Instant.now().minus(1, ChronoUnit.DAYS));

        retentionScheduler.purgeExpiredAuditLog();

        assertThat(countRows(oldId)).isZero();
        assertThat(countRows(recentId)).isEqualTo(1);
    }

    private void insertRow(UUID eventId, Instant occurredAt) {
        jdbcClient.sql("""
                INSERT INTO audit_log (event_id, service, action, details, occurred_at)
                VALUES (:eventId, 'shieldwall', 'LOGIN', '{}'::jsonb, :occurredAt)
                """)
                .param("eventId", eventId)
                .param("occurredAt", Timestamp.from(occurredAt))
                .update();
    }
}
