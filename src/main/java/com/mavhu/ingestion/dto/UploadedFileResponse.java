package com.mavhu.ingestion.dto;

import com.mavhu.ingestion.files.ApprovalStatus;
import com.mavhu.ingestion.files.UploadedFile;
import com.mavhu.ingestion.files.UploadedFileDetails;
import com.mavhu.ingestion.processing.IngestionJobStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UploadedFileResponse(
        UUID fileId,
        Integer customerId,
        String fileType,
        String targetTable,
        String originalFileName,
        String s3Path,
        Long sizeBytes,
        OffsetDateTime uploadedAt,
        UserRef uploadedBy,
        ApprovalStatus approvalStatus,
        boolean isApproved,
        UserRef reviewedBy,
        OffsetDateTime reviewedAt,
        String reviewComment,
        Ingestion ingestion
) {

    public record UserRef(Integer userId, String name) {
    }

    public record Ingestion(UUID jobId, IngestionJobStatus status, Integer rowsIngested, String errorMessage) {
    }

    public static UploadedFileResponse from(UploadedFileDetails details) {
        UploadedFile f = details.file();
        return new UploadedFileResponse(f.fileId(), f.customerId(), f.fileType().name(), f.fileType().tableName(),
                f.originalFileName(), f.s3Path(), f.sizeBytes(), f.uploadedAt(),
                userRef(f.uploadedByUserId(), details.uploadedByName()),
                f.approvalStatus(), f.isApproved(),
                userRef(f.reviewedByUserId(), details.reviewedByName()), f.reviewedAt(), f.reviewComment(),
                details.jobId() == null ? null : new Ingestion(details.jobId(), details.ingestionStatus(),
                        details.rowsIngested(), details.ingestionError()));
    }

    private static UserRef userRef(Integer userId, String name) {
        return userId == null ? null : new UserRef(userId, name);
    }
}
