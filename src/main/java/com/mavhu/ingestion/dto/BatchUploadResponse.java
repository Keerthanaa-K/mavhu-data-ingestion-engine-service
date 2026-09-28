package com.mavhu.ingestion.dto;

import java.util.List;

/**
 * Result of a batch upload: one entry per file, in the order the files were sent. Each file gets
 * its own fileId and ingestion job, and is processed and reviewed independently.
 */
public record BatchUploadResponse(
        Integer customerId,
        String fileType,
        int fileCount,
        List<FileUploadResponse> files
) {
}
