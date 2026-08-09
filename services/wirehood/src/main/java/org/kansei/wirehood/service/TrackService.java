package org.kansei.wirehood.service;

import org.kansei.wirehood.dto.LibraryTrackResponse;
import org.kansei.wirehood.dto.TrackDetailResponse;
import org.kansei.wirehood.dto.TrackFormatSummary;
import org.kansei.wirehood.model.Track;
import org.kansei.wirehood.model.TrackFormat;
import org.kansei.wirehood.model.UserLibrary;
import org.kansei.wirehood.repository.TrackFormatRepository;
import org.kansei.wirehood.repository.TrackRepository;
import org.kansei.wirehood.repository.UserLibraryRepository;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TrackService {

    private final TrackRepository trackRepository;
    private final TrackFormatRepository trackFormatRepository;
    private final UserLibraryRepository userLibraryRepository;

    public TrackService(
            TrackRepository trackRepository,
            TrackFormatRepository trackFormatRepository,
            UserLibraryRepository userLibraryRepository
    ) {
        this.trackRepository = trackRepository;
        this.trackFormatRepository = trackFormatRepository;
        this.userLibraryRepository = userLibraryRepository;
    }

    public Mono<TrackDetailResponse> getDetail(UUID trackId) {
        return trackRepository.findById(trackId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Track not found")))
                .flatMap(track -> trackFormatRepository.findByTrackId(trackId)
                        .map(TrackFormatSummary::from)
                        .collectList()
                        .map(formats -> TrackDetailResponse.of(track, formats)));
    }

    // Streams the file directly - the raw disk path never leaves the server, unlike returning thumbnailPath in JSON would
    public Mono<ResponseEntity<Resource>> getThumbnail(UUID trackId) {
        return trackRepository.findById(trackId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Track not found")))
                .flatMap(track -> track.getThumbnailPath() == null
                        ? Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "No thumbnail for this track"))
                        : Mono.just(ResponseEntity.ok()
                                .contentType(MediaType.IMAGE_JPEG) // yt-dlp always converts thumbnails to jpg (--convert-thumbnails jpg)
                                .body(new FileSystemResource(track.getThumbnailPath())))
                );
    }

    // Batches both lookups across the whole library (2 queries total, not 2N)
    public Mono<List<LibraryTrackResponse>> getLibrary(UUID userId) {
        return userLibraryRepository.findByUserId(userId)
                .collectList()
                .flatMap(entries -> {
                    if (entries.isEmpty()) {
                        return Mono.just(List.of());
                    }

                    List<UUID> trackIds = entries.stream().map(UserLibrary::getTrackId).collect(Collectors.toList());
                    Mono<Map<UUID, Track>> tracksMono = trackRepository.findAllById(trackIds).collectMap(Track::getId);
                    Mono<Map<UUID, List<TrackFormat>>> formatsMono = trackFormatRepository.findByTrackIdIn(trackIds)
                            .collect(Collectors.groupingBy(TrackFormat::getTrackId));

                    return Mono.zip(tracksMono, formatsMono).map(resolved -> {
                        Map<UUID, Track> tracks = resolved.getT1();
                        Map<UUID, List<TrackFormat>> formatsByTrack = resolved.getT2();

                        return entries.stream()
                                .map(entry -> {
                                    List<TrackFormatSummary> formats = formatsByTrack
                                            .getOrDefault(entry.getTrackId(), List.of())
                                            .stream().map(TrackFormatSummary::from).collect(Collectors.toList());
                                    return LibraryTrackResponse.of(tracks.get(entry.getTrackId()), formats, entry.getAddedAt());
                                })
                                .collect(Collectors.toList());
                    });
                });
    }
}
