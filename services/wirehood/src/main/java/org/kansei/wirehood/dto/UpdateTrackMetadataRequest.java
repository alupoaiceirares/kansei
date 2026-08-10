package org.kansei.wirehood.dto;

// Full replace, admin fixing a bad parse-and-confirm entry, artist/extraInfo are nullable columns, sending null clears them
public record UpdateTrackMetadataRequest(String title, String artist, String extraInfo) {
}
