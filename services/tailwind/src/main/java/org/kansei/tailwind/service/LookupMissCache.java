package org.kansei.tailwind.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;

/**
 * Remembers flights the provider had nothing for, so retyping the same wrong number does not spend
 * quota again. Any Redis trouble just means no memory, never an error.
 */
@Slf4j
@Component
public class LookupMissCache {

    private static final String KEY_PREFIX = "tailwind:lookup-miss:";

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    public LookupMissCache(StringRedisTemplate redisTemplate, @Value("${tailwind.lookup.miss-cache-minutes}") long missCacheMinutes) {
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.ofMinutes(missCacheMinutes);
    }

    public boolean isKnownMiss(String flightNumber, LocalDate date) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key(flightNumber, date)));
        } catch (RuntimeException ex) {
            log.warn("lookup miss cache read failed: {}", ex.toString());
            return false;
        }
    }

    public void remember(String flightNumber, LocalDate date) {
        try {
            redisTemplate.opsForValue().set(key(flightNumber, date), "1", ttl);
        } catch (RuntimeException ex) {
            log.warn("lookup miss cache write failed: {}", ex.toString());
        }
    }

    private static String key(String flightNumber, LocalDate date) {
        return KEY_PREFIX + flightNumber + ":" + date;
    }
}
