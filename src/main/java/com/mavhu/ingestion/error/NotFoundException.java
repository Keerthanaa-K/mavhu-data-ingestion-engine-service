package com.mavhu.ingestion.error;

/** Maps to 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
