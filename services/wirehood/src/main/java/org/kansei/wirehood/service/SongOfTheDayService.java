package org.kansei.wirehood.service;

import org.kansei.wirehood.dto.SongOfTheDayResponse;
import org.kansei.wirehood.repository.SongOfTheDayRepository;
import org.kansei.wirehood.repository.TrackRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

@Service
public class SongOfTheDayService {

    private final SongOfTheDayRepository songOfTheDayRepository;
    private final TrackRepository trackRepository;

    public SongOfTheDayService(SongOfTheDayRepository songOfTheDayRepository, TrackRepository trackRepository) {
        this.songOfTheDayRepository = songOfTheDayRepository;
        this.trackRepository = trackRepository;
    }

    // No-ops if the READY pool is empty (fresh/empty catalog) - nothing to pick, not an error
    public Mono<Void> pickForToday() {
        return trackRepository.findRandomReadyTrack()
                .flatMap(track -> songOfTheDayRepository.upsert(LocalDate.now(), track.getId()));
    }

    public Mono<SongOfTheDayResponse> getToday() {
        return songOfTheDayRepository.findByDay(LocalDate.now())
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "No song of the day set yet")))
                .flatMap(pick -> trackRepository.findById(pick.getTrackId())
                        .map(track -> SongOfTheDayResponse.of(pick.getDay(), track)));
    }
}
