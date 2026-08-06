package org.kansei.wirehood.dto;

import java.util.UUID;

public record SubmitDownloadResponse(UUID trackId, DownloadOutcome outcome) {
}
