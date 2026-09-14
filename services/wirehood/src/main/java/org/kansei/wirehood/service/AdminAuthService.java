package org.kansei.wirehood.service;

import org.kansei.wirehood.model.WirehoodUser;
import org.kansei.wirehood.repository.WirehoodUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Shared admin-gate check, so this is a plain service-layer helper called explicitly at the top of each admin-gated action
 * Role comes from the X-User-Role header control-tower injects from the verified JWT, not a wirehood-owned column - a wirehood_users row must still exist though, same as before
 */
@Service
public class AdminAuthService {

    private static final String ADMIN_ROLE = "ADMIN";

    private final WirehoodUserRepository wirehoodUserRepository;

    public AdminAuthService(WirehoodUserRepository wirehoodUserRepository) {
        this.wirehoodUserRepository = wirehoodUserRepository;
    }

    public Mono<WirehoodUser> requireAdmin(UUID userId, String role) {
        return wirehoodUserRepository.findById(userId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a wirehood user")))
                .flatMap(user -> ADMIN_ROLE.equals(role)
                        ? Mono.just(user)
                        : Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin only")));
    }
}
