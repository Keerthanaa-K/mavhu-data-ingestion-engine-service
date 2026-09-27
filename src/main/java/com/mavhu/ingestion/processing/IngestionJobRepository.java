package com.mavhu.ingestion.processing;

import com.mavhu.ingestion.files.UploadedFile;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC access to mavhu.ingestion_jobs (DDL: db/ddl/esg_file_ingestion.sql).
 */
@Repository
@RequiredArgsConstructor
public class IngestionJobRepository {

    // Keeps error_message readable in API responses; full details are in the logs.
    private static final int MAX_ERROR_LENGTH = 4000;

    private static final RowMapper<IngestionJob> ROW_MAPPER = (rs, rowNum) -> new IngestionJob(
            rs.getObject("job_id", UUID.class),
            rs.getObject("file_id", UUID.class),
            rs.getInt("customer_id"),
            FileType.valueOf(rs.getString("file_type")),
            rs.getString("s3_path"),
            rs.getString("original_file_name"),
            IngestionJobStatus.valueOf(rs.getString("status")),
            rs.getObject("rows_ingested", Integer.class),
            rs.getString("error_message"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("started_at", OffsetDateTime.class),
            rs.getObject("completed_at", OffsetDateTime.class));

    private final JdbcTemplate jdbcTemplate;

    public IngestionJob create(UploadedFile file) {
        UUID jobId = UUID.randomUUID();
        jdbcTemplate.update("""
                        INSERT INTO mavhu.ingestion_jobs (job_id, file_id, customer_id, file_type, s3_path, original_file_name, status)
                        VALUES (?, ?, ?, ?, ?, ?, ?)""",
                jobId, file.fileId(), file.customerId(), file.fileType().name(), file.s3Path(), file.originalFileName(),
                IngestionJobStatus.PENDING.name());
        return findById(jobId).orElseThrow(() -> new IngestionJobNotFoundException(jobId));
    }

    public Optional<IngestionJob> findById(UUID jobId) {
        return jdbcTemplate.query("SELECT * FROM mavhu.ingestion_jobs WHERE job_id = ?", ROW_MAPPER, jobId)
                .stream().findFirst();
    }

    public Optional<IngestionJob> findByFileId(UUID fileId) {
        return jdbcTemplate.query("SELECT * FROM mavhu.ingestion_jobs WHERE file_id = ?", ROW_MAPPER, fileId)
                .stream().findFirst();
    }

    /**
     * Claims the job for processing. Returns false if another worker is already processing it, or if its
     * file has been approved/rejected since (reviewed data is never re-ingested).
     */
    public boolean markProcessing(UUID jobId) {
        return jdbcTemplate.update("""
                        UPDATE mavhu.ingestion_jobs j
                        SET status = ?, started_at = CURRENT_TIMESTAMP, completed_at = NULL,
                            rows_ingested = NULL, error_message = NULL
                        WHERE j.job_id = ? AND j.status <> ?
                          AND EXISTS (SELECT 1 FROM mavhu.uploaded_files f
                                      WHERE f.file_id = j.file_id AND f.approval_status = 'PENDING')""",
                IngestionJobStatus.PROCESSING.name(), jobId, IngestionJobStatus.PROCESSING.name()) == 1;
    }

    public void markCompleted(UUID jobId, int rowsIngested) {
        jdbcTemplate.update("""
                        UPDATE mavhu.ingestion_jobs
                        SET status = ?, rows_ingested = ?, completed_at = CURRENT_TIMESTAMP
                        WHERE job_id = ?""",
                IngestionJobStatus.COMPLETED.name(), rowsIngested, jobId);
    }

    public void markFailed(UUID jobId, String errorMessage) {
        String message = errorMessage == null ? "Unknown error"
                : errorMessage.length() > MAX_ERROR_LENGTH ? errorMessage.substring(0, MAX_ERROR_LENGTH) : errorMessage;
        jdbcTemplate.update("""
                        UPDATE mavhu.ingestion_jobs
                        SET status = ?, error_message = ?, completed_at = CURRENT_TIMESTAMP
                        WHERE job_id = ?""",
                IngestionJobStatus.FAILED.name(), message, jobId);
    }
}
