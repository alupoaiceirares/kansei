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
 * One row per format/quality combo actually downloaded for a track (e.g. mp3/320kbps, mp4/1080p).
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
}
