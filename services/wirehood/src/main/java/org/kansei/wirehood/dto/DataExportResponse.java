package org.kansei.wirehood.dto;

import java.util.List;

public record DataExportResponse(List<PlaylistExport> playlists, List<LibraryTrackExport> library, ExportCounts counts) {
}
