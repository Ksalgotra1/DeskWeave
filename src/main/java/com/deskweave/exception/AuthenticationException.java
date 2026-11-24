package com.deskweave.exception;

/**
 * Thrown when user credentials are invalid or an action violates role permissions.
 */
public class AuthenticationException extends DeskWeaveException {

    public AuthenticationException(String message) {
        super(message);
    }
}
