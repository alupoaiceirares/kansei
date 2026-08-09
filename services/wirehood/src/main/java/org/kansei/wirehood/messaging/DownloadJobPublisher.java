package org.kansei.wirehood.messaging;

import org.kansei.wirehood.model.Track;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class DownloadJobPublisher {

    private final RabbitTemplate rabbitTemplate;

    public DownloadJobPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public Mono<Void> publish(Track track, String format) {
        return Mono.fromRunnable(() -> rabbitTemplate.convertAndSend(
                RabbitMQConfig.DOWNLOAD_JOBS_QUEUE,
                new DownloadJobMessage(track.getId(), track.getYoutubeVideoId(), format)
        ));
    }
}
