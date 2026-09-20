package org.kansei.tailwind.dto;

import java.util.UUID;

/**
 * A user the caller could befriend, with how they already relate to them so the UI knows which button to show.
 */
public record UserSearchResult(UUID userId, String username, Relation relation) {

    public enum Relation {
        NONE,
        REQUEST_SENT,
        REQUEST_RECEIVED,
        FRIENDS
    }
}
