package org.kansei.tailwind.service;

import org.kansei.tailwind.model.TailwindUser;
import org.kansei.tailwind.repository.TailwindUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Writing flights and journeys needs a tailwind_users row, the opt-in call creates it.
 */
@Component
public class OptInGuard {

    private final TailwindUserRepository tailwindUserRepository;

    public OptInGuard(TailwindUserRepository tailwindUserRepository) {
        this.tailwindUserRepository = tailwindUserRepository;
    }

    public TailwindUser require(UUID userId) {
        return tailwindUserRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Not opted into tailwind"));
    }
}
