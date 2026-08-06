package org.kansei.wirehood.repository;

import org.kansei.wirehood.model.Track;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface TrackRepository extends ReactiveCrudRepository<Track, UUID> {

    Mono<Track> findByYoutubeVideoId(String youtubeVideoId);
}
