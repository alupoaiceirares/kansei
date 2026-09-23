package org.kansei.tailwind.service;

import org.kansei.tailwind.client.ShieldwallUserClient;
import org.kansei.tailwind.dto.AdminUserResult;
import org.kansei.tailwind.model.TailwindUser;
import org.kansei.tailwind.repository.TailwindUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Admin lookup of tailwind users, the tailwind-scoped disable and its undo. The shieldwall login is never touched.
 */
@Service
public class AdminUserService {

    private static final int MIN_QUERY_LENGTH = 2;
    private static final int SEARCH_LIMIT = 20;

    private final AdminAuthService adminAuthService;
    private final TailwindUserRepository tailwindUserRepository;
    private final ShieldwallUserClient shieldwallUserClient;
    private final AuditPublisher auditPublisher;
    private final Clock clock;

    public AdminUserService(AdminAuthService adminAuthService, TailwindUserRepository tailwindUserRepository,
                            ShieldwallUserClient shieldwallUserClient, AuditPublisher auditPublisher, Clock clock) {
        this.adminAuthService = adminAuthService;
        this.tailwindUserRepository = tailwindUserRepository;
        this.shieldwallUserClient = shieldwallUserClient;
        this.auditPublisher = auditPublisher;
        this.clock = clock;
    }

    public List<AdminUserResult> search(UUID adminId, String role, String query) {
        adminAuthService.requireAdmin(adminId, role);
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.length() < MIN_QUERY_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "query must be at least " + MIN_QUERY_LENGTH + " characters");
        }
        List<ShieldwallUserClient.UserMatch> matches = shieldwallUserClient.searchUsers(trimmed, SEARCH_LIMIT);
        Map<UUID, TailwindUser> users = tailwindUserRepository.findAllById(matches.stream().map(ShieldwallUserClient.UserMatch::id).toList())
                .stream().collect(Collectors.toMap(TailwindUser::getUserId, Function.identity()));
        return matches.stream()
                .filter(match -> users.containsKey(match.id()))
                .map(match -> toResult(users.get(match.id()), match.username()))
                .toList();
    }

    // Everyone currently disabled, newest first, so a reinstatement request can be found without knowing the spelling
    public List<AdminUserResult> listDisabled(UUID adminId, String role) {
        adminAuthService.requireAdmin(adminId, role);
        List<TailwindUser> disabled = tailwindUserRepository.findByEnabledFalseOrderByDisabledAtDesc();
        Map<UUID, String> names = disabled.isEmpty() ? Map.of()
                : shieldwallUserClient.resolveUsernames(disabled.stream().map(TailwindUser::getUserId).toList());
        return disabled.stream().map(user -> toResult(user, names.get(user.getUserId()))).toList();
    }

    public void disable(UUID adminId, String role, UUID targetUserId) {
        adminAuthService.requireAdmin(adminId, role);
        if (adminId.equals(targetUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot disable yourself");
        }
        TailwindUser target = requireUser(targetUserId);
        if (target.isEnabled()) {
            target.setEnabled(false);
            target.setDisabledAt(clock.instant());
            tailwindUserRepository.save(target);
        }
        auditPublisher.publishWithResolvedUsername("DISABLE_USER", adminId, "TAILWIND_USER", targetUserId.toString(), Map.of());
    }

    // Reinstates a disabled user, their log was never touched so everything is back as it was
    public void enable(UUID adminId, String role, UUID targetUserId) {
        adminAuthService.requireAdmin(adminId, role);
        TailwindUser target = requireUser(targetUserId);
        target.setEnabled(true);
        target.setDisabledAt(null);
        tailwindUserRepository.save(target);
        auditPublisher.publishWithResolvedUsername("ENABLE_USER", adminId, "TAILWIND_USER", targetUserId.toString(), Map.of());
    }

    private TailwindUser requireUser(UUID userId) {
        return tailwindUserRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tailwind user not found"));
    }

    private static AdminUserResult toResult(TailwindUser user, String username) {
        return new AdminUserResult(user.getUserId(), username, user.isEnabled(), user.getJoinedAt(), user.getDisabledAt());
    }
}
