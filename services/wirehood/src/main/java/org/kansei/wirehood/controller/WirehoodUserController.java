package org.kansei.wirehood.controller;

import org.kansei.wirehood.model.WirehoodUser;
import org.kansei.wirehood.service.WirehoodUserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/wirehood/users")
public class WirehoodUserController {

    private final WirehoodUserService wirehoodUserService;

    public WirehoodUserController(WirehoodUserService wirehoodUserService) {
        this.wirehoodUserService = wirehoodUserService;
    }

    /**
     * Called once the frontend's "would you like to use wirehood?" popup is confirmed
     */
    @PostMapping("/opt-in")
    public Mono<WirehoodUser> optIn(@RequestHeader("X-User-Id") UUID userId) {
        return wirehoodUserService.optIn(userId);
    }

    // Re-checks role/enabled against the DB - see WirehoodUserService.me
    @GetMapping("/me")
    public Mono<WirehoodUser> me(@RequestHeader("X-User-Id") UUID userId) {
        return wirehoodUserService.me(userId);
    }

    // No real self-service disable exists yet - emails an admin inbox instead, see WirehoodUserService.requestDisable
    @PostMapping("/me/disable-request")
    public Mono<Void> requestDisable(@RequestHeader("X-User-Id") UUID userId) {
        return wirehoodUserService.requestDisable(userId);
    }

    // Admin-only - kicks a user off wirehood
    @PostMapping("/{targetUserId}/disable")
    public Mono<Void> disable(@RequestHeader("X-User-Id") UUID userId, @PathVariable UUID targetUserId) {
        return wirehoodUserService.disable(targetUserId, userId);
    }
}
