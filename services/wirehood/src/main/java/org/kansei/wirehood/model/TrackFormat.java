package org.kansei.wirehood.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

/**
 * One row per format/quality combo requested for a track (e.g. mp3/audio, mp4/video) - inserted PENDING at
 * request time (see DownloadService), before filePath/fileSizeBytes exist, then updated in place by
 * DownloadWorkerService as the download progresses. UNIQUE(track_id, format) at the DB level.
 */
@Table("track_formats")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrackFormat {

    @Id
    private UUID id;

    private UUID trackId;

    private String format;

    private String quality;

    private String filePath;

    private Long fileSizeBytes;

    @Builder.Default
    private TrackFormatStatus status = TrackFormatStatus.PENDING;
}
