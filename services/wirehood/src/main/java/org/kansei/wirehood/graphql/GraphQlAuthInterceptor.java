package org.kansei.wirehood.graphql;

import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.UUID;

@Component
public class GraphQlAuthInterceptor implements WebGraphQlInterceptor {

    @Override
    public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
        String header = request.getHeaders().getFirst("X-User-Id");
        if (header == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing X-User-Id"));
        }

        UUID userId;
        try {
            userId = UUID.fromString(header);
        } catch (IllegalArgumentException ex) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Malformed X-User-Id"));
        }

        request.configureExecutionInput((executionInput, builder) ->
                builder.graphQLContext(Collections.singletonMap("userId", userId)).build());
        return chain.next(request);
    }
}
