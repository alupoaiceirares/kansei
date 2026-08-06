package org.kansei.wirehood.repository;

import org.kansei.wirehood.model.WirehoodUser;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import java.util.UUID;

public interface WirehoodUserRepository extends ReactiveCrudRepository<WirehoodUser, UUID> {
}
