package org.kansei.wirehood.dto;

// Lets a search result's frontend button render correctly (Add / Pending / Accept / already Friends) without a second round-trip
public enum FriendshipRelation {
    NONE,
    PENDING_OUTGOING,
    PENDING_INCOMING,
    FRIENDS
}
