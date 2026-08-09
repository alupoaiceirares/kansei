package org.kansei.wirehood.repository;

import org.kansei.wirehood.model.Track;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface TrackRepository extends ReactiveCrudRepository<Track, UUID> {

    Mono<Track> findByYoutubeVideoId(String youtubeVideoId);

    // Song of the Day pick, readiness lives on track_formats, visible=true excludes anything an admin's hidden from the pool
    // DISTINCT since a track with two READY formats (mp3 + mp4) would otherwise join into two rows
    @Query("""
            SELECT DISTINCT t.* FROM tracks t
            JOIN track_formats tf ON tf.track_id = t.id
            WHERE tf.status = 'READY' AND t.visible = true
            ORDER BY RANDOM() LIMIT 1
            """)
    Mono<Track> findRandomReadyTrack();
}
