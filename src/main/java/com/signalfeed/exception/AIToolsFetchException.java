package com.signalfeed.exception;

public class AIToolsFetchException extends RuntimeException {

    public AIToolsFetchException(String message) {
        super(message);
    }

    public AIToolsFetchException(String message, Throwable cause) {
        super(message, cause);
    }
}
