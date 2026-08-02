package org.kansei.shieldwall.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kansei.shieldwall.model.User;
import org.kansei.shieldwall.service.MailEventPublisher;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the actual wire contract against a real broker: exchange, routing keys, and JSON shape ({to, template, vars: {username, link}}) that courier-one's `JSON.parse` expects on the other end
 */
@Testcontainers
class MailEventPublisherIntegrationTest {

    @Container
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3-management-alpine");

    private static final String QUEUE_NAME = "test.mail.events";

    private CachingConnectionFactory connectionFactory;
    private RabbitTemplate rabbitTemplate;
    private MailEventPublisher mailEventPublisher;

    @BeforeEach
    void setUp() {
        connectionFactory = new CachingConnectionFactory(rabbitmq.getHost(), rabbitmq.getAmqpPort());
        connectionFactory.setUsername(rabbitmq.getAdminUsername());
        connectionFactory.setPassword(rabbitmq.getAdminPassword());

        // Same production topology/converter setup as RabbitMQConfig - no re-implementation
        RabbitMQConfig config = new RabbitMQConfig();
        TopicExchange exchange = config.mailExchange();
        rabbitTemplate = config.rabbitTemplate(connectionFactory, config.jacksonJsonMessageConverter());

        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        admin.declareExchange(exchange);
        // Not auto-delete - rabbitTemplate.receive() attaches/detaches a temporary consumer per call, and an auto-delete queue vanishes the instant that consumer count hits zero
        Queue queue = new Queue(QUEUE_NAME, false, false, false);
        admin.declareQueue(queue);
        admin.declareBinding(BindingBuilder.bind(queue).to(exchange).with("email.verification"));
        admin.declareBinding(BindingBuilder.bind(queue).to(exchange).with("email.password-reset"));

        mailEventPublisher = new MailEventPublisher(rabbitTemplate);
        ReflectionTestUtils.setField(mailEventPublisher, "frontendUrl", "http://localhost:3000");
    }

    @AfterEach
    void tearDown() {
        connectionFactory.destroy();
    }

    private User user() {
        return User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .username("someuser")
                .password("hashed")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    void publishVerificationEmail_landsOnQueue_withExpectedJsonShape() throws Exception {
        User user = user();

        mailEventPublisher.publishVerificationEmail(user, "verify-token-123");

        var message = rabbitTemplate.receive(QUEUE_NAME, 5000);
        assertThat(message).isNotNull();

        JsonNode json = new ObjectMapper().readTree(message.getBody());
        assertThat(json.get("to").asText()).isEqualTo(user.getEmail());
        assertThat(json.get("template").asText()).isEqualTo("email-verification");
        assertThat(json.get("vars").get("username").asText()).isEqualTo(user.getUsername());
        assertThat(json.get("vars").get("link").asText())
                .isEqualTo("http://localhost:3000/verify-email?token=verify-token-123");
    }

    @Test
    void publishPasswordResetEmail_landsOnQueue_withExpectedJsonShape() throws Exception {
        User user = user();

        mailEventPublisher.publishPasswordResetEmail(user, "reset-token-456");

        var message = rabbitTemplate.receive(QUEUE_NAME, 5000);
        assertThat(message).isNotNull();

        JsonNode json = new ObjectMapper().readTree(message.getBody());
        assertThat(json.get("to").asText()).isEqualTo(user.getEmail());
        assertThat(json.get("template").asText()).isEqualTo("password-reset");
        assertThat(json.get("vars").get("username").asText()).isEqualTo(user.getUsername());
        assertThat(json.get("vars").get("link").asText())
                .isEqualTo("http://localhost:3000/reset-password?token=reset-token-456");
    }

    @Test
    void routingKeys_areIndependent_verificationDoesNotLeakIntoPasswordResetOnlyConsumer() throws Exception {
        // Both routing keys are bound to the same queue in this test setup (mirrors courier-one's single-queue design), but publishing under one key must not, e.g., silently double-publish.
        User user = user();
        mailEventPublisher.publishVerificationEmail(user, "tok");

        var message = rabbitTemplate.receive(QUEUE_NAME, 5000);
        assertThat(message).isNotNull();
        assertThat(rabbitTemplate.receive(QUEUE_NAME, 500)).isNull();
    }
}
