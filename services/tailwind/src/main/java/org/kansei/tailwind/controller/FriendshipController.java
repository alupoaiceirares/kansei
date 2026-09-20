package org.kansei.tailwind.controller;

import org.kansei.tailwind.dto.FriendRequestResponse;
import org.kansei.tailwind.dto.FriendResponse;
import org.kansei.tailwind.dto.UserSearchResult;
import org.kansei.tailwind.service.FriendshipService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Friend search, requests and the friend list. Only opted-in users take part.
 */
@RestController
@RequestMapping("/tailwind/friends")
public class FriendshipController {

    private final FriendshipService friendshipService;

    public FriendshipController(FriendshipService friendshipService) {
        this.friendshipService = friendshipService;
    }

    @GetMapping("/search")
    public List<UserSearchResult> search(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam("q") String query,
            @RequestParam(required = false) Integer limit
    ) {
        return friendshipService.search(userId, query, limit);
    }

    @GetMapping
    public List<FriendResponse> friends(@RequestHeader("X-User-Id") UUID userId) {
        return friendshipService.friends(userId);
    }

    // Both directions, so the UI can show what is waiting on them and what they already sent
    @GetMapping("/requests")
    public List<FriendRequestResponse> requests(@RequestHeader("X-User-Id") UUID userId) {
        return friendshipService.pendingRequests(userId);
    }

    // Sending back a request that was already sent to you accepts it
    @PostMapping("/requests/{targetUserId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sendRequest(@RequestHeader("X-User-Id") UUID userId, @PathVariable UUID targetUserId) {
        friendshipService.sendRequest(userId, targetUserId);
    }

    @PostMapping("/requests/{requesterId}/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void accept(@RequestHeader("X-User-Id") UUID userId, @PathVariable UUID requesterId) {
        friendshipService.accept(userId, requesterId);
    }

    @PostMapping("/requests/{requesterId}/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decline(@RequestHeader("X-User-Id") UUID userId, @PathVariable UUID requesterId) {
        friendshipService.decline(userId, requesterId);
    }

    // Unfriend, or withdraw a request you sent
    @DeleteMapping("/{otherUserId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@RequestHeader("X-User-Id") UUID userId, @PathVariable UUID otherUserId) {
        friendshipService.remove(userId, otherUserId);
    }
}
