package com.mavhu.ingestion.processing;

import java.time.OffsetDateTime;
import java.util.UUID;

public record IngestionJob(
        UUID jobId,
        UUID fileId,
        Integer customerId,
        FileType fileType,
        String s3Path,
        String originalFileName,
        IngestionJobStatus status,
        Integer rowsIngested,
        String errorMessage,
        OffsetDateTime createdAt,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt
) {
}
