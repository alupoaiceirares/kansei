package org.kansei.shieldwall.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordConfig {

    /**
     * Bcrypt: one-way hashing, salt generated and embedded per-password automatically.
     * Usage later: passwordEncoder.encode(rawPassword) at registration,
     * passwordEncoder.matches(rawPassword, storedHash) at login.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}