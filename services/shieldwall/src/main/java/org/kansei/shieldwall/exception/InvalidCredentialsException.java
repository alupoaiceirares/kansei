package org.kansei.shieldwall.exception;

/**
 * Generic - used both for "email not found" and "wrong password" as to hide which mails are registered by comparison of error messages.
 * Disable account step included
 */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}