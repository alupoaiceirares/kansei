package org.kansei.wirehood.service;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

/**
 * Short-lived single-use tickets that let a browser open the downloads SSE stream without a real Authorization header (EventSource can't send custom headers)
 * Ticket grants nothing beyond opening that one stream as the issuing user - GETDEL makes the burn atomic.
 */
@Service
public class SseTicketService {

    private static final Duration TTL = Duration.ofSeconds(10);
    private static final String KEY_PREFIX = "wirehood:sse-ticket:";

    private final ReactiveStringRedisTemplate redisTemplate;

    public SseTicketService(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Mono<String> issue(UUID userId) {
        String ticket = UUID.randomUUID().toString();
        return redisTemplate.opsForValue()
                .set(KEY_PREFIX + ticket, userId.toString(), TTL)
                .thenReturn(ticket);
    }

    /**
     * Burns the ticket (GETDEL - atomic read+delete) and resolves it to a user id, if it was valid and unexpired
     */
    public Mono<UUID> burn(String ticket) {
        return redisTemplate.opsForValue()
                .getAndDelete(KEY_PREFIX + ticket)
                .map(UUID::fromString);
    }
}
