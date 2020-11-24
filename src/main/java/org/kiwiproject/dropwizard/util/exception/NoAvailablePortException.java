package org.kiwiproject.dropwizard.util.exception;

/**
 * Exception indicating that all attempts to find an open part have been exhausted.
 */
public class NoAvailablePortException extends RuntimeException {
    public NoAvailablePortException(String message) {
        super(message);
    }
}
