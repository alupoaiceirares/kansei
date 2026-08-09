package org.kansei.wirehood.dto;

// Full replace, both fields required - mirrors EditCommentRequest's single-field replace, just two fields instead of one
public record UpdatePlaylistRequest(String name, boolean shared) {
}
