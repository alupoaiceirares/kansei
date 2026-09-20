package org.kansei.tailwind.graphql;

import org.kansei.tailwind.stats.StatsModels;

import java.util.UUID;

/**
 * Carries the viewer, the profile owner and the period from the query down to the field resolvers. Owner and
 * viewer are the same until friend profiles arrive in the social phase.
 */
public record TravelProfileRoot(UUID viewerId, UUID ownerId, StatsModels.Period period) {
}
