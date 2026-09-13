package org.kansei.wirehood.messaging;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Download job queue - producer (submit-download endpoint) and consumer (yt-dlp/FFmpeg worker, DownloadWorkerService) both live in wirehood itself, so a plain durable queue is enough there, no exchange/routing needed.
 * mail.events is different - wirehood is a producer only (courier-one consumes), same topic exchange shieldwall already declares; declared here too so whichever service starts first doesn't fail, same reasoning as shieldwall's own RabbitMQConfig.
 */
@Configuration
public class RabbitMQConfig {

    public static final String DOWNLOAD_JOBS_QUEUE = "wirehood.download-jobs";
    public static final String MAIL_EXCHANGE = "mail.events";

    @Bean
    public Queue downloadJobsQueue() {
        return new Queue(DOWNLOAD_JOBS_QUEUE, true);
    }

    @Bean
    public TopicExchange mailExchange() {
        return new TopicExchange(MAIL_EXCHANGE);
    }

    @Bean
    public JacksonJsonMessageConverter jacksonJsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, JacksonJsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }
}
