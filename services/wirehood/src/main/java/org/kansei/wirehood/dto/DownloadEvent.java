package org.kansei.wirehood.dto;

import org.kansei.wirehood.model.TrackFormatStatus;

import java.util.UUID;

/**
 * Pushed to a user's SSE stream when a (track, format) they're waiting on flips to READY or FAILED - see
 * DownloadEventPublisher. format included since a user can have two pending requests on the same track
 * (different formats) at once - without it there'd be no way to tell which one this event is about.
 */
public record DownloadEvent(UUID userId, UUID trackId, String format, TrackFormatStatus status) {
}
