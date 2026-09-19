package org.kansei.tailwind.controller;

import org.kansei.tailwind.dto.TailwindUserResponse;
import org.kansei.tailwind.service.TailwindUserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * X-User-Id and X-User-Role are injected by control-tower from the verified JWT, never sent by a client.
 */
@RestController
@RequestMapping("/tailwind/users")
public class TailwindUserController {

    private final TailwindUserService tailwindUserService;

    public TailwindUserController(TailwindUserService tailwindUserService) {
        this.tailwindUserService = tailwindUserService;
    }

    // Called once the frontend's "would you like to use tailwind?" popup is confirmed
    @PostMapping("/opt-in")
    public TailwindUserResponse optIn(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role
    ) {
        return tailwindUserService.optIn(userId, role);
    }

    // 404 means the user has not opted in yet
    @GetMapping("/me")
    public TailwindUserResponse me(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role
    ) {
        return tailwindUserService.me(userId, role);
    }
}
