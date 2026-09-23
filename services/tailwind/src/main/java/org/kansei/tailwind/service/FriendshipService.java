package org.kansei.tailwind.service;

import org.kansei.tailwind.client.ShieldwallUserClient;
import org.kansei.tailwind.dto.FriendRequestResponse;
import org.kansei.tailwind.dto.FriendResponse;
import org.kansei.tailwind.dto.UserSearchResult;
import org.kansei.tailwind.model.Friendship;
import org.kansei.tailwind.model.FriendshipId;
import org.kansei.tailwind.model.FriendshipStatus;
import org.kansei.tailwind.repository.FriendshipRepository;
import org.kansei.tailwind.repository.TailwindUserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Friend requests and the friend list. Both users must have opted into tailwind, and only the person who
 * received a request may accept or decline it.
 */
@Service
@Transactional
public class FriendshipService {

    private static final int DEFAULT_SEARCH_LIMIT = 20;
    private static final int MAX_SEARCH_LIMIT = 50;
    private static final int MIN_QUERY_LENGTH = 2;

    private final FriendshipRepository friendshipRepository;
    private final TailwindUserRepository tailwindUserRepository;
    private final ShieldwallUserClient shieldwallUserClient;
    private final OptInGuard optInGuard;
    private final Clock clock;

    public FriendshipService(FriendshipRepository friendshipRepository, TailwindUserRepository tailwindUserRepository,
                             ShieldwallUserClient shieldwallUserClient, OptInGuard optInGuard, Clock clock) {
        this.friendshipRepository = friendshipRepository;
        this.tailwindUserRepository = tailwindUserRepository;
        this.shieldwallUserClient = shieldwallUserClient;
        this.optInGuard = optInGuard;
        this.clock = clock;
    }

    // Only opted-in users show up, someone who never joined tailwind has nothing to be friends about
    @Transactional(readOnly = true)
    public List<UserSearchResult> search(UUID callerId, String query, Integer limit) {
        optInGuard.require(callerId);
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.length() < MIN_QUERY_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "query must be at least " + MIN_QUERY_LENGTH + " characters");
        }
        int size = limit == null ? DEFAULT_SEARCH_LIMIT : Math.min(Math.max(limit, 1), MAX_SEARCH_LIMIT);

        List<ShieldwallUserClient.UserMatch> matches = shieldwallUserClient.searchUsers(trimmed, size).stream()
                .filter(match -> !match.id().equals(callerId))
                .toList();
        Set<UUID> optedIn = tailwindUserRepository.findAllById(matches.stream().map(ShieldwallUserClient.UserMatch::id).toList())
                .stream().filter(user -> user.isEnabled()).map(user -> user.getUserId()).collect(Collectors.toSet());

        Map<UUID, Friendship> existing = friendshipRepository.findByUser(callerId).stream()
                .collect(Collectors.toMap(friendship -> friendship.otherThan(callerId), friendship -> friendship));

        return matches.stream()
                .filter(match -> optedIn.contains(match.id()))
                .map(match -> new UserSearchResult(match.id(), match.username(), relationTo(callerId, existing.get(match.id()))))
                .toList();
    }

    public void sendRequest(UUID callerId, UUID targetId) {
        optInGuard.require(callerId);
        if (callerId.equals(targetId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot befriend yourself");
        }
        if (!tailwindUserRepository.existsById(targetId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "That user has not opted into tailwind");
        }
        FriendshipId id = FriendshipId.of(callerId, targetId);
        Friendship existing = friendshipRepository.findById(id).orElse(null);
        if (existing != null) {
            if (existing.accepted()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "You are already friends");
            }
            // They asked first, so sending back is the same as accepting
            if (!existing.getRequestedBy().equals(callerId)) {
                accept(callerId, targetId);
                return;
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You already sent a request to this user");
        }
        try {
            friendshipRepository.saveAndFlush(Friendship.builder()
                    .id(id)
                    .requestedBy(callerId)
                    .status(FriendshipStatus.PENDING)
                    .requestedAt(clock.instant())
                    .build());
        } catch (DataIntegrityViolationException ex) {
            // Both sides asked at the same moment, the other insert won
            throw new ResponseStatusException(HttpStatus.CONFLICT, "There is already a request between you");
        }
    }

    public void accept(UUID callerId, UUID requesterId) {
        Friendship friendship = requirePending(callerId, requesterId);
        friendship.setStatus(FriendshipStatus.ACCEPTED);
        friendship.setRespondedAt(clock.instant());
        friendshipRepository.save(friendship);
    }

    public void decline(UUID callerId, UUID requesterId) {
        friendshipRepository.delete(requirePending(callerId, requesterId));
    }

    // Either side can withdraw a request they sent, or unfriend
    public void remove(UUID callerId, UUID otherId) {
        optInGuard.require(callerId);
        Friendship friendship = friendshipRepository.findById(FriendshipId.of(callerId, otherId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No friendship or request with that user"));
        friendshipRepository.delete(friendship);
    }

    @Transactional(readOnly = true)
    public List<FriendResponse> friends(UUID callerId) {
        optInGuard.require(callerId);
        List<Friendship> accepted = friendshipRepository.findByUserAndStatus(callerId, FriendshipStatus.ACCEPTED);
        Map<UUID, String> usernames = resolveUsernames(accepted, callerId);
        return accepted.stream()
                .map(friendship -> new FriendResponse(friendship.otherThan(callerId),
                        usernames.get(friendship.otherThan(callerId)), friendship.getRespondedAt()))
                .sorted(Comparator.comparing(FriendResponse::username, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FriendRequestResponse> pendingRequests(UUID callerId) {
        optInGuard.require(callerId);
        List<Friendship> pending = friendshipRepository.findByUserAndStatus(callerId, FriendshipStatus.PENDING);
        Map<UUID, String> usernames = resolveUsernames(pending, callerId);
        List<FriendRequestResponse> requests = new ArrayList<>();
        for (Friendship friendship : pending) {
            UUID other = friendship.otherThan(callerId);
            FriendRequestResponse.Direction direction = friendship.getRequestedBy().equals(callerId)
                    ? FriendRequestResponse.Direction.OUTGOING
                    : FriendRequestResponse.Direction.INCOMING;
            requests.add(new FriendRequestResponse(other, usernames.get(other), direction, friendship.getRequestedAt()));
        }
        // Incoming first, they are the ones needing an answer
        requests.sort(Comparator.comparing(FriendRequestResponse::direction).thenComparing(FriendRequestResponse::requestedAt).reversed());
        return requests;
    }

    private Friendship requirePending(UUID callerId, UUID requesterId) {
        optInGuard.require(callerId);
        Friendship friendship = friendshipRepository.findById(FriendshipId.of(callerId, requesterId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No request from that user"));
        if (friendship.accepted()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You are already friends");
        }
        // Answering your own request would be a way to befriend anyone
        if (friendship.getRequestedBy().equals(callerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot answer your own request");
        }
        return friendship;
    }

    private Map<UUID, String> resolveUsernames(List<Friendship> friendships, UUID callerId) {
        return shieldwallUserClient.resolveUsernames(friendships.stream().map(friendship -> friendship.otherThan(callerId)).toList());
    }

    private static UserSearchResult.Relation relationTo(UUID callerId, Friendship friendship) {
        if (friendship == null) {
            return UserSearchResult.Relation.NONE;
        }
        if (friendship.accepted()) {
            return UserSearchResult.Relation.FRIENDS;
        }
        return friendship.getRequestedBy().equals(callerId)
                ? UserSearchResult.Relation.REQUEST_SENT
                : UserSearchResult.Relation.REQUEST_RECEIVED;
    }
}
