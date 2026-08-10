package org.kansei.wirehood.dto;

// Hide from regular users, keep on server for admin/archive purposes
public record SetTrackVisibleRequest(boolean visible) {
}
