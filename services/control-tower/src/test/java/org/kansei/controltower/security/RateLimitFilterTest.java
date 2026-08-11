package org.kansei.controltower.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock
    private GatewayFilterChain chain;
    @Mock
    private ReactiveStringRedisTemplate redisTemplate;
    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter(redisTemplate, new ObjectMapper(), 5, 60);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void underLimit_forwardsRequest() {
        when(chain.filter(any())).thenReturn(Mono.empty());
        when(valueOperations.increment(anyString())).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/wirehood/search"));

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void firstRequestInWindow_setsExpiry() {
        when(chain.filter(any())).thenReturn(Mono.empty());
        when(valueOperations.increment(anyString())).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/wirehood/search"));

        filter.filter(exchange, chain).block();

        verify(redisTemplate).expire(any(), any(Duration.class));
    }

    @Test
    void subsequentRequestInWindow_doesNotResetExpiry() {
        when(chain.filter(any())).thenReturn(Mono.empty());
        when(valueOperations.increment(anyString())).thenReturn(Mono.just(2L));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/wirehood/search"));

        filter.filter(exchange, chain).block();

        verify(redisTemplate, never()).expire(any(), any(Duration.class));
        verify(chain).filter(any());
    }

    @Test
    void overLimit_returns429WithRetryAfter() {
        when(valueOperations.increment(anyString())).thenReturn(Mono.just(6L));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/wirehood/search"));

        filter.filter(exchange, chain).block();

        verify(chain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(exchange.getResponse().getHeaders().getFirst("Retry-After")).isEqualTo("60");
        String body = exchange.getResponse().getBodyAsString().block();
        assertThat(body).contains("\"status\":429").contains("Too many requests");
    }

    @Test
    void differentIps_trackedIndependently() {
        when(chain.filter(any())).thenReturn(Mono.empty());
        when(valueOperations.increment("control-tower:rate-limit:1.1.1.1")).thenReturn(Mono.just(6L));
        when(valueOperations.increment("control-tower:rate-limit:2.2.2.2")).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        MockServerWebExchange overLimitExchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/wirehood/search").header("X-Forwarded-For", "1.1.1.1"));
        MockServerWebExchange underLimitExchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/wirehood/search").header("X-Forwarded-For", "2.2.2.2"));

        filter.filter(overLimitExchange, chain).block();
        filter.filter(underLimitExchange, chain).block();

        assertThat(overLimitExchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(underLimitExchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void redisUnreachable_failsOpenAndForwardsRequest() {
        when(chain.filter(any())).thenReturn(Mono.empty());
        when(valueOperations.increment(anyString())).thenReturn(Mono.error(new RuntimeException("connection refused")));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/wirehood/search"));

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }
}
