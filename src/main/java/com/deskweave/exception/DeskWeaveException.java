package com.deskweave.exception;

/**
 * Base checked exception for domain and business-rule failures in DeskWeave.
 */
public class DeskWeaveException extends Exception {

    public DeskWeaveException(String message) {
        super(message);
    }

    public DeskWeaveException(String message, Throwable cause) {
        super(message, cause);
    }
}
