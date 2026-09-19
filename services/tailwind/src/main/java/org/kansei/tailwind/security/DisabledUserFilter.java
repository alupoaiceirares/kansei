package org.kansei.tailwind.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.kansei.tailwind.repository.TailwindUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Rejects requests from a disabled tailwind user with 403. Only acts when X-User-Id is present,
 * and a user with no tailwind_users row (never opted in) passes through, each endpoint handles that itself.
 */
@Component
public class DisabledUserFilter extends OncePerRequestFilter {

    private final TailwindUserRepository tailwindUserRepository;

    public DisabledUserFilter(TailwindUserRepository tailwindUserRepository) {
        this.tailwindUserRepository = tailwindUserRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("X-User-Id");
        if (header != null && isDisabled(header)) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"status\":403,\"message\":\"Account disabled\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isDisabled(String header) {
        UUID userId;
        try {
            userId = UUID.fromString(header);
        } catch (IllegalArgumentException ex) {
            return false; // malformed id is not this filter's job, downstream rejects it
        }
        return tailwindUserRepository.findById(userId).map(user -> !user.isEnabled()).orElse(false);
    }
}
