package org.kansei.tailwind.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.UUID;

/**
 * Per-user cap on external lookups per hour, so one user cannot burn the whole monthly budget.
 * Only lookups that would really hit the provider are counted, stored flights are free.
 */
@Slf4j
@Component
public class LookupRateLimiter {

    private static final String KEY_PREFIX = "tailwind:lookup-rate:";
    private static final Duration WINDOW = Duration.ofHours(1);

    private final StringRedisTemplate redisTemplate;
    private final int maxPerHour;

    public LookupRateLimiter(StringRedisTemplate redisTemplate, @Value("${tailwind.lookup.max-per-hour}") int maxPerHour) {
        this.redisTemplate = redisTemplate;
        this.maxPerHour = maxPerHour;
    }

    public void checkAndCount(UUID userId) {
        String key = KEY_PREFIX + userId;
        Long count;
        try {
            count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                redisTemplate.expire(key, WINDOW);
            }
        } catch (RuntimeException ex) {
            log.warn("lookup rate counter unavailable, refusing the external lookup: {}", ex.toString());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Flight lookup is temporarily unavailable, add the flight manually");
        }
        if (count == null || count > maxPerHour) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many flight lookups, try again later");
        }
    }
}
