package org.kansei.wirehood.repository;

import org.kansei.wirehood.model.SongOfTheDay;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.Repository;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;

public interface SongOfTheDayRepository extends Repository<SongOfTheDay, Void> {

    // ON CONFLICT DO NOTHING - if the scheduler ever fires twice for the same day, the first pick wins, no error
    @Query("INSERT INTO song_of_the_day (day, track_id) VALUES (:day, :trackId) ON CONFLICT (day) DO NOTHING")
    Mono<Void> upsert(LocalDate day, UUID trackId);

    @Query("SELECT * FROM song_of_the_day WHERE day = :day")
    Mono<SongOfTheDay> findByDay(LocalDate day);
}
