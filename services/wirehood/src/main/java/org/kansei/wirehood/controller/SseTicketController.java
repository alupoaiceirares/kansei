package org.kansei.wirehood.controller;

import org.kansei.wirehood.dto.SseTicketResponse;
import org.kansei.wirehood.service.SseTicketService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
public class SseTicketController {

    private final SseTicketService sseTicketService;

    public SseTicketController(SseTicketService sseTicketService) {
        this.sseTicketService = sseTicketService;
    }

    @PostMapping("/wirehood/sse-ticket")
    public Mono<SseTicketResponse> issueTicket(@RequestHeader("X-User-Id") UUID userId) {
        return sseTicketService.issue(userId).map(SseTicketResponse::new);
    }
}
