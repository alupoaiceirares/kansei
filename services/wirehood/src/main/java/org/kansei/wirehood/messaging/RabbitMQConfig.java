package org.kansei.wirehood.messaging;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Download job queue - producer (submit-download endpoint) and consumer (yt-dlp/FFmpeg worker, not built yet) both live in wirehood itself, unlike mail.events so a plain durable queue is enough, no exchange/routing needed
 */
@Configuration
public class RabbitMQConfig {

    public static final String DOWNLOAD_JOBS_QUEUE = "wirehood.download-jobs";

    @Bean
    public Queue downloadJobsQueue() {
        return new Queue(DOWNLOAD_JOBS_QUEUE, true);
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
