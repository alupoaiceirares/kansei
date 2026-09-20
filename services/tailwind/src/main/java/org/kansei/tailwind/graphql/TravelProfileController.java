package org.kansei.tailwind.graphql;

import org.kansei.tailwind.service.TravelProfileService;
import org.kansei.tailwind.stats.StatsModels;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Each field is its own resolver, so Spring GraphQL only runs the ones a query actually asks for. A small
 * dashboard query and the full stats page then cost different amounts, which is the point of GraphQL here.
 */
@Controller
public class TravelProfileController {

    private final TravelProfileService travelProfileService;

    public TravelProfileController(TravelProfileService travelProfileService) {
        this.travelProfileService = travelProfileService;
    }

    /**
     * Without userId this is the caller's own profile. With one it is that user's, already narrowed to what the
     * caller may see: a friend's friends-only flights, a stranger's public ones.
     */
    @QueryMapping
    public TravelProfileRoot travelProfile(@ContextValue("userId") UUID callerId, @Argument("userId") UUID profileUserId,
                                           @Argument PeriodInput period) {
        travelProfileService.requireAccess(callerId);
        UUID owner = profileUserId == null ? callerId : profileUserId;
        travelProfileService.requireProfileExists(owner);
        return new TravelProfileRoot(callerId, owner, toPeriod(period));
    }

    @SchemaMapping(typeName = "TravelProfile")
    public StatsModels.TravelStats stats(TravelProfileRoot root) {
        return travelProfileService.stats(root.viewerId(), root.ownerId(), root.period());
    }

    @SchemaMapping(typeName = "TravelProfile")
    public StatsModels.VisitedCountries countries(TravelProfileRoot root) {
        return travelProfileService.countries(root.viewerId(), root.ownerId(), root.period());
    }

    @SchemaMapping(typeName = "TravelProfile")
    public List<StatsModels.AirportVisit> airports(TravelProfileRoot root) {
        return travelProfileService.airports(root.viewerId(), root.ownerId(), root.period());
    }

    @SchemaMapping(typeName = "TravelProfile")
    public List<StatsModels.AircraftFamilyCollection> aircraft(TravelProfileRoot root) {
        return travelProfileService.aircraft(root.viewerId(), root.ownerId(), root.period());
    }

    @SchemaMapping(typeName = "TravelProfile")
    public List<StatsModels.AirlineCount> airlines(TravelProfileRoot root) {
        return travelProfileService.airlines(root.viewerId(), root.ownerId(), root.period());
    }

    @SchemaMapping(typeName = "TravelProfile")
    public StatsModels.TravelRecords records(TravelProfileRoot root) {
        return travelProfileService.records(root.viewerId(), root.ownerId(), root.period());
    }

    private static StatsModels.Period toPeriod(PeriodInput input) {
        if (input == null) {
            return StatsModels.Period.ALL_TIME;
        }
        if (input.from() != null && input.to() != null && input.from().isAfter(input.to())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "period.from must not be after period.to");
        }
        return new StatsModels.Period(input.from(), input.to());
    }

    public record PeriodInput(LocalDate from, LocalDate to) {
    }
}
