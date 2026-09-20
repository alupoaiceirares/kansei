package org.kansei.tailwind.service;

import lombok.extern.slf4j.Slf4j;
import org.kansei.tailwind.client.ShieldwallUserClient;
import org.kansei.tailwind.dto.TailwindUserResponse;
import org.kansei.tailwind.model.TailwindUser;
import org.kansei.tailwind.repository.TailwindUserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class TailwindUserService {

    // Matches shieldwall's MAX_IDS on the internal lookup
    private static final int RECONCILE_CHUNK_SIZE = 200;

    // A chunk this size where shieldwall knows none of the ids looks like a shieldwall fault, not real purges
    private static final int ANOMALY_MIN_CHUNK_SIZE = 5;

    private final TailwindUserRepository tailwindUserRepository;
    private final UserDataCleaner userDataCleaner;
    private final ShieldwallUserClient shieldwallUserClient;

    public TailwindUserService(TailwindUserRepository tailwindUserRepository, ShieldwallUserClient shieldwallUserClient, UserDataCleaner userDataCleaner) {
        this.tailwindUserRepository = tailwindUserRepository;
        this.userDataCleaner = userDataCleaner;
        this.shieldwallUserClient = shieldwallUserClient;
    }

    public TailwindUserResponse me(UUID userId, String role) {
        TailwindUser user = tailwindUserRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not opted into tailwind"));
        return toResponse(user, role);
    }

    // Called when the frontend popup is confirmed, idempotent
    public TailwindUserResponse optIn(UUID userId, String role) {
        TailwindUser user = tailwindUserRepository.findById(userId).orElseGet(() -> create(userId));
        return toResponse(user, role);
    }

    /**
     * Removes everything tailwind holds for users shieldwall no longer has (purged accounts), see UserDataCleaner
     * for the per-user tables.
     */
    public int purgeOrphanedUsers() {
        List<UUID> allIds = tailwindUserRepository.findAllUserIds();
        int deleted = 0;
        for (int from = 0; from < allIds.size(); from += RECONCILE_CHUNK_SIZE) {
            List<UUID> chunk = allIds.subList(from, Math.min(from + RECONCILE_CHUNK_SIZE, allIds.size()));

            Set<UUID> existing;
            try {
                existing = shieldwallUserClient.findExistingUserIds(chunk);
            } catch (RuntimeException ex) {
                log.warn("account reconcile aborted, shieldwall lookup failed: {}", ex.toString());
                return deleted;
            }

            List<UUID> orphans = chunk.stream().filter(id -> !existing.contains(id)).toList();
            if (orphans.isEmpty()) {
                continue;
            }
            if (chunk.size() >= ANOMALY_MIN_CHUNK_SIZE && orphans.size() == chunk.size()) {
                log.warn("account reconcile skipped a chunk of {}, shieldwall knows none of them", chunk.size());
                continue;
            }
            userDataCleaner.deleteAllFor(orphans);
            deleted += orphans.size();
        }
        if (deleted > 0) {
            log.info("account reconcile removed {} tailwind users whose shieldwall account is gone", deleted);
        }
        return deleted;
    }

    private TailwindUser create(UUID userId) {
        try {
            return tailwindUserRepository.saveAndFlush(TailwindUser.builder().userId(userId).joinedAt(Instant.now()).build());
        } catch (DataIntegrityViolationException ex) {
            // Two opt-in calls raced, the other one won
            return tailwindUserRepository.findById(userId).orElseThrow(() -> ex);
        }
    }

    private TailwindUserResponse toResponse(TailwindUser user, String role) {
        String username = shieldwallUserClient.resolveUsernames(List.of(user.getUserId())).get(user.getUserId());
        return TailwindUserResponse.of(user, username, role);
    }
}
