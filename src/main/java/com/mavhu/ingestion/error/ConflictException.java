package com.mavhu.ingestion.error;

/** The request is valid but the resource is in the wrong state for it; maps to 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
