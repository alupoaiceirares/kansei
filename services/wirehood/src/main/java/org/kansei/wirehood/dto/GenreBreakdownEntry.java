package org.kansei.wirehood.dto;

import java.util.UUID;

// percentage is of tagged library tracks only, untagged tracks are excluded from the denominator entirely
public record GenreBreakdownEntry(UUID genreId, String genreName, long trackCount, double percentage) {
}
