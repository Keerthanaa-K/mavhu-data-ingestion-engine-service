package com.mavhu.ingestion.processing;

import com.mavhu.ingestion.dto.ProcessFileRequest;
import com.mavhu.ingestion.error.ConflictException;
import com.mavhu.ingestion.files.ApprovalStatus;
import com.mavhu.ingestion.files.UploadedFile;
import com.mavhu.ingestion.files.UploadedFileNotFoundException;
import com.mavhu.ingestion.files.UploadedFileRepository;
import com.mavhu.ingestion.service.FileStorageService;
import com.mavhu.ingestion.service.S3Location;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Downloads an uploaded file from S3, parses it according to its {@link FileType} and ingests
 * the rows into that type's table, tracking progress in mavhu.ingestion_jobs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileProcessingService {

    private final FileStorageService fileStorageService;
    private final IngestionJobRepository jobRepository;
    private final UploadedFileRepository uploadedFileRepository;
    private final ExcelSheetReader excelSheetReader;
    private final EsgRowWriter rowWriter;

    /**
     * Validates the request and resolves the job to run for the uploaded file at s3Path: the given job,
     * else the file's existing job (re-processing replaces its rows), else a new job.
     * Only files still PENDING approval can be (re-)processed.
     */
    public IngestionJob prepare(ProcessFileRequest request) {
        if (request.customerId() == null) {
            throw new IllegalArgumentException("customerId is required");
        }
        FileType fileType = FileType.fromValue(request.fileType());
        S3Location location = S3Location.parse(request.s3Path());
        fileStorageService.requireOwnBucket(location);
        if (!location.key().startsWith(FileStorageService.customerPrefix(request.customerId()))) {
            throw new IllegalArgumentException("s3Path does not belong to customer " + request.customerId());
        }

        IngestionJob requestedJob = request.jobId() == null ? null : jobRepository.findById(request.jobId())
                .orElseThrow(() -> new IngestionJobNotFoundException(request.jobId()));

        UploadedFile file = uploadedFileRepository.findByS3Path(location.toString())
                .orElseThrow(() -> new UploadedFileNotFoundException("No uploaded file at " + location));
        if (file.fileType() != fileType || !Objects.equals(file.customerId(), request.customerId())) {
            throw new IllegalArgumentException("File " + file.fileId() + " does not match the given fileType/customerId");
        }
        if (requestedJob != null && !Objects.equals(requestedJob.fileId(), file.fileId())) {
            throw new IllegalArgumentException("Job " + requestedJob.jobId() + " does not match the given s3Path");
        }
        if (file.approvalStatus() != ApprovalStatus.PENDING) {
            throw new ConflictException("File " + file.fileId() + " is " + file.approvalStatus()
                    + "; only files pending approval can be processed");
        }

        return requestedJob != null ? requestedJob
                : jobRepository.findByFileId(file.fileId()).orElseGet(() -> jobRepository.create(file));
    }

    @Async
    public void processAsync(UUID jobId) {
        process(jobId);
    }

    /**
     * Runs the job to completion; failures are recorded on the job rather than thrown.
     */
    public IngestionJob process(UUID jobId) {
        IngestionJob job = jobRepository.findById(jobId).orElseThrow(() -> new IngestionJobNotFoundException(jobId));
        if (!jobRepository.markProcessing(jobId)) {
            log.info("Job {} is already being processed or its file has been reviewed; skipping", jobId);
            return job;
        }

        log.info("Processing job {}: {} from {}", jobId, job.fileType(), job.s3Path());
        try {
            // Read fully first so S3 failures and bad file content are reported distinctly.
            byte[] content;
            try (InputStream inputStream = fileStorageService.download(S3Location.parse(job.s3Path()))) {
                content = inputStream.readAllBytes();
            }
            List<ParsedRow> rows = excelSheetReader.read(new ByteArrayInputStream(content), job.fileType());
            int ingested = rowWriter.replaceRows(job, rows);
            jobRepository.markCompleted(jobId, ingested);
            log.info("Job {} completed: {} rows ingested into {}", jobId, ingested, job.fileType().tableName());
        } catch (FileValidationException e) {
            log.warn("Job {} failed validation: {}", jobId, e.getMessage());
            jobRepository.markFailed(jobId, e.getMessage());
        } catch (Exception e) {
            log.error("Job {} failed", jobId, e);
            jobRepository.markFailed(jobId, "Processing error: " + e.getMessage());
        }
        return jobRepository.findById(jobId).orElseThrow(() -> new IngestionJobNotFoundException(jobId));
    }
}
