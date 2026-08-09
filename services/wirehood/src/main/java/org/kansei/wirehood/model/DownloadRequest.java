package org.kansei.wirehood.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Per-user "waiting on this download" record - separate from track_formats.status (shared/global per format)
 * because multiple users can each be waiting on the same in-flight download. format matters here too - two
 * users waiting on the same track but different formats must only get acknowledged by their own format finishing.
 */
@Table("download_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DownloadRequest {

    @Id
    private UUID id;

    private UUID userId;

    private UUID trackId;

    private String format;

    private Instant requestedAt;

    private Instant acknowledgedAt;
}
