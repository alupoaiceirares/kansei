package org.kansei.wirehood.controller;

import org.kansei.wirehood.dto.FriendRequestResponse;
import org.kansei.wirehood.dto.FriendResponse;
import org.kansei.wirehood.dto.FriendSearchResult;
import org.kansei.wirehood.dto.SendFriendRequestRequest;
import org.kansei.wirehood.service.FriendshipService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/wirehood/friends")
public class FriendController {

    private final FriendshipService friendshipService;

    public FriendController(FriendshipService friendshipService) {
        this.friendshipService = friendshipService;
    }

    @GetMapping("/search")
    public Mono<List<FriendSearchResult>> search(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam String query,
            @RequestParam(required = false) Integer limit
    ) {
        return friendshipService.search(userId, query, limit);
    }

    @GetMapping
    public Mono<List<FriendResponse>> listFriends(@RequestHeader("X-User-Id") UUID userId) {
        return friendshipService.listFriends(userId);
    }

    @GetMapping("/requests")
    public Mono<List<FriendRequestResponse>> listPendingRequests(@RequestHeader("X-User-Id") UUID userId) {
        return friendshipService.listPendingRequests(userId);
    }

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> sendRequest(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody SendFriendRequestRequest request
    ) {
        return friendshipService.sendRequest(userId, request.userId());
    }

    @PostMapping("/requests/{otherUserId}/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> accept(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID otherUserId
    ) {
        return friendshipService.accept(userId, otherUserId);
    }

    // Covers cancel (still-pending outgoing request), decline (still-pending incoming request), and unfriend (accepted) uniformly
    @DeleteMapping("/{otherUserId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> remove(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID otherUserId
    ) {
        return friendshipService.remove(userId, otherUserId);
    }
}
