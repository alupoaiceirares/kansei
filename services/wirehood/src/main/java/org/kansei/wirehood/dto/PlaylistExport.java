package org.kansei.wirehood.dto;

import java.util.List;

public record PlaylistExport(String name, List<ExportTrackSummary> tracks) {
}
