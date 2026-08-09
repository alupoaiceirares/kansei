package org.kansei.wirehood.dto;

import java.util.List;
import java.util.UUID;

// trackIds is the FULL new order - must contain exactly the playlist's current track set, no partial reorders (service validates and 400s on mismatch, so a client can't silently drop or inject a track it doesn't have add-rights to via this endpoint)
public record ReorderPlaylistRequest(List<UUID> trackIds) {
}
