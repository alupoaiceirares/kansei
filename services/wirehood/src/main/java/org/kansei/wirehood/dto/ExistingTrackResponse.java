package org.kansei.wirehood.dto;

import java.util.List;
import java.util.UUID;

/**
 * Answers "does this youtube_video_id already exist on the platform" for the search confirm popup -
 * lets the frontend grey out the editable title/artist/extra-info fields and show the canonical
 * values instead, since an existing track's own columns always win over a resubmitted request's
 * (see SubmitDownloadRequest's own doc comment).
 */
public record ExistingTrackResponse(boolean exists, UUID trackId, String title, String artist, String extraInfo, List<TrackFormatSummary> formats) {
    public static ExistingTrackResponse notFound() {
        return new ExistingTrackResponse(false, null, null, null, null, List.of());
    }
}
