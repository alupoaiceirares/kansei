package org.kansei.tailwind.service;

import org.kansei.tailwind.dto.JourneyRequest;
import org.kansei.tailwind.dto.JourneyResponse;
import org.kansei.tailwind.model.Journey;
import org.kansei.tailwind.model.UserFlight;
import org.kansei.tailwind.model.Visibility;
import org.kansei.tailwind.repository.FlightRepository;
import org.kansei.tailwind.repository.JourneyRepository;
import org.kansei.tailwind.repository.UserFlightRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The user's own journeys. Every method is scoped to the caller, someone else's journey looks like it does not exist.
 */
@Service
@Transactional
public class JourneyService {

    private final JourneyRepository journeyRepository;
    private final UserFlightRepository userFlightRepository;
    private final FlightRepository flightRepository;
    private final JourneyViews journeyViews;
    private final OptInGuard optInGuard;
    private final Clock clock;

    public JourneyService(JourneyRepository journeyRepository, UserFlightRepository userFlightRepository, FlightRepository flightRepository,
                          JourneyViews journeyViews, OptInGuard optInGuard, Clock clock) {
        this.journeyRepository = journeyRepository;
        this.userFlightRepository = userFlightRepository;
        this.flightRepository = flightRepository;
        this.journeyViews = journeyViews;
        this.optInGuard = optInGuard;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<JourneyResponse> list(UUID userId) {
        Map<Long, List<UserFlight>> flightsByJourney = userFlightRepository.findDetailedByUserId(userId).stream()
                .collect(Collectors.groupingBy(UserFlight::getJourneyId));
        List<JourneyResponse> journeys = new ArrayList<>();
        for (Journey journey : journeyRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId)) {
            journeys.add(journeyViews.journey(journey, flightsByJourney.getOrDefault(journey.getId(), List.of())));
        }
        // Newest trip first by its first flight, empty journeys keep their creation order at the end
        journeys.sort(Comparator.comparing((JourneyResponse j) -> j.flights().isEmpty() ? null : j.flights().get(0).flight().flightDate(),
                Comparator.nullsLast(Comparator.reverseOrder())));
        return journeys;
    }

    @Transactional(readOnly = true)
    public JourneyResponse get(UUID userId, Long journeyId) {
        return view(requireOwned(userId, journeyId));
    }

    public JourneyResponse create(UUID userId, JourneyRequest request) {
        optInGuard.require(userId);
        Journey journey = journeyRepository.save(Journey.builder()
                .userId(userId)
                .title(blankToNull(request.title()))
                .notes(blankToNull(request.notes()))
                .createdAt(clock.instant())
                .build());
        return view(journey);
    }

    public JourneyResponse update(UUID userId, Long journeyId, JourneyRequest request) {
        Journey journey = requireOwned(userId, journeyId);
        if (request.title() != null) {
            journey.setTitle(blankToNull(request.title()));
        }
        if (request.notes() != null) {
            journey.setNotes(blankToNull(request.notes()));
        }
        return view(journeyRepository.save(journey));
    }

    // Removes the journey with its flights, a manual flight is private to its one entry so it goes too
    public void delete(UUID userId, Long journeyId) {
        Journey journey = requireOwned(userId, journeyId);
        List<Long> flightIds = userFlightRepository.findFlightIdsByJourneyId(journeyId);
        userFlightRepository.deleteByJourneyId(journeyId);
        if (!flightIds.isEmpty()) {
            flightRepository.deleteManualByIds(flightIds);
        }
        journeyRepository.delete(journey);
    }

    public JourneyResponse setVisibility(UUID userId, Long journeyId, Visibility visibility) {
        Journey journey = requireOwned(userId, journeyId);
        userFlightRepository.findByJourneyId(journeyId).forEach(uf -> uf.setVisibility(visibility));
        userFlightRepository.flush();
        return view(journey);
    }

    Journey requireOwned(UUID userId, Long journeyId) {
        return journeyRepository.findByIdAndUserId(journeyId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Journey not found"));
    }

    Journey createAutomatic(UUID userId) {
        return journeyRepository.save(Journey.builder().userId(userId).createdAt(clock.instant()).build());
    }

    JourneyResponse view(Journey journey) {
        return journeyViews.journey(journey, userFlightRepository.findDetailedByJourneyId(journey.getId()));
    }

    // An automatic journey (no title, no notes) with nothing left in it has no reason to exist
    void deleteIfEmptyAndPlain(Long journeyId) {
        journeyRepository.findById(journeyId).ifPresent(journey -> {
            boolean plain = journey.getTitle() == null && journey.getNotes() == null;
            if (plain && userFlightRepository.countByJourneyId(journeyId) == 0) {
                journeyRepository.delete(journey);
            }
        });
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
