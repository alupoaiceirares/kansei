package org.kansei.wirehood.repository;

import org.kansei.wirehood.model.DownloadRequest;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface DownloadRequestRepository extends ReactiveCrudRepository<DownloadRequest, UUID> {

    Flux<DownloadRequest> findByUserIdAndAcknowledgedAtIsNull(UUID userId);

    Flux<DownloadRequest> findByTrackIdAndAcknowledgedAtIsNull(UUID trackId);
}
