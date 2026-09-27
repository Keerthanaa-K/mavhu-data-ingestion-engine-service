package com.mavhu.ingestion.files;

import com.mavhu.ingestion.processing.FileType;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A row of mavhu.uploaded_files. uploadedByUserId is null only for files ingested before user
 * attribution existed.
 */
public record UploadedFile(
        UUID fileId,
        Integer customerId,
        Integer uploadedByUserId,
        FileType fileType,
        String originalFileName,
        String contentType,
        Long sizeBytes,
        String s3Path,
        ApprovalStatus approvalStatus,
        boolean isApproved,
        Integer reviewedByUserId,
        OffsetDateTime reviewedAt,
        String reviewComment,
        OffsetDateTime uploadedAt
) {
}
