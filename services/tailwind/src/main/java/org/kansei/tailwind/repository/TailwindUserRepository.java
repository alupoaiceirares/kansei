package org.kansei.tailwind.repository;

import org.kansei.tailwind.model.TailwindUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface TailwindUserRepository extends JpaRepository<TailwindUser, UUID> {

    @Query("select u.userId from TailwindUser u")
    List<UUID> findAllUserIds();
}
