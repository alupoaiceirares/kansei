package org.kansei.tailwind.graphql;

import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseValueException;
import graphql.schema.GraphQLScalarType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * ISO local date (2026-09-12) for the Date scalar, the same shape the REST side uses.
 */
@Configuration
public class GraphQlDateScalarConfig {

    @Bean
    public RuntimeWiringConfigurer dateScalarConfigurer() {
        return wiringBuilder -> wiringBuilder.scalar(GraphQLScalarType.newScalar()
                .name("Date")
                .description("An ISO-8601 local date, for example 2026-09-12")
                .coercing(new Coercing<LocalDate, String>() {
                    @Override
                    public String serialize(Object dataFetcherResult) {
                        return dataFetcherResult.toString();
                    }

                    @Override
                    public LocalDate parseValue(Object input) {
                        try {
                            return LocalDate.parse(input.toString());
                        } catch (DateTimeParseException ex) {
                            throw new CoercingParseValueException("Not an ISO date: " + input);
                        }
                    }

                    @Override
                    public LocalDate parseLiteral(Object input) {
                        if (input instanceof StringValue stringValue) {
                            return parseValue(stringValue.getValue());
                        }
                        throw new CoercingParseValueException("Date must be given as a string");
                    }
                })
                .build());
    }
}
