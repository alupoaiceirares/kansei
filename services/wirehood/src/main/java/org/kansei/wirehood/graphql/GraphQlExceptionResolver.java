package org.kansei.wirehood.graphql;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * GraphQL responses don't carry HTTP status codes the way REST does, so a bare ResponseStatusException thrown from a @SchemaMapping method would otherwise surface as an opaque generic INTERNAL_ERROR instead of a well-formed classified error
 * This is GraphQL-response-shape infrastructure, not a reintroduction of wirehood's REST convention of "no global exception handler", it's the one place that convention structurally can't apply.
 */
@Component
public class GraphQlExceptionResolver extends DataFetcherExceptionResolverAdapter {

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        if (ex instanceof ResponseStatusException rse) {
            return GraphqlErrorBuilder.newError(env)
                    .message(rse.getReason())
                    .errorType(mapErrorType(rse.getStatusCode()))
                    .build();
        }
        return null;
    }

    private ErrorType mapErrorType(HttpStatusCode status) {
        if (status.equals(org.springframework.http.HttpStatus.NOT_FOUND)) {
            return ErrorType.NOT_FOUND;
        }
        if (status.equals(org.springframework.http.HttpStatus.FORBIDDEN)) {
            return ErrorType.FORBIDDEN;
        }
        if (status.equals(org.springframework.http.HttpStatus.UNAUTHORIZED)) {
            return ErrorType.UNAUTHORIZED;
        }
        if (status.equals(org.springframework.http.HttpStatus.BAD_REQUEST)) {
            return ErrorType.BAD_REQUEST;
        }
        return ErrorType.INTERNAL_ERROR;
    }
}
