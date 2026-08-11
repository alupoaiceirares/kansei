package org.kansei.shieldwall.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kansei.shieldwall.model.User;

import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    // 32 random bytes, base64-encoded - satisfies HS256's minimum key length
    private static final String SECRET = Base64.getEncoder().encodeToString(
            "01234567890123456789012345678901".getBytes());

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, 60_000);
    }

    private User user(int credentialsVersion) {
        return User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .username("someuser")
                .password("hashed")
                .credentialsVersion(credentialsVersion)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    void generateToken_roundTripsUserIdUsernameAndCredentialsVersion() {
        User user = user(3);

        String token = jwtService.generateToken(user);

        assertThat(jwtService.extractUserId(token)).isEqualTo(user.getId());
        assertThat(jwtService.extractUsername(token)).isEqualTo(user.getUsername());
        assertThat(jwtService.extractCredentialsVersion(token)).isEqualTo(3);
    }

    @Test
    void generateToken_eachTokenGetsAUniqueJti() {
        User user = user(0);

        String jtiOne = jwtService.extractJti(jwtService.generateToken(user));
        String jtiTwo = jwtService.extractJti(jwtService.generateToken(user));

        assertThat(jtiOne).isNotBlank().isNotEqualTo(jtiTwo);
    }

    @Test
    void extractExpiration_matchesConfiguredExpirationMs() {
        String token = jwtService.generateToken(user(0));

        Instant expiresAt = jwtService.extractExpiration(token);

        assertThat(expiresAt).isCloseTo(Instant.now().plusMillis(60_000), org.assertj.core.api.Assertions.within(2, java.time.temporal.ChronoUnit.SECONDS));
    }

    @Test
    void isTokenValid_freshlyIssuedToken_isValid() {
        String token = jwtService.generateToken(user(0));

        assertThat(jwtService.isTokenValid(token)).isTrue();
    }

    @Test
    void isTokenValid_garbageString_isInvalid() {
        assertThat(jwtService.isTokenValid("not-a-jwt-at-all")).isFalse();
    }

    @Test
    void isTokenValid_tamperedSignature_isInvalid() {
        String token = jwtService.generateToken(user(0));
        // Flip the last character of the signature segment.
        String tampered = token.substring(0, token.length() - 1) + (token.endsWith("a") ? "b" : "a");

        assertThat(jwtService.isTokenValid(tampered)).isFalse();
    }

    @Test
    void isTokenValid_expiredToken_isInvalid() throws InterruptedException {
        JwtService shortLived = new JwtService(SECRET, 1);
        String token = shortLived.generateToken(user(0));
        Thread.sleep(20);

        assertThat(shortLived.isTokenValid(token)).isFalse();
    }

    @Test
    void differentSigningKey_rejectsToken() {
        String token = jwtService.generateToken(user(0));
        String otherSecret = Base64.getEncoder().encodeToString("abcdefghijabcdefghijabcdefghij12".getBytes());
        JwtService otherService = new JwtService(otherSecret, 60_000);

        assertThat(otherService.isTokenValid(token)).isFalse();
    }
}
