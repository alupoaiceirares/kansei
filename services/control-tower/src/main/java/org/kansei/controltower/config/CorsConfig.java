package org.kansei.controltower.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Cloud Gateway's own globalcors config (application.properties) only applies to requests
 * routed through the gateway's route-matching mechanism - it never reaches plain @RestController
 * endpoints living directly in this app (e.g. HealthController), since those are dispatched by
 * WebFlux's normal DispatcherHandler, not the gateway's FilteringWebHandler. Confirmed missing
 * entirely on /health/wirehood (curl -i showed zero Access-Control-Allow-Origin), which silently
 * broke the frontend's health-check polling - the browser correctly blocks a cross-origin response
 * with no CORS headers, so the request just looks permanently failed with no server-side error at
 * all. This filter covers every path uniformly, gateway-routed or not, same allowlist either way.
 */
@Configuration
public class CorsConfig {

    @Value("${CORS_ALLOWED_ORIGINS}")
    private String allowedOrigins;

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return new CorsWebFilter(source);
    }
}
