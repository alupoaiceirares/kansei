package org.kansei.wirehood.controller;

import org.kansei.wirehood.dto.DownloadEvent;
import org.kansei.wirehood.dto.DownloadStatusResponse;
import org.kansei.wirehood.dto.SubmitDownloadRequest;
import org.kansei.wirehood.dto.SubmitDownloadResponse;
import org.kansei.wirehood.service.DownloadEventPublisher;
import org.kansei.wirehood.service.DownloadService;
import org.kansei.wirehood.service.SseTicketService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/wirehood/downloads")
public class DownloadController {

    // Keeps dead/dropped connections from leaking silently
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(15);

    private final DownloadService downloadService;
    private final SseTicketService sseTicketService;
    private final DownloadEventPublisher downloadEventPublisher;

    public DownloadController(
            DownloadService downloadService,
            SseTicketService sseTicketService,
            DownloadEventPublisher downloadEventPublisher
    ) {
        this.downloadService = downloadService;
        this.sseTicketService = sseTicketService;
        this.downloadEventPublisher = downloadEventPublisher;
    }

    @PostMapping
    public Mono<SubmitDownloadResponse> submit(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestBody SubmitDownloadRequest request
    ) {
        return downloadService.submit(userId, request);
    }

    // Page-load/reconnect badge, complements the SSE stream below (which only pushes while connected) rather than replacing it, so a completed/failed download while offline isn't silently lost
    @GetMapping("/pending")
    public Mono<DownloadStatusResponse> pendingAndFailed(@RequestHeader("X-User-Id") UUID userId) {
        return downloadService.getMyPendingAndFailed(userId);
    }

    // Ticket-based auth since EventSource can't send an Authorization header - burn it here to resolve the user, then scope the stream to that user only (filter server-side, never broadcast + client-filter)
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<DownloadEvent>> stream(@RequestParam String ticket) {
        return sseTicketService.burn(ticket)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired ticket")))
                .flatMapMany(userId -> Flux.merge(
                        downloadEventPublisher.events()
                                .filter(event -> event.userId().equals(userId))
                                .map(event -> ServerSentEvent.builder(event).build()),
                        Flux.interval(HEARTBEAT_INTERVAL)
                                .map(tick -> ServerSentEvent.<DownloadEvent>builder().comment("heartbeat").build())
                ));
    }
}
