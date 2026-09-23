package org.kansei.tailwind.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * One manual refresh per flight per cooldown, shared by everyone who logged it since the flight row is shared.
 * Fails closed when Redis is down, same as the lookup guards.
 */
@Slf4j
@Component
public class RefreshCooldown {

    private static final String KEY_PREFIX = "tailwind:flight-refresh:";

    private final StringRedisTemplate redisTemplate;
    private final Duration cooldown;

    public RefreshCooldown(StringRedisTemplate redisTemplate, @Value("${tailwind.flight-refresh.cooldown-minutes}") int cooldownMinutes) {
        this.redisTemplate = redisTemplate;
        this.cooldown = Duration.ofMinutes(cooldownMinutes);
    }

    public void start(Long flightId) {
        String key = KEY_PREFIX + flightId;
        Boolean started;
        Long minutesLeft = null;
        try {
            started = redisTemplate.opsForValue().setIfAbsent(key, "1", cooldown);
            if (!Boolean.TRUE.equals(started)) {
                minutesLeft = redisTemplate.getExpire(key, TimeUnit.MINUTES);
            }
        } catch (RuntimeException ex) {
            log.warn("refresh cooldown unavailable, refusing the refresh: {}", ex.toString());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Refreshing is temporarily unavailable, try again later");
        }
        if (!Boolean.TRUE.equals(started)) {
            long wait = minutesLeft == null || minutesLeft < 0 ? cooldown.toMinutes() : minutesLeft + 1;
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "This flight was refreshed recently, try again in " + wait + (wait == 1 ? " minute" : " minutes"));
        }
    }

    // Hands the cooldown back when a later guard refused, so nothing was spent
    public void cancel(Long flightId) {
        try {
            redisTemplate.delete(KEY_PREFIX + flightId);
        } catch (RuntimeException ex) {
            log.warn("could not clear a refused refresh cooldown: {}", ex.toString());
        }
    }
}
