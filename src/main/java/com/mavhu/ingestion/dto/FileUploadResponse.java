package com.mavhu.ingestion.dto;

import com.mavhu.ingestion.files.ApprovalStatus;
import com.mavhu.ingestion.processing.IngestionJobStatus;

import java.util.UUID;

public record FileUploadResponse(
        UUID fileId,
        Integer customerId,
        Integer uploadedByUserId,
        String fileType,
        String originalFileName,
        String bucket,
        String key,
        String s3Path,
        long size,
        ApprovalStatus approvalStatus,
        UUID jobId,
        IngestionJobStatus jobStatus
) {
}
