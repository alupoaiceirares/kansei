package org.kansei.shieldwall.messaging;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

// The wire contract for audit.events - copied into each producer rather than shared as a jar, so a
// producer never depends on fdr and can evolve its own copy independently
public record AuditEvent(
        UUID eventId,
        String service,
        String action,
        UUID actorUserId,
        String actorUsername,
        String targetType,
        String targetId,
        Map<String, Object> details,
        String traceId,
        Instant occurredAt
) {
}
