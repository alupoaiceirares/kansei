package org.kansei.wirehood.service;

import org.kansei.wirehood.model.WirehoodUser;
import org.kansei.wirehood.repository.WirehoodUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Service
public class WirehoodUserService {

    private final WirehoodUserRepository wirehoodUserRepository;
    private final AdminAuthService adminAuthService;

    public WirehoodUserService(WirehoodUserRepository wirehoodUserRepository, AdminAuthService adminAuthService) {
        this.wirehoodUserRepository = wirehoodUserRepository;
        this.adminAuthService = adminAuthService;
    }

    /**
     * Called when the frontend's popup is confirmed - not triggered automatically on login
     */
    public Mono<WirehoodUser> optIn(UUID userId) {
        return wirehoodUserRepository.findById(userId)
                .switchIfEmpty(Mono.defer(() -> wirehoodUserRepository.save(
                        WirehoodUser.builder()
                                .userId(userId)
                                .joinedAt(Instant.now())
                                .build()
                )));
    }

    // Wirehood-scoped kick
    public Mono<Void> disable(UUID targetUserId, UUID adminUserId) {
        return adminAuthService.requireAdmin(adminUserId)
                .then(wirehoodUserRepository.findById(targetUserId))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Wirehood user not found")))
                .flatMap(target -> {
                    target.setEnabled(false);
                    return wirehoodUserRepository.save(target);
                })
                .then();
    }
}
