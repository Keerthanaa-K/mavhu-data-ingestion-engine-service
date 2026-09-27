package com.mavhu.ingestion.files;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

public enum ApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED;

    /** Case-insensitive; throws IllegalArgumentException (400) listing valid values. */
    public static ApprovalStatus fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("status is required. Supported: " + supported());
        }
        return Arrays.stream(values())
                .filter(s -> s.name().equals(value.trim().toUpperCase(Locale.ROOT)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported status '" + value + "'. Supported: " + supported()));
    }

    private static String supported() {
        return Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", "));
    }
}
