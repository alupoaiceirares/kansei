package org.kansei.wirehood.dto;

import java.util.UUID;

// Raw per-(track, genre) vote count, MusicProfileService.computeBreakdown picks each track's single dominant genre from these rows, this projection itself makes no such decision
public record TrackGenreVoteRow(UUID trackId, UUID genreId, String genreName, long votes) {
}
