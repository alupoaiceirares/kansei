package org.kansei.wirehood.service;

import org.kansei.wirehood.dto.DownloadEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * In-process pub/sub for download READY/FAILED notifications - one shared multicast sink, each open SSE stream subscribes and filters to its own user id (see DownloadController.stream)
 * Single-instance only, same caveat as SseTicketService before it moved to Redis - fine for now,
 * would need a real broadcast (e.g. Redis pub/sub) once wirehood runs more than one instance, since an event published on instance A would never reach a stream open on B
 */
@Service
public class DownloadEventPublisher {

    private final Sinks.Many<DownloadEvent> sink = Sinks.many().multicast().onBackpressureBuffer();

    public void publish(DownloadEvent event) {
        sink.tryEmitNext(event);
    }

    public Flux<DownloadEvent> events() {
        return sink.asFlux();
    }
}
