package org.kansei.shieldwall.exception;

public class InvalidOrExpiredTokenException extends RuntimeException {
    public InvalidOrExpiredTokenException() {
        super("Invalid or expired token");
    }
}
