package org.kansei.tailwind.graphql;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * A GraphQL response carries no HTTP status, so a ResponseStatusException from a resolver would surface as an
 * opaque INTERNAL_ERROR. This turns it into a classified error with its real message.
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
        if (status.equals(HttpStatus.NOT_FOUND)) {
            return ErrorType.NOT_FOUND;
        }
        if (status.equals(HttpStatus.FORBIDDEN)) {
            return ErrorType.FORBIDDEN;
        }
        if (status.equals(HttpStatus.UNAUTHORIZED)) {
            return ErrorType.UNAUTHORIZED;
        }
        if (status.equals(HttpStatus.BAD_REQUEST)) {
            return ErrorType.BAD_REQUEST;
        }
        return ErrorType.INTERNAL_ERROR;
    }
}
