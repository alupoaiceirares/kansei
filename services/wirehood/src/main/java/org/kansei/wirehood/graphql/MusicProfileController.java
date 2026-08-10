package org.kansei.wirehood.graphql;

import org.kansei.wirehood.dto.GenreBreakdownEntry;
import org.kansei.wirehood.service.MusicProfileService;
import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * @Controller + @QueryMapping/@SchemaMapping, not @RestController
 * Each field below is its own @SchemaMapping data fetcher rather than one method building the whole MusicProfile at once, Spring GraphQL only invokes a field's resolver if the incoming query actually asks for it
 * A compact-card query and a full-stats-page query only pay for what they request. That laziness is the actual point of choosing GraphQL here
 */
@Controller
public class MusicProfileController {

    private final MusicProfileService musicProfileService;

    public MusicProfileController(MusicProfileService musicProfileService) {
        this.musicProfileService = musicProfileService;
    }

    // No DB work here, just carries userId (resolved by GraphQlAuthInterceptor) to the field resolvers below
    @QueryMapping
    public MusicProfileRoot musicProfile(@ContextValue("userId") UUID userId) {
        return new MusicProfileRoot(userId);
    }

    @SchemaMapping(typeName = "MusicProfile")
    public Mono<Long> totalTracksSaved(MusicProfileRoot root) {
        return musicProfileService.totalTracksSaved(root.userId());
    }

    @SchemaMapping(typeName = "MusicProfile")
    public Mono<List<GenreBreakdownEntry>> genreBreakdown(MusicProfileRoot root) {
        return musicProfileService.genreBreakdown(root.userId());
    }

    @SchemaMapping(typeName = "MusicProfile")
    public Mono<String> mostDownloadedArtist(MusicProfileRoot root) {
        return musicProfileService.mostDownloadedArtist(root.userId());
    }

    @SchemaMapping(typeName = "MusicProfile")
    public Mono<Long> playlistsOwned(MusicProfileRoot root) {
        return musicProfileService.playlistsOwned(root.userId());
    }

    @SchemaMapping(typeName = "MusicProfile")
    public Mono<Long> playlistsCollaborated(MusicProfileRoot root) {
        return musicProfileService.playlistsCollaborated(root.userId());
    }

    @SchemaMapping(typeName = "MusicProfile")
    public Mono<Long> friendCount(MusicProfileRoot root) {
        return musicProfileService.friendCount(root.userId());
    }
}
