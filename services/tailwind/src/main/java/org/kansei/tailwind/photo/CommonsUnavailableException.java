package org.kansei.tailwind.photo;

public class CommonsUnavailableException extends RuntimeException {

    public CommonsUnavailableException(String message) {
        super(message);
    }

    public CommonsUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
