package org.kansei.wirehood.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

// trackIds is the FULL new order - must contain exactly the playlist's current track set, no partial reorders (service validates and 400s on mismatch, so a client can't silently drop or inject a track it doesn't have add-rights to via this endpoint)
// Not @NotEmpty, an empty playlist legitimately reorders to an empty list, the set-equality check in PlaylistService.reorder is the real validation
public record ReorderPlaylistRequest(@NotNull List<UUID> trackIds) {
}
