package org.kansei.wirehood.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Only handles @Valid request-body failures - matches shieldwall's {timestamp,status,errors}
 * validation-error shape for consistency across services. Every other error in wirehood stays an
 * inline ResponseStatusException with no handler here, deliberately - this isn't a general global
 * exception handler, just the one gap Bean Validation itself needs filled in.
 */
@RestControllerAdvice
public class WirehoodValidationExceptionHandler {

    @ExceptionHandler(WebExchangeBindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleValidation(WebExchangeBindException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getFieldErrors().forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("errors", errors);
        return body;
    }
}
