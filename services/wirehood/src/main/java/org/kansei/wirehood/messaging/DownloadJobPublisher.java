package org.kansei.wirehood.messaging;

import org.kansei.wirehood.model.Track;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class DownloadJobPublisher {

    private final RabbitTemplate rabbitTemplate;

    public DownloadJobPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    // convertAndSend is a blocking call (pooled AMQP connection) - offloaded same as every other
    // blocking call in this service (YtDlpDownloadClient, YtDlpSearchClient), so a slow/stuck
    // RabbitMQ connection doesn't tie up a WebFlux event loop thread
    public Mono<Void> publish(Track track, String format) {
        return Mono.fromRunnable(() -> rabbitTemplate.convertAndSend(
                        RabbitMQConfig.DOWNLOAD_JOBS_QUEUE,
                        new DownloadJobMessage(track.getId(), track.getYoutubeVideoId(), format)
                ))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }
}
