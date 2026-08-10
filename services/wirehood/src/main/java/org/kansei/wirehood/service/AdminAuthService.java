package org.kansei.wirehood.service;

import org.kansei.wirehood.model.WirehoodRole;
import org.kansei.wirehood.model.WirehoodUser;
import org.kansei.wirehood.repository.WirehoodUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Shared admin-gate check, so this is a plain service-layer helper called explicitly at the top of each admin-gated action
 */
@Service
public class AdminAuthService {

    private final WirehoodUserRepository wirehoodUserRepository;

    public AdminAuthService(WirehoodUserRepository wirehoodUserRepository) {
        this.wirehoodUserRepository = wirehoodUserRepository;
    }

    public Mono<WirehoodUser> requireAdmin(UUID userId) {
        return wirehoodUserRepository.findById(userId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a wirehood user")))
                .flatMap(user -> user.getRole() == WirehoodRole.ADMIN
                        ? Mono.just(user)
                        : Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin only")));
    }
}
