package org.kansei.wirehood.service;

import org.kansei.wirehood.dto.ExistingTrackResponse;
import org.kansei.wirehood.dto.LibraryTrackResponse;
import org.kansei.wirehood.dto.PageResponse;
import org.kansei.wirehood.dto.TrackDetailResponse;
import org.kansei.wirehood.dto.TrackFormatSummary;
import org.kansei.wirehood.dto.UpdateTrackMetadataRequest;
import org.kansei.wirehood.model.Track;
import org.kansei.wirehood.model.TrackFormat;
import org.kansei.wirehood.model.TrackFormatFavorite;
import org.kansei.wirehood.model.TrackFormatPlayCount;
import org.kansei.wirehood.model.TrackFormatStatus;
import org.kansei.wirehood.model.TrackThumbnailSubmission;
import org.kansei.wirehood.model.UserLibrary;
import org.kansei.wirehood.repository.TrackFormatFavoriteRepository;
import org.kansei.wirehood.repository.TrackFormatPlayCountRepository;
import org.kansei.wirehood.repository.TrackFormatRepository;
import org.kansei.wirehood.repository.TrackRepository;
import org.kansei.wirehood.repository.TrackThumbnailSubmissionRepository;
import org.kansei.wirehood.repository.UserLibraryRepository;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TrackService {

    private final TrackRepository trackRepository;
    private final TrackFormatRepository trackFormatRepository;
    private final UserLibraryRepository userLibraryRepository;
    private final TrackThumbnailSubmissionRepository trackThumbnailSubmissionRepository;
    private final TrackFormatFavoriteRepository trackFormatFavoriteRepository;
    private final TrackFormatPlayCountRepository trackFormatPlayCountRepository;
    private final AdminAuthService adminAuthService;

    public TrackService(
            TrackRepository trackRepository,
            TrackFormatRepository trackFormatRepository,
            UserLibraryRepository userLibraryRepository,
            TrackThumbnailSubmissionRepository trackThumbnailSubmissionRepository,
            TrackFormatFavoriteRepository trackFormatFavoriteRepository,
            TrackFormatPlayCountRepository trackFormatPlayCountRepository,
            AdminAuthService adminAuthService
    ) {
        this.trackRepository = trackRepository;
        this.trackFormatRepository = trackFormatRepository;
        this.userLibraryRepository = userLibraryRepository;
        this.trackThumbnailSubmissionRepository = trackThumbnailSubmissionRepository;
        this.trackFormatFavoriteRepository = trackFormatFavoriteRepository;
        this.trackFormatPlayCountRepository = trackFormatPlayCountRepository;
        this.adminAuthService = adminAuthService;
    }

    // userId optional - anonymous browsing still works (matches this endpoint's existing no-auth behavior), just
    // without per-format playCount/favorited on the response
    public Mono<TrackDetailResponse> getDetail(UUID trackId, UUID userId) {
        return trackRepository.findById(trackId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Track not found")))
                .flatMap(track -> trackFormatRepository.findByTrackId(trackId).collectList()
                        .flatMap(formats -> userId == null ? plainSummaries(formats) : summariesForUser(userId, formats))
                        .map(formats -> TrackDetailResponse.of(track, formats)));
    }

    // Backs the search confirm popup's "already exists" check, keyed by youtube_video_id since that's
    // all the popup has before any download is submitted - no trackId yet. No auth needed, same as search itself.
    public Mono<ExistingTrackResponse> findExistingByVideoId(String youtubeVideoId) {
        return trackRepository.findByYoutubeVideoId(youtubeVideoId)
                .flatMap(track -> trackFormatRepository.findByTrackId(track.getId())
                        .map(TrackFormatSummary::from)
                        .collectList()
                        .map(formats -> new ExistingTrackResponse(true, track.getId(), track.getTitle(), track.getArtist(), track.getExtraInfo(), formats)))
                .defaultIfEmpty(ExistingTrackResponse.notFound());
    }

    private Mono<List<TrackFormatSummary>> plainSummaries(List<TrackFormat> formats) {
        return Mono.just(formats.stream().map(TrackFormatSummary::from).collect(Collectors.toList()));
    }

    private Mono<List<TrackFormatSummary>> summariesForUser(UUID userId, List<TrackFormat> formats) {
        List<UUID> formatIds = formats.stream().map(TrackFormat::getId).collect(Collectors.toList());
        if (formatIds.isEmpty()) {
            return Mono.just(List.of());
        }

        Mono<Map<UUID, Integer>> playsMono = trackFormatPlayCountRepository.findByUserIdAndTrackFormatIdIn(userId, formatIds)
                .collectMap(TrackFormatPlayCount::getTrackFormatId, TrackFormatPlayCount::getPlayCount);
        Mono<Set<UUID>> favoritesMono = trackFormatFavoriteRepository.findByUserIdAndTrackFormatIdIn(userId, formatIds)
                .map(TrackFormatFavorite::getTrackFormatId)
                .collect(Collectors.toSet());

        return Mono.zip(playsMono, favoritesMono).map(resolved -> formats.stream()
                .map(format -> TrackFormatSummary.from(
                        format,
                        resolved.getT1().getOrDefault(format.getId(), 0),
                        resolved.getT2().contains(format.getId())))
                .collect(Collectors.toList()));
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

    // Not scoped to the user's own library - this endpoint backs both in-browser streaming
    // (mini-player, nothing ever touches disk) and the explicit "Save to device" action, and a
    // library-ownership gate blocked the former for any track a user hasn't saved yet, including
    // Song of the Day's own discovery picks (deliberately NOT scoped to any one user's library -
    // the whole point is previewing something before deciding to keep it). Same trust level every
    // other per-track read already has (comments, genre-tags, thumbnail) - platform-wide shared
    // archive, no ownership check, not "download to my machine is a personal action" (that
    // reasoning only ever applied to the save case, never the stream-to-play one sharing this path).
    public Mono<ResponseEntity<Resource>> downloadFile(UUID userId, UUID trackId, String format) {
        return trackFormatRepository.findByTrackIdAndFormat(trackId, format)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "This format hasn't been downloaded for this track")))
                .flatMap(trackFormat -> toFileResponse(userId, trackFormat));
    }

    // Records the play only on an actual successful serve (status READY, file present) - never on a 404/409, so a
    // not-ready or missing format can't rack up plays
    private Mono<ResponseEntity<Resource>> toFileResponse(UUID userId, TrackFormat trackFormat) {
        if (trackFormat.getStatus() != TrackFormatStatus.READY || trackFormat.getFilePath() == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT, "This format isn't ready yet"));
        }
        Path path = Path.of(trackFormat.getFilePath());
        MediaType mediaType = "mp4".equals(trackFormat.getFormat()) ? MediaType.valueOf("video/mp4") : MediaType.valueOf("audio/mpeg");
        return trackFormatPlayCountRepository.recordPlay(userId, trackFormat.getId(), Instant.now())
                .then(Mono.just(ResponseEntity.ok()
                        .contentType(mediaType)
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + path.getFileName() + "\"")
                        .body(new FileSystemResource(path))));
    }

    // Batches both lookups across the page (2 queries total, not 2N), plus a 3rd for the total count
    public Mono<PageResponse<LibraryTrackResponse>> getLibrary(UUID userId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size), Sort.by(Sort.Direction.DESC, "addedAt"));
        return Mono.zip(
                        userLibraryRepository.findByUserId(userId, pageable).collectList(),
                        userLibraryRepository.countByUserId(userId))
                .flatMap(counted -> {
                    List<UserLibrary> entries = counted.getT1();
                    long total = counted.getT2();
                    if (entries.isEmpty()) {
                        return Mono.just(PageResponse.of(List.<LibraryTrackResponse>of(), pageable.getPageNumber(), pageable.getPageSize(), total));
                    }

                    List<UUID> trackIds = entries.stream().map(UserLibrary::getTrackId).collect(Collectors.toList());
                    Mono<Map<UUID, Track>> tracksMono = trackRepository.findAllById(trackIds).collectMap(Track::getId);
                    Mono<Map<UUID, List<TrackFormat>>> formatsMono = trackFormatRepository.findByTrackIdIn(trackIds)
                            .collect(Collectors.groupingBy(TrackFormat::getTrackId));

                    return Mono.zip(tracksMono, formatsMono).map(resolved -> {
                        Map<UUID, Track> tracks = resolved.getT1();
                        Map<UUID, List<TrackFormat>> formatsByTrack = resolved.getT2();

                        List<LibraryTrackResponse> items = entries.stream()
                                .map(entry -> {
                                    List<TrackFormatSummary> formats = formatsByTrack
                                            .getOrDefault(entry.getTrackId(), List.of())
                                            .stream().map(TrackFormatSummary::from).collect(Collectors.toList());
                                    return LibraryTrackResponse.of(tracks.get(entry.getTrackId()), formats, entry.getAddedAt());
                                })
                                .collect(Collectors.toList());
                        return PageResponse.of(items, pageable.getPageNumber(), pageable.getPageSize(), total);
                    });
                });
    }

    // 1-100, defaulting downward rather than erroring, a bad size param isn't worth 400ing over
    private static int clampSize(int size) {
        return Math.min(Math.max(size, 1), 100);
    }

    // Admin-only, fixing a bad parse-and-confirm entry (title/artist/extra_info), title's @NotBlank enforced by @Valid at the controller, not re-checked here
    public Mono<TrackDetailResponse> updateMetadata(UUID trackId, UUID adminUserId, String adminRole, UpdateTrackMetadataRequest request) {
        return adminAuthService.requireAdmin(adminUserId, adminRole)
                .then(findTrackOr404(trackId))
                .flatMap(track -> {
                    track.setTitle(request.title());
                    track.setArtist(request.artist());
                    track.setExtraInfo(request.extraInfo());
                    track.setUpdatedAt(Instant.now());
                    return trackRepository.save(track);
                })
                .flatMap(saved -> trackFormatRepository.findByTrackId(trackId)
                        .map(TrackFormatSummary::from)
                        .collectList()
                        .map(formats -> TrackDetailResponse.of(saved, formats)));
    }

    // Admin-only, hide from regular users, keep on server/DB for admin/archive purposes; distinct from hardDelete below
    public Mono<Void> setVisible(UUID trackId, UUID adminUserId, String adminRole, boolean visible) {
        return adminAuthService.requireAdmin(adminUserId, adminRole)
                .then(findTrackOr404(trackId))
                .flatMap(track -> {
                    track.setVisible(visible);
                    track.setUpdatedAt(Instant.now());
                    return trackRepository.save(track);
                })
                .then();
    }

    // Admin-only, permanent, the audit_log row is what preserves the history, not the row itself
    // Every FK to tracks(id) is ON DELETE CASCADE, so DB rows clean up on their own
    // The actual media/thumbnail/submission files on disk don't, so those are gathered before the delete and removed after
    public Mono<Void> hardDelete(UUID trackId, UUID adminUserId, String adminRole) {
        return adminAuthService.requireAdmin(adminUserId, adminRole)
                .then(findTrackOr404(trackId))
                .flatMap(track -> Mono.zip(
                                trackFormatRepository.findByTrackId(trackId).collectList(),
                                trackThumbnailSubmissionRepository.findByTrackId(trackId).collectList()
                        )
                        .flatMap(gathered -> trackRepository.delete(track)
                                .then(deleteTrackFiles(track, gathered.getT1(), gathered.getT2()))));
    }

    private Mono<Void> deleteTrackFiles(Track track, List<TrackFormat> formats, List<TrackThumbnailSubmission> submissions) {
        return Flux.fromIterable(formats)
                .map(TrackFormat::getFilePath)
                .filter(path -> path != null)
                .concatWith(Flux.fromIterable(submissions).map(TrackThumbnailSubmission::getFilePath).filter(path -> path != null))
                .concatMap(path -> deleteFileIfPresent(Path.of(path)))
                .then(track.getThumbnailPath() == null ? Mono.empty() : deleteFileIfPresent(Path.of(track.getThumbnailPath())));
    }

    // Best-effort, fail-soft, same pattern as ThumbnailSubmissionService, a leftover file is cheaper than blocking the caller over disk cleanup
    private Mono<Void> deleteFileIfPresent(Path path) {
        return Mono.fromCallable(() -> Files.deleteIfExists(path))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(ex -> Mono.just(false))
                .then();
    }

    private Mono<Track> findTrackOr404(UUID trackId) {
        return trackRepository.findById(trackId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Track not found")));
    }
}
