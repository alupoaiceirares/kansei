package org.kansei.tailwind.controller;

import jakarta.validation.Valid;
import org.kansei.tailwind.dto.TailwindUserResponse;
import org.kansei.tailwind.dto.UiPreferencesRequest;
import org.kansei.tailwind.dto.UiPreferencesResponse;
import org.kansei.tailwind.dto.UpdateTailwindUserRequest;
import org.kansei.tailwind.service.TailwindUserService;
import org.kansei.tailwind.service.UiPreferencesService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * X-User-Id and X-User-Role are injected by control-tower from the verified JWT, never sent by a client.
 */
@RestController
@RequestMapping("/tailwind/users")
public class TailwindUserController {

    private final TailwindUserService tailwindUserService;
    private final UiPreferencesService uiPreferencesService;

    public TailwindUserController(TailwindUserService tailwindUserService, UiPreferencesService uiPreferencesService) {
        this.tailwindUserService = tailwindUserService;
        this.uiPreferencesService = uiPreferencesService;
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

    // Only applies to flights added from here on
    @PatchMapping("/me")
    public TailwindUserResponse update(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @Valid @RequestBody UpdateTailwindUserRequest request
    ) {
        return tailwindUserService.updateDefaultVisibility(userId, request.defaultVisibility(), role);
    }

    // The user deleting their own log, their shieldwall account is untouched
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteOwnLog(@RequestHeader("X-User-Id") UUID userId) {
        tailwindUserService.deleteOwnLog(userId);
    }

    // Display choices, so the map looks the same on another device
    @GetMapping("/me/preferences")
    public UiPreferencesResponse preferences(@RequestHeader("X-User-Id") UUID userId) {
        return uiPreferencesService.get(userId);
    }

    @PutMapping("/me/preferences")
    public UiPreferencesResponse savePreferences(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody UiPreferencesRequest request
    ) {
        return uiPreferencesService.save(userId, request.preferences());
    }
}
