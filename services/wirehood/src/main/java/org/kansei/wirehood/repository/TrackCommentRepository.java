package org.kansei.wirehood.repository;

import org.kansei.wirehood.model.TrackComment;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface TrackCommentRepository extends ReactiveCrudRepository<TrackComment, UUID> {

    // Flat list, oldest first - frontend builds the reply tree from parentCommentId, same "no server-side nesting" approach as the schema's own self-join note
    Flux<TrackComment> findByTrackIdOrderByCreatedAtAsc(UUID trackId);
}
