package org.kansei.wirehood.repository;

import org.kansei.wirehood.model.Genre;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import java.util.UUID;

public interface GenreRepository extends ReactiveCrudRepository<Genre, UUID> {
}
