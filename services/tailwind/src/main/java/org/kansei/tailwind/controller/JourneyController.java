package org.kansei.tailwind.controller;

import jakarta.validation.Valid;
import org.kansei.tailwind.dto.JourneyRequest;
import org.kansei.tailwind.dto.JourneyResponse;
import org.kansei.tailwind.dto.SetVisibilityRequest;
import org.kansei.tailwind.service.JourneyService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The user's own journeys. Visibility is set on each flight, the "set all" call is only a shortcut over them.
 */
@RestController
@RequestMapping("/tailwind/journeys")
public class JourneyController {

    private final JourneyService journeyService;

    public JourneyController(JourneyService journeyService) {
        this.journeyService = journeyService;
    }

    @GetMapping
    public List<JourneyResponse> list(@RequestHeader("X-User-Id") UUID userId) {
        return journeyService.list(userId);
    }

    // Someone else's journeys, each cut down to the flights this viewer may see
    @GetMapping("/users/{ownerId}")
    public List<JourneyResponse> ofUser(@RequestHeader("X-User-Id") UUID userId, @PathVariable UUID ownerId) {
        return journeyService.listVisible(userId, ownerId);
    }

    @GetMapping("/{journeyId}")
    public JourneyResponse get(@RequestHeader("X-User-Id") UUID userId, @PathVariable Long journeyId) {
        return journeyService.get(userId, journeyId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JourneyResponse create(@RequestHeader("X-User-Id") UUID userId, @Valid @RequestBody JourneyRequest request) {
        return journeyService.create(userId, request);
    }

    @PatchMapping("/{journeyId}")
    public JourneyResponse update(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable Long journeyId,
            @Valid @RequestBody JourneyRequest request
    ) {
        return journeyService.update(userId, journeyId, request);
    }

    // Removes the journey and every flight entry in it
    @DeleteMapping("/{journeyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestHeader("X-User-Id") UUID userId, @PathVariable Long journeyId) {
        journeyService.delete(userId, journeyId);
    }

    @PostMapping("/{journeyId}/visibility")
    public JourneyResponse setVisibility(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable Long journeyId,
            @Valid @RequestBody SetVisibilityRequest request
    ) {
        return journeyService.setVisibility(userId, journeyId, request.visibility());
    }
}
