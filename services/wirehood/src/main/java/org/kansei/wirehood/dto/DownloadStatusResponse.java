package org.kansei.wirehood.dto;

import java.util.List;

// Current pending/failed download_requests for the caller, for a page-load/reconnect badge complements the SSE stream (which only pushes while connected), doesn't replace it
// Loading this acknowledges the failed entries it returns so they don't resurface next call; pending entries stay unacknowledged since they're still in flight
public record DownloadStatusResponse(
        List<PendingDownloadItem> pending,
        List<FailedDownloadItem> failed
) {
}
