package org.kansei.tailwind.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneOffset;

/**
 * Monthly unit budget for the flight data provider, counted in Redis (one key per calendar month).
 * Refuses new external lookups once the guarded share of the plan cap is used, instead of getting
 * billed or hard-blocked mid month. Fails closed when Redis is down, manual entry still works.
 */
@Slf4j
@Component
public class LookupQuotaGuard {

    private static final String KEY_PREFIX = "tailwind:aerodatabox:units:";
    private static final long EXPIRY_GRACE_DAYS = 2;

    private final StringRedisTemplate redisTemplate;
    private final long guardedLimit;
    private final int unitsPerLookup;
    private final Clock clock;

    @Autowired
    public LookupQuotaGuard(
            StringRedisTemplate redisTemplate,
            @Value("${aerodatabox.monthly-unit-cap}") int monthlyUnitCap,
            @Value("${aerodatabox.quota-guard-percent}") int guardPercent,
            @Value("${aerodatabox.units-per-lookup}") int unitsPerLookup
    ) {
        this(redisTemplate, monthlyUnitCap, guardPercent, unitsPerLookup, Clock.systemUTC());
    }

    LookupQuotaGuard(StringRedisTemplate redisTemplate, int monthlyUnitCap, int guardPercent, int unitsPerLookup, Clock clock) {
        this.redisTemplate = redisTemplate;
        this.guardedLimit = (long) monthlyUnitCap * guardPercent / 100;
        this.unitsPerLookup = unitsPerLookup;
        this.clock = clock;
    }

    // Reserves the cost of one external lookup before it is made, the units are not handed back if the call then fails
    public void reserveLookup() {
        YearMonth month = YearMonth.now(clock);
        String key = KEY_PREFIX + month;
        Long used;
        try {
            used = redisTemplate.opsForValue().increment(key, unitsPerLookup);
            redisTemplate.expireAt(key, month.plusMonths(1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC).plusSeconds(EXPIRY_GRACE_DAYS * 86_400));
        } catch (RuntimeException ex) {
            log.warn("lookup quota counter unavailable, refusing the external lookup: {}", ex.toString());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Flight lookup is temporarily unavailable, add the flight manually");
        }
        if (used == null || used > guardedLimit) {
            release(key);
            log.warn("monthly flight lookup budget reached, refusing external lookups until next month");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "The monthly flight lookup budget is used up, add the flight manually");
        }
    }

    private void release(String key) {
        try {
            redisTemplate.opsForValue().decrement(key, unitsPerLookup);
        } catch (RuntimeException ex) {
            log.warn("could not hand back a refused quota reservation: {}", ex.toString());
        }
    }

    long unitsUsedThisMonth() {
        String value = redisTemplate.opsForValue().get(KEY_PREFIX + YearMonth.now(clock));
        return value == null ? 0 : Long.parseLong(value);
    }
}
