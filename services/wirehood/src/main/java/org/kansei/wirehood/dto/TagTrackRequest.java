package org.kansei.wirehood.dto;

import java.util.List;
import java.util.UUID;

public record TagTrackRequest(List<UUID> genreIds) {
}
