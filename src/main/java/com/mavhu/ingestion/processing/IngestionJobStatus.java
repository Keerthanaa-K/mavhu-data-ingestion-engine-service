package com.mavhu.ingestion.processing;

public enum IngestionJobStatus {
    /** File is in S3; processing has not started yet. */
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}
