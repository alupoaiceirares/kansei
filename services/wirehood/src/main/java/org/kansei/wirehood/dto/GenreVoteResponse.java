package org.kansei.wirehood.dto;

import java.util.UUID;

// Aggregate only - crowd-tagging is a vote count per genre, not per-user attribution, so no userId belongs here
public record GenreVoteResponse(UUID genreId, String genreName, long votes) {
}
