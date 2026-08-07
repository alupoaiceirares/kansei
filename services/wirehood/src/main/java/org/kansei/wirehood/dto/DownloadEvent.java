package org.kansei.wirehood.dto;

import org.kansei.wirehood.model.TrackStatus;

import java.util.UUID;

/**
 * Pushed to a user's SSE stream when a track they're waiting on flips to READY or FAILED - see DownloadEventPublisher
 */
public record DownloadEvent(UUID userId, UUID trackId, TrackStatus status) {
}
