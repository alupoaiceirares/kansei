package org.kansei.tailwind.controller;

import org.kansei.tailwind.dto.AdminUserResult;
import org.kansei.tailwind.service.AdminUserService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Admin-only user lookup, disable and reinstatement, the gate and the audit trail live in AdminUserService.
 */
@RestController
@RequestMapping("/tailwind/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping("/search")
    public List<AdminUserResult> search(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam String query
    ) {
        return adminUserService.search(userId, role, query);
    }

    @GetMapping("/disabled")
    public List<AdminUserResult> disabled(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role
    ) {
        return adminUserService.listDisabled(userId, role);
    }

    @PostMapping("/{targetUserId}/enable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void enable(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable UUID targetUserId
    ) {
        adminUserService.enable(userId, role, targetUserId);
    }

    // Closes tailwind to the user and hides their log from everyone, nothing is deleted
    @PostMapping("/{targetUserId}/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disable(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable UUID targetUserId
    ) {
        adminUserService.disable(userId, role, targetUserId);
    }
}
