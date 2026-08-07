package org.kansei.wirehood.dto;

import java.util.UUID;

// Aggregate only - crowd-tagging is a vote count per genre, not per-user attribution (see WIREHOOD_PLAN.md's Genres + music profile section), so no userId belongs here
public record GenreVoteResponse(UUID genreId, String genreName, long votes) {
}
