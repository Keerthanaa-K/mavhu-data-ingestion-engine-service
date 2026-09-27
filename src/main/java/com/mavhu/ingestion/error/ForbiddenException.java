package com.mavhu.ingestion.error;

/** The user is known but not allowed to do this; maps to 403. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
