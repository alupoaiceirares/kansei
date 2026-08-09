package org.kansei.wirehood.repository;

import org.kansei.wirehood.model.TrackFormat;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.UUID;

public interface TrackFormatRepository extends ReactiveCrudRepository<TrackFormat, UUID> {

    Flux<TrackFormat> findByTrackId(UUID trackId);

    Mono<TrackFormat> findByTrackIdAndFormat(UUID trackId, String format);

    // Batch version for library listing - one query for N tracks' formats, not N queries
    Flux<TrackFormat> findByTrackIdIn(Collection<UUID> trackIds);
}
