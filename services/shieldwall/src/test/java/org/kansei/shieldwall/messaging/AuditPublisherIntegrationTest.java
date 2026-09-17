package org.kansei.shieldwall.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kansei.shieldwall.service.AuditPublisher;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the actual wire contract against a real broker: exchange, routing key, AMQP user-id
 * property (checked by fdr's consumer against the payload's service field), and JSON shape.
 */
@Testcontainers
class AuditPublisherIntegrationTest {

    @Container
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3-management-alpine");

    private static final String QUEUE_NAME = "test.audit.events";

    private CachingConnectionFactory connectionFactory;
    private RabbitTemplate rabbitTemplate;
    private AuditPublisher auditPublisher;

    @BeforeEach
    void setUp() {
        connectionFactory = new CachingConnectionFactory(rabbitmq.getHost(), rabbitmq.getAmqpPort());
        connectionFactory.setUsername(rabbitmq.getAdminUsername());
        connectionFactory.setPassword(rabbitmq.getAdminPassword());
        connectionFactory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        connectionFactory.setPublisherReturns(true);

        // Same production topology/converter setup as RabbitMQConfig - no re-implementation
        RabbitMQConfig config = new RabbitMQConfig();
        TopicExchange exchange = config.auditExchange();
        rabbitTemplate = config.rabbitTemplate(connectionFactory, config.jacksonJsonMessageConverter());
        rabbitTemplate.setMandatory(true);

        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        admin.declareExchange(exchange);
        Queue queue = new Queue(QUEUE_NAME, false, false, false);
        admin.declareQueue(queue);
        admin.declareBinding(BindingBuilder.bind(queue).to(exchange).with("#"));

        auditPublisher = new AuditPublisher(rabbitTemplate, io.micrometer.tracing.Tracer.NOOP, new tools.jackson.databind.ObjectMapper(),
                rabbitmq.getAdminUsername(), System.getProperty("java.io.tmpdir") + "/audit-fallback-test.jsonl");
    }

    @AfterEach
    void tearDown() {
        connectionFactory.destroy();
    }

    @Test
    void publish_landsOnQueue_withExpectedJsonShapeAndUserId() throws Exception {
        UUID actorId = UUID.randomUUID();
        auditPublisher.publish("LOGIN", actorId, "someuser", "USER", actorId.toString(), Map.of("k", "v"));

        var message = rabbitTemplate.receive(QUEUE_NAME, 5000);
        assertThat(message).isNotNull();
        assertThat(message.getMessageProperties().getReceivedRoutingKey()).isEqualTo("shieldwall.login");
        assertThat(message.getMessageProperties().getReceivedUserId()).isEqualTo(rabbitmq.getAdminUsername());

        JsonNode json = new ObjectMapper().readTree(message.getBody());
        assertThat(json.get("service").asText()).isEqualTo("shieldwall");
        assertThat(json.get("action").asText()).isEqualTo("LOGIN");
        assertThat(json.get("actorUserId").asText()).isEqualTo(actorId.toString());
        assertThat(json.get("actorUsername").asText()).isEqualTo("someuser");
        assertThat(json.get("details").get("k").asText()).isEqualTo("v");
    }

    @Test
    void publish_nullDetails_sendsEmptyObjectNotNull() throws Exception {
        auditPublisher.publish("LOGOUT", UUID.randomUUID(), "someuser", "USER", "id", null);

        var message = rabbitTemplate.receive(QUEUE_NAME, 5000);
        assertThat(message).isNotNull();

        JsonNode json = new ObjectMapper().readTree(message.getBody());
        assertThat(json.get("details").isObject()).isTrue();
        assertThat(json.get("details")).isEmpty();
    }
}
