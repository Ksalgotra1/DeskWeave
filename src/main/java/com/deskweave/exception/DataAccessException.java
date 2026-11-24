package com.deskweave.exception;

/**
 * Unchecked wrapper for JDBC and persistence-layer errors.
 */
public class DataAccessException extends RuntimeException {

    public DataAccessException(String message) {
        super(message);
    }

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
