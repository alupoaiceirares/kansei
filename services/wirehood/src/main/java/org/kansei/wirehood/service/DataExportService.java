package org.kansei.wirehood.service;

import org.kansei.wirehood.dto.DataExportResponse;
import org.kansei.wirehood.dto.ExportCounts;
import org.kansei.wirehood.dto.ExportTrackSummary;
import org.kansei.wirehood.dto.FormatExport;
import org.kansei.wirehood.dto.LibraryTrackExport;
import org.kansei.wirehood.dto.PlaylistExport;
import org.kansei.wirehood.messaging.AuditPublisher;
import org.kansei.wirehood.model.Playlist;
import org.kansei.wirehood.model.PlaylistTrack;
import org.kansei.wirehood.model.Track;
import org.kansei.wirehood.model.TrackFormat;
import org.kansei.wirehood.model.TrackFormatStatus;
import org.kansei.wirehood.model.UserLibrary;
import org.kansei.wirehood.repository.PlaylistRepository;
import org.kansei.wirehood.repository.PlaylistTrackRepository;
import org.kansei.wirehood.repository.TrackFormatRepository;
import org.kansei.wirehood.repository.TrackRepository;
import org.kansei.wirehood.repository.UserLibraryRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

// Musical-data angle only,
// Scoped to playlists the user OWNS (not ones they collaborate on)
// "your own musical data"
@Service
public class DataExportService {

    private final PlaylistRepository playlistRepository;
    private final PlaylistTrackRepository playlistTrackRepository;
    private final UserLibraryRepository userLibraryRepository;
    private final TrackRepository trackRepository;
    private final TrackFormatRepository trackFormatRepository;
    private final AuditPublisher auditPublisher;

    public DataExportService(
            PlaylistRepository playlistRepository,
            PlaylistTrackRepository playlistTrackRepository,
            UserLibraryRepository userLibraryRepository,
            TrackRepository trackRepository,
            TrackFormatRepository trackFormatRepository,
            AuditPublisher auditPublisher
    ) {
        this.playlistRepository = playlistRepository;
        this.playlistTrackRepository = playlistTrackRepository;
        this.userLibraryRepository = userLibraryRepository;
        this.trackRepository = trackRepository;
        this.trackFormatRepository = trackFormatRepository;
        this.auditPublisher = auditPublisher;
    }

    // Self-service action, not admin-gated - still worth an audit trail entry since it's a full personal-data pull
    public Mono<DataExportResponse> export(UUID userId) {
        return Mono.zip(exportPlaylists(userId), exportLibrary(userId))
                .map(resolved -> new DataExportResponse(
                        resolved.getT1(),
                        resolved.getT2(),
                        new ExportCounts(resolved.getT2().size(), resolved.getT1().size())
                ))
                .flatMap(response -> auditPublisher.publishWithResolvedUsername("EXPORT_DATA", userId, "USER", userId.toString(),
                                Map.of("playlistsOwned", response.counts().playlistsOwned(), "tracksDownloaded", response.counts().tracksDownloaded()))
                        .thenReturn(response));
    }

    // One query per owned playlist for its track order - not a hot path, this is a one-off export action
    private Mono<List<PlaylistExport>> exportPlaylists(UUID userId) {
        return playlistRepository.findByOwnerId(userId)
                .concatMap(playlist -> playlistTrackRepository.findByPlaylistIdOrderByPosition(playlist.getId())
                        .map(PlaylistTrack::getTrackId)
                        .collectList()
                        .flatMap(trackIds -> trackIds.isEmpty() ? Mono.just(List.<Track>of()) : trackRepository.findAllById(trackIds).collectList())
                        .map(tracks -> new PlaylistExport(playlist.getName(),
                                tracks.stream().map(ExportTrackSummary::from).collect(Collectors.toList()))))
                .collectList();
    }

    private Mono<List<LibraryTrackExport>> exportLibrary(UUID userId) {
        return userLibraryRepository.findByUserId(userId)
                .collectList()
                .flatMap(entries -> {
                    if (entries.isEmpty()) {
                        return Mono.just(List.of());
                    }

                    List<UUID> trackIds = entries.stream().map(UserLibrary::getTrackId).collect(Collectors.toList());
                    Mono<Map<UUID, Track>> tracksMono = trackRepository.findAllById(trackIds).collectMap(Track::getId);
                    // Only READY formats count as "saved" - a still-pending or failed attempt isn't a format the user actually has
                    Mono<Map<UUID, List<TrackFormat>>> formatsMono = trackFormatRepository.findByTrackIdIn(trackIds)
                            .filter(format -> format.getStatus() == TrackFormatStatus.READY)
                            .collect(Collectors.groupingBy(TrackFormat::getTrackId));

                    return Mono.zip(tracksMono, formatsMono).map(resolved -> {
                        Map<UUID, Track> tracks = resolved.getT1();
                        Map<UUID, List<TrackFormat>> formatsByTrack = resolved.getT2();

                        return trackIds.stream()
                                .map(trackId -> {
                                    List<FormatExport> formats = formatsByTrack
                                            .getOrDefault(trackId, List.of())
                                            .stream().map(FormatExport::from).collect(Collectors.toList());
                                    return LibraryTrackExport.of(tracks.get(trackId), formats);
                                })
                                .collect(Collectors.toList());
                    });
                });
    }
}
