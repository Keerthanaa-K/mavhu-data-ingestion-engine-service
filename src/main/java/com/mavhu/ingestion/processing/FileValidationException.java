package com.mavhu.ingestion.processing;

import java.util.List;

/**
 * The uploaded file does not match what its file type expects (missing headers, unparseable values).
 */
public class FileValidationException extends RuntimeException {

    private static final int MAX_ERRORS_IN_MESSAGE = 20;

    private final List<String> errors;

    public FileValidationException(List<String> errors) {
        super(buildMessage(errors));
        this.errors = List.copyOf(errors);
    }

    public FileValidationException(String error) {
        this(List.of(error));
    }

    public List<String> getErrors() {
        return errors;
    }

    private static String buildMessage(List<String> errors) {
        String shown = String.join("; ", errors.subList(0, Math.min(errors.size(), MAX_ERRORS_IN_MESSAGE)));
        int hidden = errors.size() - MAX_ERRORS_IN_MESSAGE;
        return hidden > 0 ? shown + "; ... and " + hidden + " more" : shown;
    }
}
