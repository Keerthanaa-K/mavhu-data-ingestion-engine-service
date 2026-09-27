package com.mavhu.ingestion.dto;

import com.mavhu.ingestion.processing.IngestionJob;
import com.mavhu.ingestion.processing.IngestionJobStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record IngestionJobResponse(
        UUID jobId,
        UUID fileId,
        Integer customerId,
        String fileType,
        String targetTable,
        String s3Path,
        String originalFileName,
        IngestionJobStatus status,
        Integer rowsIngested,
        String errorMessage,
        OffsetDateTime createdAt,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt
) {

    public static IngestionJobResponse from(IngestionJob job) {
        return new IngestionJobResponse(job.jobId(), job.fileId(), job.customerId(), job.fileType().name(),
                job.fileType().tableName(), job.s3Path(), job.originalFileName(), job.status(),
                job.rowsIngested(), job.errorMessage(), job.createdAt(), job.startedAt(), job.completedAt());
    }
}
