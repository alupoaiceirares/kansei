package org.kansei.controltower.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.SecretKey;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final String SECRET = Base64.getEncoder().encodeToString(bytes(32, (byte) 1));
    private static final SecretKey SIGNING_KEY = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
    private static final SecretKey OTHER_KEY = Keys.hmacShaKeyFor(bytes(32, (byte) 2));

    @Mock
    private GatewayFilterChain chain;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(SECRET, new ObjectMapper());
    }

    @Test
    void publicPath_bypassesAuth_andStripsSpoofedUserIdHeader() {
        when(chain.filter(any())).thenReturn(Mono.empty());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/login").header("X-User-Id", "spoofed-id"));

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void protectedPath_missingAuthHeader_returns401WithBody() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/wirehood/tracks"));

        filter.filter(exchange, chain).block();

        verify(chain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        String body = exchange.getResponse().getBodyAsString().block();
        assertThat(body).contains("\"status\":401").contains("Missing or invalid Authorization header");
    }

    @Test
    void protectedPath_malformedAuthHeader_returns401() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/wirehood/tracks").header("Authorization", "not-a-bearer-token"));

        filter.filter(exchange, chain).block();

        verify(chain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void protectedPath_invalidSignature_returns401WithInvalidTokenMessage() {
        String token = Jwts.builder()
                .subject("11111111-1111-1111-1111-111111111111")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(OTHER_KEY)
                .compact();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/wirehood/tracks").header("Authorization", "Bearer " + token));

        filter.filter(exchange, chain).block();

        verify(chain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        String body = exchange.getResponse().getBodyAsString().block();
        assertThat(body).contains("Invalid or expired token");
    }

    @Test
    void protectedPath_expiredToken_returns401WithInvalidTokenMessage() {
        String token = Jwts.builder()
                .subject("11111111-1111-1111-1111-111111111111")
                .expiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(SIGNING_KEY)
                .compact();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/wirehood/tracks").header("Authorization", "Bearer " + token));

        filter.filter(exchange, chain).block();

        verify(chain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        String body = exchange.getResponse().getBodyAsString().block();
        assertThat(body).contains("Invalid or expired token");
    }

    @Test
    void protectedPath_validToken_forwardsRequestWithUserIdHeader() {
        when(chain.filter(any())).thenReturn(Mono.empty());
        String userId = "11111111-1111-1111-1111-111111111111";
        String token = Jwts.builder()
                .subject(userId)
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(SIGNING_KEY)
                .compact();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/wirehood/tracks").header("Authorization", "Bearer " + token));

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    private static byte[] bytes(int length, byte fill) {
        byte[] result = new byte[length];
        Arrays.fill(result, fill);
        return result;
    }
}
