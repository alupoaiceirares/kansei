package org.kansei.wirehood.controller;

import org.kansei.wirehood.dto.FavoriteResponse;
import org.kansei.wirehood.dto.PageResponse;
import org.kansei.wirehood.service.FavoriteService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    // Idempotent, one favorite per (user, format)
    @PostMapping("/wirehood/track-formats/{trackFormatId}/favorite")
    public Mono<Void> favorite(@RequestHeader("X-User-Id") UUID userId, @PathVariable UUID trackFormatId) {
        return favoriteService.favorite(userId, trackFormatId);
    }

    @DeleteMapping("/wirehood/track-formats/{trackFormatId}/favorite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> unfavorite(@RequestHeader("X-User-Id") UUID userId, @PathVariable UUID trackFormatId) {
        return favoriteService.unfavorite(userId, trackFormatId);
    }

    @GetMapping("/wirehood/favorites")
    public Mono<PageResponse<FavoriteResponse>> myFavorites(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return favoriteService.list(userId, page, size);
    }
}
