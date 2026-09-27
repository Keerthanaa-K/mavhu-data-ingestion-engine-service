package com.mavhu.ingestion.files;

import com.mavhu.ingestion.processing.IngestionJobStatus;

import java.util.UUID;

/**
 * An uploaded file with the names of the users involved and the state of its ingestion job.
 */
public record UploadedFileDetails(
        UploadedFile file,
        String uploadedByName,
        String reviewedByName,
        UUID jobId,
        IngestionJobStatus ingestionStatus,
        Integer rowsIngested,
        String ingestionError
) {
}
