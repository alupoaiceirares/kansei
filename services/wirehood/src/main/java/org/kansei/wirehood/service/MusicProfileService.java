package org.kansei.wirehood.service;

import org.kansei.wirehood.dto.GenreBreakdownEntry;
import org.kansei.wirehood.dto.TrackGenreVoteRow;
import org.kansei.wirehood.model.Track;
import org.kansei.wirehood.model.UserLibrary;
import org.kansei.wirehood.repository.FriendshipRepository;
import org.kansei.wirehood.repository.PlaylistCollaboratorRepository;
import org.kansei.wirehood.repository.PlaylistRepository;
import org.kansei.wirehood.repository.TrackGenreTagRepository;
import org.kansei.wirehood.repository.TrackRepository;
import org.kansei.wirehood.repository.UserLibraryRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Backs the GraphQL music profile query (MusicProfileController), everything here is computed at query time from user_library/tracks/track_genre_tags/playlists/friendships, nothing stored/cached
 */
@Service
public class MusicProfileService {

    private final UserLibraryRepository userLibraryRepository;
    private final TrackRepository trackRepository;
    private final TrackGenreTagRepository trackGenreTagRepository;
    private final PlaylistRepository playlistRepository;
    private final PlaylistCollaboratorRepository playlistCollaboratorRepository;
    private final FriendshipRepository friendshipRepository;

    public MusicProfileService(
            UserLibraryRepository userLibraryRepository,
            TrackRepository trackRepository,
            TrackGenreTagRepository trackGenreTagRepository,
            PlaylistRepository playlistRepository,
            PlaylistCollaboratorRepository playlistCollaboratorRepository,
            FriendshipRepository friendshipRepository
    ) {
        this.userLibraryRepository = userLibraryRepository;
        this.trackRepository = trackRepository;
        this.trackGenreTagRepository = trackGenreTagRepository;
        this.playlistRepository = playlistRepository;
        this.playlistCollaboratorRepository = playlistCollaboratorRepository;
        this.friendshipRepository = friendshipRepository;
    }

    public Mono<Long> totalTracksSaved(UUID userId) {
        return userLibraryRepository.countByUserId(userId);
    }

    // Each library track contributes to its single dominant (most-voted) genre, not every genre it's ever been tagged with, avoids one heavily-tagged track skewing the percentages disproportionately
    public Mono<List<GenreBreakdownEntry>> genreBreakdown(UUID userId) {
        return libraryTrackIds(userId)
                .flatMap(trackIds -> trackIds.isEmpty()
                        ? Mono.just(List.<GenreBreakdownEntry>of())
                        : trackGenreTagRepository.countVotesForTracks(trackIds)
                                .collectList()
                                .map(MusicProfileService::computeBreakdown));
    }

    private static List<GenreBreakdownEntry> computeBreakdown(List<TrackGenreVoteRow> rows) {
        Map<UUID, TrackGenreVoteRow> dominantPerTrack = new HashMap<>();
        for (TrackGenreVoteRow row : rows) {
            TrackGenreVoteRow current = dominantPerTrack.get(row.trackId());
            if (current == null || row.votes() > current.votes()
                    || (row.votes() == current.votes() && row.genreName().compareTo(current.genreName()) < 0)) {
                dominantPerTrack.put(row.trackId(), row);
            }
        }

        long taggedTrackCount = dominantPerTrack.size();
        if (taggedTrackCount == 0) {
            return List.of();
        }

        Map<UUID, String> genreNameById = new HashMap<>();
        Map<UUID, Long> trackCountByGenre = new HashMap<>();
        for (TrackGenreVoteRow dominant : dominantPerTrack.values()) {
            genreNameById.putIfAbsent(dominant.genreId(), dominant.genreName());
            trackCountByGenre.merge(dominant.genreId(), 1L, Long::sum);
        }

        return trackCountByGenre.entrySet().stream()
                .map(entry -> new GenreBreakdownEntry(
                        entry.getKey(),
                        genreNameById.get(entry.getKey()),
                        entry.getValue(),
                        (entry.getValue() * 100.0) / taggedTrackCount))
                .sorted(Comparator.comparingLong(GenreBreakdownEntry::trackCount).reversed())
                .toList();
    }

    // "Most-downloaded artist" = the artist with the most tracks in the caller's own library, there's no separate per-user download-count stat, this is the personal-profile reading of that field
    public Mono<String> mostDownloadedArtist(UUID userId) {
        return libraryTrackIds(userId)
                .flatMap(trackIds -> trackIds.isEmpty()
                        ? Mono.<String>empty()
                        : trackRepository.findAllById(trackIds)
                                .map(Track::getArtist)
                                .filter(artist -> artist != null && !artist.isBlank())
                                .collect(Collectors.groupingBy(artist -> artist, Collectors.counting()))
                                .flatMap(countByArtist -> countByArtist.entrySet().stream()
                                        .max(Map.Entry.comparingByValue())
                                        .map(entry -> Mono.just(entry.getKey()))
                                        .orElse(Mono.empty())));
    }

    public Mono<Long> playlistsOwned(UUID userId) {
        return playlistRepository.countByOwnerId(userId);
    }

    public Mono<Long> playlistsCollaborated(UUID userId) {
        return playlistCollaboratorRepository.countByUserId(userId);
    }

    public Mono<Long> friendCount(UUID userId) {
        return friendshipRepository.countAcceptedForUser(userId);
    }

    private Mono<List<UUID>> libraryTrackIds(UUID userId) {
        return userLibraryRepository.findByUserId(userId)
                .map(UserLibrary::getTrackId)
                .collectList();
    }
}
