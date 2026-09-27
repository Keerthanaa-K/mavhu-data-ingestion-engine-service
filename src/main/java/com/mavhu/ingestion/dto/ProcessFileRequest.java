package com.mavhu.ingestion.dto;

import java.util.UUID;

/**
 * Request to process a file already in S3.
 *
 * @param jobId optional; the upload flow passes the job it created. When absent, the latest job for
 *              s3Path is re-processed, or a new job is created.
 */
public record ProcessFileRequest(UUID jobId, Integer customerId, String s3Path, String fileType) {
}
