package org.kansei.wirehood.service;

import org.kansei.wirehood.client.ShieldwallUserClient;
import org.kansei.wirehood.client.ShieldwallUserClient.UserMatch;
import org.kansei.wirehood.dto.FriendRequestDirection;
import org.kansei.wirehood.dto.FriendRequestResponse;
import org.kansei.wirehood.dto.FriendResponse;
import org.kansei.wirehood.dto.FriendSearchResult;
import org.kansei.wirehood.dto.FriendshipRelation;
import org.kansei.wirehood.model.Friendship;
import org.kansei.wirehood.model.FriendshipStatus;
import org.kansei.wirehood.repository.FriendshipRepository;
import org.kansei.wirehood.repository.WirehoodUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class FriendshipService {

    private static final int DEFAULT_SEARCH_LIMIT = 20;

    private final FriendshipRepository friendshipRepository;
    private final WirehoodUserRepository wirehoodUserRepository;
    private final ShieldwallUserClient shieldwallUserClient;

    public FriendshipService(
            FriendshipRepository friendshipRepository,
            WirehoodUserRepository wirehoodUserRepository,
            ShieldwallUserClient shieldwallUserClient
    ) {
        this.friendshipRepository = friendshipRepository;
        this.wirehoodUserRepository = wirehoodUserRepository;
        this.shieldwallUserClient = shieldwallUserClient;
    }

    public Mono<List<FriendSearchResult>> search(UUID callerId, String query, Integer limit) {
        int effectiveLimit = limit == null ? DEFAULT_SEARCH_LIMIT : limit;
        return shieldwallUserClient.searchUsers(query, effectiveLimit)
                .map(matches -> matches.stream().filter(m -> !m.id().equals(callerId)).collect(Collectors.toList()))
                .flatMap(candidates -> {
                    List<UUID> candidateIds = candidates.stream().map(UserMatch::id).collect(Collectors.toList());
                    // wirehood_users scopes discovery to people actually on wirehood, not the entire shieldwall user base
                    Mono<Set<UUID>> enrolledIdsMono = wirehoodUserRepository.findAllById(candidateIds)
                            .map(wu -> wu.getUserId())
                            .collect(Collectors.toSet());
                    Mono<Map<UUID, Friendship>> friendshipsMono = friendshipRepository.findAllForUser(callerId)
                            .collect(Collectors.toMap(f -> otherId(f, callerId), f -> f));

                    return Mono.zip(enrolledIdsMono, friendshipsMono).map(resolved -> {
                        Set<UUID> enrolledIds = resolved.getT1();
                        Map<UUID, Friendship> friendships = resolved.getT2();

                        return candidates.stream()
                                .filter(match -> enrolledIds.contains(match.id()))
                                .map(match -> new FriendSearchResult(match.id(), match.username(), relationOf(friendships.get(match.id()), callerId)))
                                .collect(Collectors.toList());
                    });
                });
    }

    public Mono<Void> sendRequest(UUID callerId, UUID targetId) {
        if (targetId.equals(callerId)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Can't send a friend request to yourself"));
        }
        return wirehoodUserRepository.existsById(targetId)
                .flatMap(isWirehoodUser -> isWirehoodUser
                        ? Mono.empty()
                        : Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Target is not a wirehood user")))
                .then(Mono.defer(() -> {
                    OrderedPair pair = OrderedPair.of(callerId, targetId);
                    return friendshipRepository.findByPair(pair.a(), pair.b())
                            .flatMap(existing -> Mono.<Void>error(new ResponseStatusException(HttpStatus.CONFLICT,
                                    "A friendship or pending request already exists between these users")))
                            .switchIfEmpty(friendshipRepository.insertPending(pair.a(), pair.b(), callerId, Instant.now()));
                }));
    }

    public Mono<Void> accept(UUID callerId, UUID otherId) {
        OrderedPair pair = OrderedPair.of(callerId, otherId);
        return friendshipRepository.findByPair(pair.a(), pair.b())
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "No pending request between these users")))
                .flatMap(friendship -> {
                    if (friendship.getStatus() != FriendshipStatus.PENDING) {
                        return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request is not pending"));
                    }
                    if (friendship.getRequestedBy().equals(callerId)) {
                        return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Can't accept your own outgoing request"));
                    }
                    return friendshipRepository.markAccepted(pair.a(), pair.b(), Instant.now());
                });
    }

    // Covers cancel (caller was the requester, still PENDING), decline (caller was the recipient, still PENDING),
    // and unfriend (ACCEPTED) - no extra permission branching needed, the pair is always derived from (callerId, otherId) so the row touched is always the caller's own relationship
    public Mono<Void> remove(UUID callerId, UUID otherId) {
        OrderedPair pair = OrderedPair.of(callerId, otherId);
        return friendshipRepository.findByPair(pair.a(), pair.b())
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "No friendship or pending request between these users")))
                .then(friendshipRepository.deleteByPair(pair.a(), pair.b()));
    }

    public Mono<List<FriendResponse>> listFriends(UUID callerId) {
        return friendshipRepository.findAllForUser(callerId)
                .filter(f -> f.getStatus() == FriendshipStatus.ACCEPTED)
                .collectList()
                .flatMap(friendships -> resolveAndMap(friendships, callerId,
                        (f, username) -> FriendResponse.of(otherId(f, callerId), f.getRespondedAt(), username)));
    }

    public Mono<List<FriendRequestResponse>> listPendingRequests(UUID callerId) {
        return friendshipRepository.findAllForUser(callerId)
                .filter(f -> f.getStatus() == FriendshipStatus.PENDING)
                .collectList()
                .flatMap(friendships -> resolveAndMap(friendships, callerId,
                        (f, username) -> FriendRequestResponse.of(
                                otherId(f, callerId),
                                f.getRequestedBy().equals(callerId) ? FriendRequestDirection.OUTGOING : FriendRequestDirection.INCOMING,
                                f.getRequestedAt(),
                                username)));
    }

    private <T> Mono<List<T>> resolveAndMap(List<Friendship> friendships, UUID callerId, java.util.function.BiFunction<Friendship, String, T> mapper) {
        List<UUID> otherIds = friendships.stream().map(f -> otherId(f, callerId)).collect(Collectors.toList());
        return shieldwallUserClient.resolveUsernames(otherIds)
                .map(usernames -> friendships.stream()
                        .map(f -> mapper.apply(f, usernames.get(otherId(f, callerId))))
                        .collect(Collectors.toList()));
    }

    private static UUID otherId(Friendship friendship, UUID callerId) {
        return friendship.getUserIdA().equals(callerId) ? friendship.getUserIdB() : friendship.getUserIdA();
    }

    private static FriendshipRelation relationOf(Friendship friendship, UUID callerId) {
        if (friendship == null) {
            return FriendshipRelation.NONE;
        }
        if (friendship.getStatus() == FriendshipStatus.ACCEPTED) {
            return FriendshipRelation.FRIENDS;
        }
        return friendship.getRequestedBy().equals(callerId) ? FriendshipRelation.PENDING_OUTGOING : FriendshipRelation.PENDING_INCOMING;
    }

    private record OrderedPair(UUID a, UUID b) {
        static OrderedPair of(UUID x, UUID y) {
            return x.compareTo(y) <= 0 ? new OrderedPair(x, y) : new OrderedPair(y, x);
        }
    }
}
