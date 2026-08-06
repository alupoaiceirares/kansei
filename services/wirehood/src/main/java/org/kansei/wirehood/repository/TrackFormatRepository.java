package org.kansei.wirehood.repository;

import org.kansei.wirehood.model.TrackFormat;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface TrackFormatRepository extends ReactiveCrudRepository<TrackFormat, UUID> {

    Flux<TrackFormat> findByTrackId(UUID trackId);
}
