package org.kansei.wirehood.service;

import org.kansei.wirehood.client.ShieldwallUserClient;
import org.kansei.wirehood.dto.WirehoodUserResponse;
import org.kansei.wirehood.messaging.AuditPublisher;
import org.kansei.wirehood.model.WirehoodUser;
import org.kansei.wirehood.repository.WirehoodUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WirehoodUserService {

    private final WirehoodUserRepository wirehoodUserRepository;
    private final AdminAuthService adminAuthService;
    private final ShieldwallUserClient shieldwallUserClient;
    private final MailEventPublisher mailEventPublisher;
    private final AuditPublisher auditPublisher;

    public WirehoodUserService(
            WirehoodUserRepository wirehoodUserRepository,
            AdminAuthService adminAuthService,
            ShieldwallUserClient shieldwallUserClient,
            MailEventPublisher mailEventPublisher,
            AuditPublisher auditPublisher
    ) {
        this.wirehoodUserRepository = wirehoodUserRepository;
        this.adminAuthService = adminAuthService;
        this.shieldwallUserClient = shieldwallUserClient;
        this.mailEventPublisher = mailEventPublisher;
        this.auditPublisher = auditPublisher;
    }

    // Backs re-checking role/enabled after opt-in - the frontend only ever learns its role once,
    // at opt-in time, and caches it in localStorage with no refresh path, so a role change made
    // in shieldwall (promoting to ADMIN, requiring re-login) was invisible until now
    public Mono<WirehoodUserResponse> me(UUID userId, String role) {
        return wirehoodUserRepository.findById(userId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Not opted into wirehood")))
                .map(user -> WirehoodUserResponse.of(user, role));
    }

    /**
     * Called when the frontend's popup is confirmed - not triggered automatically on login
     */
    public Mono<WirehoodUserResponse> optIn(UUID userId, String role) {
        return wirehoodUserRepository.findById(userId)
                .switchIfEmpty(Mono.defer(() -> wirehoodUserRepository.save(
                                WirehoodUser.builder()
                                        .userId(userId)
                                        .joinedAt(Instant.now())
                                        .build()
                        )
                        .flatMap(saved -> auditPublisher.publishWithResolvedUsername("OPT_IN", userId, "WIREHOOD_USER", userId.toString(), null)
                                .thenReturn(saved))))
                .map(user -> WirehoodUserResponse.of(user, role));
    }

    // No self-service leave yet (accounts are part of a shared archive) - sends the request to an
    // admin inbox via mail.events instead, same publisher/sender split as shieldwall's own emails
    public Mono<Void> requestDisable(UUID userId) {
        return shieldwallUserClient.resolveUsernames(List.of(userId))
                .doOnNext(usernames -> mailEventPublisher.publishDisableRequest(userId, usernames.getOrDefault(userId, "Unknown user")))
                .then();
    }

    // Wirehood-scoped kick
    public Mono<Void> disable(UUID targetUserId, UUID adminUserId, String adminRole) {
        return adminAuthService.requireAdmin(adminUserId, adminRole)
                .then(wirehoodUserRepository.findById(targetUserId))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Wirehood user not found")))
                .flatMap(target -> {
                    target.setEnabled(false);
                    return wirehoodUserRepository.save(target);
                })
                .then(auditPublisher.publishWithResolvedUsername("DISABLE_USER", adminUserId, "WIREHOOD_USER", targetUserId.toString(), null));
    }
}
