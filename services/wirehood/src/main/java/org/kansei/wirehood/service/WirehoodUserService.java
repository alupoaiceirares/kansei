package org.kansei.wirehood.service;

import org.kansei.wirehood.client.ShieldwallUserClient;
import org.kansei.wirehood.model.WirehoodUser;
import org.kansei.wirehood.repository.WirehoodUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class WirehoodUserService {

    private final WirehoodUserRepository wirehoodUserRepository;
    private final AdminAuthService adminAuthService;
    private final ShieldwallUserClient shieldwallUserClient;
    private final MailEventPublisher mailEventPublisher;

    public WirehoodUserService(
            WirehoodUserRepository wirehoodUserRepository,
            AdminAuthService adminAuthService,
            ShieldwallUserClient shieldwallUserClient,
            MailEventPublisher mailEventPublisher
    ) {
        this.wirehoodUserRepository = wirehoodUserRepository;
        this.adminAuthService = adminAuthService;
        this.shieldwallUserClient = shieldwallUserClient;
        this.mailEventPublisher = mailEventPublisher;
    }

    // Backs re-checking role/enabled after opt-in - the frontend only ever learns its role once,
    // at opt-in time, and caches it in localStorage with no refresh path, so a role change made
    // directly in the DB (e.g. promoting to ADMIN) was invisible until now
    public Mono<WirehoodUser> me(UUID userId) {
        return wirehoodUserRepository.findById(userId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Not opted into wirehood")));
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

    // No self-service leave yet (accounts are part of a shared archive) - sends the request to an
    // admin inbox via mail.events instead, same publisher/sender split as shieldwall's own emails
    public Mono<Void> requestDisable(UUID userId) {
        return shieldwallUserClient.resolveUsernames(List.of(userId))
                .doOnNext(usernames -> mailEventPublisher.publishDisableRequest(userId, usernames.getOrDefault(userId, "Unknown user")))
                .then();
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
