package org.kansei.tailwind.service;

import org.kansei.tailwind.model.Visibility;
import org.kansei.tailwind.repository.FriendshipRepository;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

/**
 * The one place that decides what a viewer may see of an owner's flights. Every list, total and record goes
 * through it, so widening or narrowing access is a single change here.
 */
@Component
public class ViewerAccess {

    private static final Set<Visibility> OWN = Set.of(Visibility.PRIVATE, Visibility.FRIENDS, Visibility.PUBLIC);
    private static final Set<Visibility> FRIEND = Set.of(Visibility.FRIENDS, Visibility.PUBLIC);
    private static final Set<Visibility> STRANGER = Set.of(Visibility.PUBLIC);

    private final FriendshipRepository friendshipRepository;

    public ViewerAccess(FriendshipRepository friendshipRepository) {
        this.friendshipRepository = friendshipRepository;
    }

    public Set<Visibility> visibleTo(UUID viewerId, UUID ownerId) {
        if (viewerId.equals(ownerId)) {
            return OWN;
        }
        return friendshipRepository.areFriends(viewerId, ownerId) ? FRIEND : STRANGER;
    }

    public boolean areFriends(UUID one, UUID other) {
        return !one.equals(other) && friendshipRepository.areFriends(one, other);
    }
}
