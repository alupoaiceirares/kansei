package org.kansei.wirehood.repository;

import org.kansei.wirehood.model.Track;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface TrackRepository extends ReactiveCrudRepository<Track, UUID> {

    Mono<Track> findByYoutubeVideoId(String youtubeVideoId);

    // Song of the Day pick, readiness lives on track_formats, visible=true excludes anything an admin's hidden from the pool
    // mp3-only pool on purpose - a video-only (mp4) track would have nothing playable in the audio-only Song of the Day player
    // Dedup via an IN subquery, not SELECT DISTINCT - Postgres rejects DISTINCT combined with
    // ORDER BY RANDOM() ("ORDER BY expressions must appear in select list") since DISTINCT would
    // need RANDOM() itself in the select list to dedup on. This shape avoids DISTINCT at the outer
    // level entirely, so a track with two READY formats (mp3 + mp4) still only joins into one row.
    @Query("""
            SELECT t.* FROM tracks t
            WHERE t.visible = true
            AND t.id IN (SELECT DISTINCT tf.track_id FROM track_formats tf WHERE tf.status = 'READY' AND tf.format = 'mp3')
            ORDER BY RANDOM() LIMIT 1
            """)
    Mono<Track> findRandomReadyTrack();
}
