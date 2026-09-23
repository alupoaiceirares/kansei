package org.kansei.tailwind.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One committed import run. errors lists the first rejected rows.
 */
public record CsvImportResponse(Long id, UUID adminUserId, String adminUsername, UUID targetUserId, String targetUsername, String fileName,
                                int totalRows, int importedRows, int duplicateRows, int errorRows, List<RowError> errors, Instant createdAt) {

    public record RowError(int line, String message) {
    }
}
