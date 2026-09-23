package org.kansei.tailwind.service;

import org.kansei.tailwind.client.ShieldwallUserClient;
import org.kansei.tailwind.dto.FlightJoinRequestResponse;
import org.kansei.tailwind.dto.FlightJoinRequestResponse.Direction;
import org.kansei.tailwind.dto.FlightResponse;
import org.kansei.tailwind.dto.JoinFlightResult;
import org.kansei.tailwind.dto.SharedFlightFriend;
import org.kansei.tailwind.model.FlightJoinRequest;
import org.kansei.tailwind.model.FlightSource;
import org.kansei.tailwind.model.UserFlight;
import org.kansei.tailwind.repository.FlightJoinRequestRepository;
import org.kansei.tailwind.repository.TailwindUserRepository;
import org.kansei.tailwind.repository.UserFlightRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Flights two people took together. Shared flights are only shown between friends. "I was on this too" adds a
 * friend's looked-up flight straight away, anything else (a stranger's public flight, a friend's own manual
 * flight) waits for the owner's approval.
 */
@Service
@Transactional
public class FlightShareService {

    private final UserFlightRepository userFlightRepository;
    private final FlightJoinRequestRepository joinRequestRepository;
    private final TailwindUserRepository tailwindUserRepository;
    private final UserFlightService userFlightService;
    private final ViewerAccess viewerAccess;
    private final OptInGuard optInGuard;
    private final ShieldwallUserClient shieldwallUserClient;
    private final Clock clock;

    public FlightShareService(UserFlightRepository userFlightRepository, FlightJoinRequestRepository joinRequestRepository,
                              TailwindUserRepository tailwindUserRepository, UserFlightService userFlightService, ViewerAccess viewerAccess,
                              OptInGuard optInGuard, ShieldwallUserClient shieldwallUserClient, Clock clock) {
        this.userFlightRepository = userFlightRepository;
        this.joinRequestRepository = joinRequestRepository;
        this.tailwindUserRepository = tailwindUserRepository;
        this.userFlightService = userFlightService;
        this.viewerAccess = viewerAccess;
        this.optInGuard = optInGuard;
        this.shieldwallUserClient = shieldwallUserClient;
        this.clock = clock;
    }

    // Friends who logged the same flight and let the caller see that entry, "you and X were on this flight"
    @Transactional(readOnly = true)
    public List<SharedFlightFriend> sharedWith(UUID userId, Long userFlightId) {
        UserFlight own = userFlightRepository.findDetailedByIdAndUserId(userFlightId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Flight not found in your log"));
        Set<UUID> friends = new HashSet<>();
        for (UserFlight other : userFlightRepository.findDetailedByFlightIdExcludingUser(own.getFlight().getId(), userId)) {
            if (viewerAccess.areFriends(userId, other.getUserId())
                    && viewerAccess.visibleTo(userId, other.getUserId()).contains(other.getVisibility())) {
                friends.add(other.getUserId());
            }
        }
        if (friends.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> names = shieldwallUserClient.resolveUsernames(friends);
        return friends.stream().map(id -> new SharedFlightFriend(id, names.get(id)))
                .sorted((a, b) -> String.valueOf(a.username()).compareToIgnoreCase(String.valueOf(b.username())))
                .toList();
    }

    public JoinFlightResult join(UUID userId, Long ownerUserFlightId) {
        optInGuard.require(userId);
        UserFlight target = userFlightRepository.findDetailedById(ownerUserFlightId)
                .filter(uf -> !uf.getUserId().equals(userId))
                .filter(uf -> viewerAccess.visibleTo(userId, uf.getUserId()).contains(uf.getVisibility()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Flight not found"));
        if (userFlightRepository.existsByUserIdAndFlightId(userId, target.getFlight().getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This flight is already in your log");
        }

        // A looked-up flight already exists for everyone, between friends there is nothing to approve
        if (target.getFlight().getSource() == FlightSource.API && viewerAccess.areFriends(userId, target.getUserId())) {
            return new JoinFlightResult(JoinFlightResult.Outcome.ADDED, userFlightService.addShared(userId, target.getFlight()));
        }
        if (joinRequestRepository.existsByRequesterIdAndUserFlight_Id(userId, target.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You already asked to join this flight");
        }
        try {
            joinRequestRepository.saveAndFlush(FlightJoinRequest.builder()
                    .requesterId(userId)
                    .userFlight(target)
                    .requestedAt(clock.instant())
                    .build());
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You already asked to join this flight");
        }
        return new JoinFlightResult(JoinFlightResult.Outcome.REQUESTED, null);
    }

    @Transactional(readOnly = true)
    public List<FlightJoinRequestResponse> requests(UUID userId) {
        List<FlightJoinRequest> incoming = joinRequestRepository.findIncoming(userId);
        List<FlightJoinRequest> outgoing = joinRequestRepository.findOutgoing(userId);
        Set<UUID> others = new HashSet<>();
        incoming.forEach(r -> others.add(r.getRequesterId()));
        outgoing.forEach(r -> others.add(r.getUserFlight().getUserId()));
        Map<UUID, String> names = others.isEmpty() ? Map.of() : shieldwallUserClient.resolveUsernames(others);

        Instant now = clock.instant();
        List<FlightJoinRequestResponse> result = new ArrayList<>();
        for (FlightJoinRequest r : incoming) {
            result.add(view(r, Direction.INCOMING, r.getRequesterId(), names, now));
        }
        for (FlightJoinRequest r : outgoing) {
            result.add(view(r, Direction.OUTGOING, r.getUserFlight().getUserId(), names, now));
        }
        return result;
    }

    // The owner approves: the requester gets the same flight row in a new journey. A requester who left or already has it is skipped
    public void accept(UUID ownerId, Long requestId) {
        FlightJoinRequest request = requireIncoming(ownerId, requestId);
        UUID requesterId = request.getRequesterId();
        var flight = request.getUserFlight().getFlight();
        joinRequestRepository.delete(request);
        boolean stillHere = tailwindUserRepository.findById(requesterId).map(user -> user.isEnabled()).orElse(false);
        if (stillHere && !userFlightRepository.existsByUserIdAndFlightId(requesterId, flight.getId())) {
            userFlightService.addShared(requesterId, flight);
        }
    }

    public void decline(UUID ownerId, Long requestId) {
        joinRequestRepository.delete(requireIncoming(ownerId, requestId));
    }

    public void withdraw(UUID requesterId, Long requestId) {
        FlightJoinRequest request = joinRequestRepository.findById(requestId)
                .filter(r -> r.getRequesterId().equals(requesterId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
        joinRequestRepository.delete(request);
    }

    private FlightJoinRequest requireIncoming(UUID ownerId, Long requestId) {
        return joinRequestRepository.findById(requestId)
                .filter(r -> r.getUserFlight().getUserId().equals(ownerId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
    }

    private static FlightJoinRequestResponse view(FlightJoinRequest r, Direction direction, UUID other, Map<UUID, String> names, Instant now) {
        return new FlightJoinRequestResponse(r.getId(), direction, other, names.get(other), r.getUserFlight().getId(),
                FlightResponse.of(r.getUserFlight().getFlight(), now), r.getRequestedAt());
    }
}
