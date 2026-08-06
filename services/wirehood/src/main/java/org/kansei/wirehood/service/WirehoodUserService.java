package org.kansei.wirehood.service;

import org.kansei.wirehood.model.WirehoodUser;
import org.kansei.wirehood.repository.WirehoodUserRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Service
public class WirehoodUserService {

    private final WirehoodUserRepository wirehoodUserRepository;

    public WirehoodUserService(WirehoodUserRepository wirehoodUserRepository) {
        this.wirehoodUserRepository = wirehoodUserRepository;
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
}
