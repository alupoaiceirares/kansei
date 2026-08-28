package org.kansei.shieldwall.exception;

/**
 * The current-password confirmation on an already-authenticated self-service request (change password, deactivate account) didn't match.
 * Deliberately distinct from {@link InvalidCredentialsException} (401, "the bearer token itself isn't valid") as this is a 400: the JWT is fine, one field in the request body was wrong.
 * Callers (control-tower's gateway rejection and this) both returning 401 with the same shape would otherwise be indistinguishable to a client that needs to tell "your session died" apart from "you typed the wrong password"
 */
public class InvalidCurrentPasswordException extends RuntimeException {
    public InvalidCurrentPasswordException() {
        super("Current password is incorrect");
    }
}
