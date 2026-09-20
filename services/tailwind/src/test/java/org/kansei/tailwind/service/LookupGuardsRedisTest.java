package org.kansei.tailwind.service;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The quota guard, the per-user rate limit and the miss cache against a real Redis.
 */
@Testcontainers
class LookupGuardsRedisTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private static final Clock SEPTEMBER = Clock.fixed(Instant.parse("2026-09-20T10:00:00Z"), ZoneOffset.UTC);

    private StringRedisTemplate template;

    @BeforeEach
    void connect() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(redis.getHost(), redis.getMappedPort(6379)));
        factory.afterPropertiesSet();
        template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
        template.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    private static HttpStatus statusOf(Throwable ex) {
        return (HttpStatus) ((ResponseStatusException) ex).getStatusCode();
    }

    @Test
    void quotaGuardRefusesOnceTheGuardedShareIsUsedAndHandsTheRefusedUnitsBack() {
        // cap 10 at 90 percent leaves 9 units, a lookup costs 2, so 4 fit
        LookupQuotaGuard guard = new LookupQuotaGuard(template, 10, 90, 2, SEPTEMBER);
        for (int i = 0; i < 4; i++) {
            guard.reserveLookup();
        }

        assertThatThrownBy(guard::reserveLookup).isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(statusOf(ex)).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        assertThat(guard.unitsUsedThisMonth()).isEqualTo(8);
    }

    @Test
    void quotaCounterCoversOneCalendarMonthAndExpires() {
        LookupQuotaGuard guard = new LookupQuotaGuard(template, 100, 90, 2, SEPTEMBER);
        guard.reserveLookup();

        assertThat(template.opsForValue().get("tailwind:aerodatabox:units:2026-09")).isEqualTo("2");
        assertThat(template.getExpire("tailwind:aerodatabox:units:2026-09")).isPositive();
    }

    @Test
    void quotaGuardFailsClosedWhenRedisIsUnreachable() {
        LettuceClientConfiguration config = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(1))
                .clientOptions(ClientOptions.builder().socketOptions(SocketOptions.builder().connectTimeout(Duration.ofSeconds(1)).build()).build())
                .build();
        LettuceConnectionFactory dead = new LettuceConnectionFactory(new RedisStandaloneConfiguration("localhost", 1), config);
        dead.afterPropertiesSet();
        StringRedisTemplate deadTemplate = new StringRedisTemplate(dead);
        deadTemplate.afterPropertiesSet();

        LookupQuotaGuard guard = new LookupQuotaGuard(deadTemplate, 100, 90, 2, SEPTEMBER);

        assertThatThrownBy(guard::reserveLookup).isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(statusOf(ex)).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void rateLimiterCapsEachUserSeparately() {
        LookupRateLimiter limiter = new LookupRateLimiter(template, 3);
        UUID busy = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        for (int i = 0; i < 3; i++) {
            limiter.checkAndCount(busy);
        }

        assertThatThrownBy(() -> limiter.checkAndCount(busy)).isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(statusOf(ex)).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        limiter.checkAndCount(other);
        assertThat(template.getExpire("tailwind:lookup-rate:" + busy)).isPositive();
    }

    @Test
    void missCacheRemembersOneNumberAndDateOnly() {
        LookupMissCache cache = new LookupMissCache(template, 60);
        LocalDate day = LocalDate.of(2026, 9, 12);

        assertThat(cache.isKnownMiss("LH400", day)).isFalse();
        cache.remember("LH400", day);

        assertThat(cache.isKnownMiss("LH400", day)).isTrue();
        assertThat(cache.isKnownMiss("LH400", day.plusDays(1))).isFalse();
        assertThat(cache.isKnownMiss("LH401", day)).isFalse();
    }
}
