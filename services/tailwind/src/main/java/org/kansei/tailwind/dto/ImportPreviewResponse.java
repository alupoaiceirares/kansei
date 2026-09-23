package org.kansei.tailwind.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The dry run of a CSV import: what every row would become, nothing is written.
 */
public record ImportPreviewResponse(UUID targetUserId, String targetUsername, int totalRows, int readyRows, int duplicateRows,
                                    int errorRows, List<Row> rows) {

    public enum Status {READY, DUPLICATE, ERROR}

    /**
     * line is the line in the file, the header being line 1. aircraft says what the aircraft resolved to.
     */
    public record Row(int line, Status status, String message, LocalDate date, String flightNumber, String airline, String from,
                      String to, String aircraft, String journey) {
    }
}
