package org.kansei.wirehood.repository;

import org.kansei.wirehood.model.Track;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface TrackRepository extends ReactiveCrudRepository<Track, UUID> {

    Mono<Track> findByYoutubeVideoId(String youtubeVideoId);

    // Song of the Day pick - visible=true excludes anything an admin's hidden from the pool
    @Query("SELECT * FROM tracks WHERE status = 'READY' AND visible = true ORDER BY RANDOM() LIMIT 1")
    Mono<Track> findRandomReadyTrack();
}
